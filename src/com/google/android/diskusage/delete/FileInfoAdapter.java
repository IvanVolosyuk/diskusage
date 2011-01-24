package com.google.android.diskusage.delete;

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

import com.google.android.diskusage.FileSystemEntry;
import com.google.android.diskusage.R;

public class FileInfoAdapter extends BaseAdapter {
  private int count;
  private ArrayList<Entry> entries = new ArrayList<Entry>();
  private boolean finished = false;
  LayoutInflater inflater;

  private String rootBase;
  private String[] roots;
  private String base;
  
  private ArrayList<String> workingSet = new ArrayList<String>();
  private String currentDir = "";
  TextView summary;
  long totalSize;
  Context context;
  
  public FileInfoAdapter(Context context, String rootBase,
      String[] roots, int count, TextView summary) {
    this.context = context;
    this.count = count;
    this.rootBase = rootBase;
    this.roots = roots;
    this.summary = summary;
  }
  
  public static String formatMessage(Context context, int numfiles, String sizeString) {
    return context.getString(R.string.delete_summary, numfiles, sizeString);
  }
  
  class Entry {
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
  
  Comparator<String> reverseCaseInsensitiveOrder = new Comparator<String>() {
    @Override
    public int compare(String object1, String object2) {
      return object2.compareToIgnoreCase(object1);
    }
  };
  
  private void prepareRoots() {
    base = new File(rootBase + "/" + roots[0]).getParent();
    Set<String> dirs = new TreeSet<String>(reverseCaseInsensitiveOrder);
    Set<String> files = new TreeSet<String>(reverseCaseInsensitiveOrder);

    for (String root : roots) {
      File entity = new File(rootBase + "/" + root);
      if (!entity.exists()) continue;
      String name = entity.getName();
      if (entity.isFile()) {
        files.add(name);
      } else {
        dirs.add(name);
      }
    }
    for (String file : files) {
      workingSet.add(file);
    }
    
    for (String dir : dirs) {
      workingSet.add(dir);
    }
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
      Set<String> dirs = new TreeSet<String>(reverseCaseInsensitiveOrder);
      Set<String> files = new TreeSet<String>(reverseCaseInsensitiveOrder);

      if (entries == null) continue;
      
      for (File entity : entries) {
        String name = entity.getName();
        if (entity.isFile()) {
          files.add(last + "/" + name);
        } else {
          dirs.add(last + "/" + name);
        }
      }
      for (String dir : dirs) {
        workingSet.add(dir);
      }

      for (String file : files) {
        workingSet.add(file);
      }
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
    protected ArrayList<Entry> doInBackground(Integer... params) {
      int toLoad = params[0];
      ArrayList<Entry> newEntries = new ArrayList<Entry>();
      for (int i = 0; i < toLoad; i++) {
        if (!loadOne(newEntries)) {
          return newEntries;
        }
      }
      return newEntries;
    }
    
    @Override
    protected void onPostExecute(ArrayList<Entry> newEntries) {
      running = false;
      if (newEntries.size() == 0) {
        finished = true;
        count = entries.size();
        notifyDataSetChanged();
        summary.setText(formatMessage(context, count, FileSystemEntry.calcSizeString(totalSize)));
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
  };
  
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
