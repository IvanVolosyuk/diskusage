package com.google.android.diskusage.delete;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

import android.content.Context;
import android.util.Log;
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
  
  public FileInfoAdapter(String rootBase, String[] roots, int count, TextView summary) {
    this.count = count;
    this.rootBase = rootBase;
    this.roots = roots;
    this.summary = summary;
  }
  
  class Entry {
    long size;
    String name;
    
    Entry(long size, String name) {
      this.size = size;
      this.name = name;
    }
  }
  
  Entry overflow = new Entry(-1, "");
  
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
  
  private void loadOne() {
    if (base == null) prepareRoots();
    
    while (true) {
      if (workingSet.isEmpty()) {
        finished = true;
        count = entries.size();
        notifyDataSetChanged();
        if (summary != null)
          summary.setText(
              String.format("%d files, total size: %s",
                  count, FileSystemEntry.calcSizeString(totalSize)));
        return;
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
          entries.add(new Entry(-1, dirName));
          currentDir = dirName;
        }
        long size = currentEntity.length();
        totalSize += size;
        entries.add(new Entry(size, last));
//        notifyDataSetChanged();
        return;
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
      for (String file : files) {
        workingSet.add(file);
      }

      for (String dir : dirs) {
        workingSet.add(dir);
      }
    }
  }

  public void load(int pos) {
    while (!finished && entries.size() <= pos + 5) {
      loadOne();
    }
  }

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
    load(position);
    return getEntry(position);
  }
  
  @Override
  public int getItemViewType(int position) {
    load(position);
    Entry entry = getEntry(position);
    
    return (entry.size == -1) ? 1 : 0;
  }

  @Override
  public long getItemId(int position) {
    return position;
  }
  
  @Override
  public View getView(int position, View view, ViewGroup parent) {
    load(position);
    Entry entry = getEntry(position);
    
    LayoutInflater inflater = this.inflater;
    if (inflater == null) {
      inflater = this.inflater = (LayoutInflater) parent.getContext().getSystemService(
          Context.LAYOUT_INFLATER_SERVICE);
    }

    if (entry.size == -1) {
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
      sizeView.setText(FileSystemEntry.calcSizeString(entry.size));
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
