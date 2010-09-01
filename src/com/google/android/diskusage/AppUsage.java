package com.google.android.diskusage;

import java.io.File;
import java.lang.reflect.Array;
import java.util.ArrayList;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;

public class AppUsage extends DiskUsage {
  static FileSystemEntry root;
  private AppFilter pendingFilter;
  private static int blockSizeCache;

  @Override
  FileSystemEntry getRoot() {
    return root;
  }

  @Override
  void setRoot(FileSystemEntry root) {
    AppUsage.root = root;
  }
  
  public static int getDataBlockSize() {
    if (blockSizeCache != 0) return blockSizeCache;
    final File dataDir = Environment.getDataDirectory();
    StatFs data = new StatFs(dataDir.getAbsolutePath());
    int blockSize = data.getBlockSize();
    return blockSize;
  }
  
  @Override
  public int getBlockSize() {
    return getDataBlockSize();
  }
  
  FileSystemEntry wrapApps(FileSystemSpecial appsElement, AppFilter filter) {
    long freeSize = 0;
    long allocatedSpace = 0;
    long systemSize = 0;
    long entryBlockSize = getBlockSize();
    if ((filter.useApk || filter.useData) && !filter.useSD) {
      StatFs data = new StatFs("/data");
      long blockSize = data.getBlockSize();
      freeSize = data.getAvailableBlocks() * blockSize;
      allocatedSpace = data.getBlockCount() * blockSize - freeSize;
    }
    if (filter.useCache && ! filter.useSD) {
      StatFs cache = new StatFs("/cache");
      long blockSize = cache.getBlockSize();
      long cacheFreeSpace = cache.getAvailableBlocks() * blockSize; 
      freeSize += cacheFreeSpace;
      allocatedSpace += cache.getBlockCount() * blockSize - cacheFreeSpace;
    }
    
    if (allocatedSpace > 0) {
      systemSize = allocatedSpace - appsElement.getSizeInBlocks() * FileSystemEntry.blockSize;
    }
    
    if (filter.useSD) {
      FileSystemEntry newRoot = new FileSystemEntry(null,
          new FileSystemEntry[] { appsElement }, entryBlockSize);
      return newRoot;
    }
    
    ArrayList<FileSystemEntry> entries = new ArrayList<FileSystemEntry>();
    entries.add(appsElement);
    if (systemSize > 0) {
      entries.add(new FileSystemSystemSpace("System data", systemSize, entryBlockSize));
    }
    if (freeSize > 0) {
      entries.add(new FileSystemFreeSpace("Free space", freeSize, entryBlockSize));
    }

    FileSystemEntry[] internalArray = entries.toArray(new FileSystemEntry[] {});
    String name = "Data";
    if (filter.useCache) {
      name = "Cache";
      if (filter.useApk || filter.useData) {
        name = "Data and Cache";
      }
    }
    FileSystemEntry internalElement = new FileSystemEntry(name, internalArray, entryBlockSize);
    
    FileSystemEntry newRoot = new FileSystemEntry(null,
        new FileSystemEntry[] { internalElement }, entryBlockSize);
    return newRoot;
  }

  @Override
  FileSystemEntry scan() {
    AppFilter filter  = pendingFilter;
    int blockSize = AppUsage.getDataBlockSize();
    FileSystemEntry[] appsArray = loadApps2SD(false, filter, blockSize);
    FileSystemSpecial appsElement = new FileSystemSpecial("Applications", appsArray, blockSize);
    appsElement.filter = filter;
    return wrapApps(appsElement, filter);
  }

  @Override
  protected FileSystemView makeView(DiskUsage diskUsage, FileSystemEntry root) {
    return new AppView(this, root);
  }
  
  @Override
  protected void onCreate(Bundle icicle) {
    pendingFilter = AppFilter.loadSavedAppFilter(this);
    super.onCreate(icicle);
  }
  
  private FileSystemSpecial getAppsElement(FileSystemView view) {
    FileSystemEntry root = view.masterRoot;
    FileSystemEntry apps = root.children[0].children[0];
    if (apps instanceof FileSystemPackage) {
      apps = apps.parent;
    }
    return (FileSystemSpecial) apps;
  }
  
  private void updateFilter(AppFilter newFilter) {
    if (view == null) {
      pendingFilter = newFilter;
      return;
    }

    FileSystemSpecial appsElement = getAppsElement(view);
    if (newFilter.equals(appsElement.filter)) {
      return;
    }
    for (FileSystemEntry entry : appsElement.children) {
      FileSystemPackage pkg = (FileSystemPackage) entry;
      pkg.applyFilter(newFilter);
    }
    java.util.Arrays.sort(appsElement.children, FileSystemEntry.COMPARE);
    
    appsElement = new FileSystemSpecial(appsElement.name, appsElement.children, getDataBlockSize());
    appsElement.filter = newFilter;
    
    FileSystemEntry newRoot = wrapApps(appsElement, newFilter);
    setRoot(newRoot);
    view.rescanFinished(newRoot);
    view.startZoomAnimation();
  }
  
  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    if (view == null) return;
    FileSystemSpecial appsElement = getAppsElement(view);
    outState.putParcelable("filter", appsElement.filter);
  }
  
  @Override
  protected void onRestoreInstanceState(Bundle inState) {
    super.onRestoreInstanceState(inState);
    AppFilter newFilter = (AppFilter) inState.getParcelable("filter");
    if (newFilter != null) updateFilter(newFilter);
  }
  
  @Override
  public void onActivityResult(int a, int result, Intent i) {
    super.onActivityResult(a, result, i);
    AppFilter newFilter = AppFilter.loadSavedAppFilter(this);
    updateFilter(newFilter);
  }
}
