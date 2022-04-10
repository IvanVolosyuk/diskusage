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

package com.google.android.diskusage.ui.delete;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

import android.content.Context;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.support.annotation.NonNull;
import com.google.android.diskusage.R;
import com.google.android.diskusage.filesystem.entity.FileSystemEntry;

public class FileInfoAdapter extends BaseAdapter {
  private int count;
  private final ArrayList<Entry> entries = new ArrayList<>();
  private boolean finished = false;
  LayoutInflater inflater;

  private final String deletePath;
  private String base;
  
  private final ArrayList<String> workingSet = new ArrayList<>();
  private String currentDir = "";
  TextView summary;
  long totalSize;
  Context context;
  
  public FileInfoAdapter(Context context, String deletePath,
      int count, TextView summary) {
    this.context = context;
    this.count = count;
    this.deletePath = deletePath;
    this.summary = summary;
  }
  
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
  
  Entry overflow = new Entry("", ".             .             .             .             .");
  
  Entry getEntry(int pos) {
    if (pos >= entries.size()) {
      return overflow;
    }
    return entries.get(pos);
  }
  
  Comparator<String> reverseCaseInsensitiveOrder = (object1, object2) -> object2.compareToIgnoreCase(object1);
  
  private void prepareRoots() {
    base = new File(deletePath).getParent();
    File entity = new File(deletePath);
    String name = entity.getName();
    workingSet.add(name);
  }
  
  private boolean loadOne(ArrayList<Entry> newEntries) {
    if (base == null) prepareRoots();
    
    while (true) {
      if (workingSet.isEmpty()) {
//        finished = true;
//        count = entries.size();
//        notifyDataSetChanged();
//        if (summary != null)
//          summary.setText(
//              String.format("%d files, total size: %s",
//                  count, FileSystemEntry.calcSizeString(totalSize)));
        return false;
      }

      String last = workingSet.remove(workingSet.size() - 1);
      File currentEntity = new File(base + "/" + last);
      if (currentEntity.isFile()) {
        String dirName = "";
        int sep = last.lastIndexOf("/");
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
      Set<String> dirs = new TreeSet<>(reverseCaseInsensitiveOrder);
      Set<String> files = new TreeSet<>(reverseCaseInsensitiveOrder);

      if (entries == null) continue;
      
      for (File entity : entries) {
        String name = entity.getName();
        if (entity.isFile()) {
          files.add(last + "/" + name);
        } else {
          dirs.add(last + "/" + name);
        }
      }
      workingSet.addAll(dirs);
      workingSet.addAll(files);
    }
  }

//  public void load(int pos) {
//    while (!finished && entries.size() <= pos + 10) {
//      loadOne();
//    }
//  }

  @Override
  public int getCount() {
    return Math.max(count, entries.size() + (finished ? 0 : 1));
  }
  
  @Override
  public int getViewTypeCount() {
    return 2;
  }

  @Override
  public Object getItem(int position) {
    return getEntry(position);
  }
  
  @Override
  public int getItemViewType(int position) {
    Entry entry = getEntry(position);
    
    return (entry.size == null) ? 1 : 0;
  }

  @Override
  public long getItemId(int position) {
    return position;
  }
  
  public boolean running = false;
  
  class LoaderTask extends AsyncTask<Integer, Void, ArrayList<Entry>> {
    int currentPos;
    public LoaderTask(int currentPos) {
      this.currentPos = currentPos;
    }
    @Override
    protected ArrayList<Entry> doInBackground(@NonNull Integer... params) {
      int toLoad = params[0];
      ArrayList<Entry> newEntries = new ArrayList<>();
      for (int i = 0; i < toLoad; i++) {
        if (!loadOne(newEntries)) {
          return newEntries;
        }
      }
      return newEntries;
    }
    
    @Override
    protected void onPostExecute(@NonNull ArrayList<Entry> newEntries) {
      running = false;
      if (newEntries.size() == 0) {
        finished = true;
        count = entries.size();
        notifyDataSetChanged();
        setMessage(context, summary, count, FileSystemEntry.calcSizeString(totalSize));
      } else {
        entries.addAll(newEntries);
        notifyDataSetChanged();
        
        int toLoad = maxPos + 200 - entries.size();
        if (toLoad > 0) {
          running = true;
          new LoaderTask(maxPos).execute(20); // request even more
        }
      }
    }
  }
  
  private int maxPos;
  
  @Override
  public View getView(int position, View view, ViewGroup parent) {
    
    if (finished) {
    } else if (running) {
      if (position > maxPos) maxPos = position;
    } else {
      int toLoad = position + 200 - entries.size();
      if (toLoad > 0) {
        running = true;
        new LoaderTask(position).execute(20); // request even more
      }
    } 
    Entry entry = getEntry(position);
    
    LayoutInflater inflater = this.inflater;
    if (inflater == null) {
      inflater = this.inflater = (LayoutInflater) parent.getContext().getSystemService(
          Context.LAYOUT_INFLATER_SERVICE);
    }

    if (entry.size == null) {
      // directory
      if (view == null) {
        view = inflater.inflate(R.layout.list_dir_item, null);
      }
      TextView nameView = (TextView) view.findViewById(R.id.name);
      nameView.setText(entry.name);
    } else {
      if (view == null) {
        view = inflater.inflate(R.layout.list_file_item, null);
      }
      TextView nameView = (TextView) view.findViewById(R.id.name);
      TextView sizeView = (TextView) view.findViewById(R.id.size);
      nameView.setText(entry.name);
      sizeView.setText(entry.size);
    }
    
    return view;
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
