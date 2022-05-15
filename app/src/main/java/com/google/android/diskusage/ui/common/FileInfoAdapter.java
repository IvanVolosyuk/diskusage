/*
 * DiskUsage - displays sdcard usage on android.
 * Copyright (C) 2008-2011 Ivan Volosyuk
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package com.google.android.diskusage.ui.common;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.google.android.diskusage.R;
import com.google.android.diskusage.databinding.ListDirItemBinding;
import com.google.android.diskusage.databinding.ListFileItemBinding;
import com.google.android.diskusage.filesystem.entity.FileSystemEntry;

// TODO: Use RecyclerView and RecyclerAdapter
public class FileInfoAdapter extends BaseAdapter {
  private int itemCount;
  private final String deletePath;
  private final TextView summaryView;
  private final Context context;

  public FileInfoAdapter(Context context, String deletePath,
      int count, TextView summary) {
    this.context = context;
    this.itemCount = count;
    this.deletePath = deletePath;
    this.summaryView = summary;
  }
  private boolean isFinished = false;

  private final ArrayList<Entry> entries = new ArrayList<>();
  private final LinkedList<String> workingSet = new LinkedList<>();
  private String parentBase;
  private String currentDir = "";
  private long totalSize;

  private LoaderTaskRunner taskRunner;
  private static final Executor THREAD_POOL_EXECUTOR =
          new ThreadPoolExecutor(5, 128, 1,
                  TimeUnit.SECONDS, new LinkedBlockingQueue<>());

  public static void setMessage(
          @NonNull Context context, @NonNull TextView textView, int numfiles, String sizeString) {
    String text = context.getString(
        R.string.delete_summary, numfiles, sizeString);
    textView.setText(text);
  }

  static class Entry {
    String size;
    String name;

    Entry(String size, String name) {
      this.size = size;
      this.name = name;
    }
  }

  private void prepareRoots() {
    File topEntity = new File(deletePath);
    parentBase = topEntity.getParent();
    workingSet.add(topEntity.getName());
  }

  private boolean loadOne(ArrayList<Entry> newEntries) {
    if (parentBase == null) prepareRoots();

    while (!workingSet.isEmpty()) {
      //* if (workingSet.isEmpty()) {
//        finished = true;
//        count = entries.size();
//        notifyDataSetChanged();
//        if (summary != null)
//          summary.setText(
//              String.format("%d files, total size: %s",
//                  count, FileSystemEntry.calcSizeString(totalSize)));
        //return false;
      //}

      String last = workingSet.removeLast();
      File currentEntity = new File(parentBase + File.separatorChar + last);
      if (currentEntity.isFile()) {
        String dirName = "";
        int sep = last.lastIndexOf(File.separatorChar);
        if (sep != -1) {
          dirName = last.substring(0, sep);
          last = last.substring(sep + 1);
        }
        if (!dirName.equals(currentDir)) {
          newEntries.add(new Entry(null, dirName));
          currentDir = dirName;
        }
        long size = currentEntity.length();
        totalSize += size;
        newEntries.add(new Entry(FileSystemEntry.calcSizeString(size), last));
//        notifyDataSetChanged();
        return true;
      }

      File[] entries = currentEntity.listFiles();
      Set<String> dirs = new TreeSet<>(String::compareToIgnoreCase);
      Set<String> files = new TreeSet<>(String::compareToIgnoreCase);

      if (entries == null) continue;

      for (File entity : entries) {
        String name = entity.getName();
        if (entity.isFile()) {
          files.add(last + File.separatorChar + name);
        } else {
          dirs.add(last + File.separatorChar + name);
        }
      }
      workingSet.addAll(dirs);
      workingSet.addAll(files);
    }
    return false;
  }

//*  public void load(int pos) {
//    while (!finished && entries.size() <= pos + 10) {
//      loadOne();
//    }
//  }

  @Override
  public int getCount() {
    return Math.max(itemCount, entries.size() + (isFinished ? 0 : 1));
  }

  @Override
  public int getViewTypeCount() {
    return 2;
  }

  @Override
  public Object getItem(int position) {
    if (position >= entries.size()) {
      return new Entry("", ".             .             .             .             .");
    }
    return entries.get(position);
  }

  @Override
  public int getItemViewType(int position) {
    Entry entry = (Entry) getItem(position);

    return (entry.size == null) ? 1 : 0;
  }

  @Override
  public long getItemId(int position) {
    return position;
  }

  public boolean isRunning = false;
  // TODO: Use Kotlin CoroutineScope
  static class LoaderTaskRunner {
    private final Executor executor = THREAD_POOL_EXECUTOR;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public interface Callback<R> {
      void onComplete(R result);
    }

    public <R> void executeAsync(Callable<R> callable, Callback<R> callback) {
      executor.execute(() -> {
        try {
          final R result = callable.call();
          handler.post(() -> {
            callback.onComplete(result);
          });
        } catch (Exception e) {
          e.printStackTrace();
        }
      });
    }
  }

  class LoaderTask implements Callable<ArrayList<Entry>> {
    // int currentPos;
    private final Integer input;
    public LoaderTask(/* int currentPos, */Integer input) {
      //this.currentPos = currentPos;
      this.input = input;
    }

    @Override
    public ArrayList<Entry> call() {
      int toLoad = input;
      ArrayList<Entry> newEntries = new ArrayList<>();
      for (int i = 0; i < toLoad; i++) {
        if (!loadOne(newEntries)) {
          return newEntries;
        }
      }
      return newEntries;
    }
  }

  private int maxPos;
  private void onPostExecute(@NonNull ArrayList<Entry> newEntries) {
    isRunning = false;
    if (newEntries.size() == 0) {
      isFinished = true;
      itemCount = entries.size();
      notifyDataSetChanged();
      summaryView.setText(context.getString(R.string.delete_summary,
              itemCount, FileSystemEntry.calcSizeString(totalSize)));
    } else {
      entries.addAll(newEntries);
      notifyDataSetChanged();

      int toLoad = maxPos + 200 - entries.size();
      if (toLoad > 0) {
        isRunning = true;
        taskRunner.executeAsync(new LoaderTask(20), this::onPostExecute); // request even more
      }
    }
  }

  @Override
  public View getView(int position, View view, ViewGroup parent) {
    if (!isFinished) {
      if (isRunning) {
        if (position > maxPos) maxPos = position;
      } else {
        taskRunner = new LoaderTaskRunner();
        int toLoad = position + 200 - entries.size();
        if (toLoad > 0) {
          isRunning = true;
          taskRunner.executeAsync(new LoaderTask(20), this::onPostExecute); // request even more
        }
      }
    }

    final Entry entry = (Entry) getItem(position);
    final LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());

    if (entry.size == null) {
      // directory
      final ListDirItemBinding binding;
      if (view != null) {
        binding = ListDirItemBinding.bind(view);
      } else {
        binding = ListDirItemBinding.inflate(layoutInflater);
      }
      binding.name.setText(entry.name);
      return binding.getRoot();
    } else {
      final ListFileItemBinding binding;
      if (view != null) {
        binding = ListFileItemBinding.bind(view);
      } else {
        binding = ListFileItemBinding.inflate(layoutInflater);
      }
      binding.name.setText(entry.name);
      binding.size.setText(entry.size);
      return binding.getRoot();
    }
  }

  @Override
  public boolean areAllItemsEnabled() {
    return false;
  }

  @Override
  public boolean isEnabled(int position) {
    return false;
  }

  public boolean hasStableIds() {
    return true;
  }
}
