package com.google.android.diskusage;

import java.lang.reflect.Array;
import java.util.ArrayList;

import android.os.StatFs;

public class AppUsage extends DiskUsage {
  static FileSystemEntry root;

  @Override
  FileSystemEntry getRoot() {
    return root;
  }

  @Override
  void setRoot(FileSystemEntry root) {
    AppUsage.root = root;
  }
  
  FileSystemEntry wrapApps(FileSystemSpecial appsElement, AppFilter filter) {
    long freeSize = 0;
    long allocatedSpace = 0;
    long systemSize = 0;
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
      systemSize = allocatedSpace - appsElement.size;
    }
    
    if (filter.useSD) {
      FileSystemEntry newRoot = new FileSystemEntry(null,
          new FileSystemEntry[] { appsElement } );
      return newRoot;
    }
    
    ArrayList<FileSystemEntry> entries = new ArrayList<FileSystemEntry>();
    entries.add(appsElement);
    if (systemSize > 0) {
      entries.add(new FileSystemEmptySpace("System data", systemSize));
    }
    if (freeSize > 0) {
      entries.add(new FileSystemEmptySpace("Free space", freeSize));
    }

    FileSystemEntry[] internalArray = entries.toArray(new FileSystemEntry[] {});
    String name = "Data";
    if (filter.useCache) {
      name = "Cache";
      if (filter.useApk || filter.useData) {
        name = "Data and Cache";
      }
    }
    FileSystemEntry internalElement = new FileSystemEntry(name, internalArray);
    
    FileSystemEntry newRoot = new FileSystemEntry(null,
        new FileSystemEntry[] { internalElement } );
    return newRoot;
  }

  @Override
  FileSystemEntry scan() {
    AppFilter filter = AppFilter.loadSavedAppFilter(this);
    FileSystemEntry[] appsArray = loadApps2SD(false, filter);
    FileSystemSpecial appsElement = new FileSystemSpecial("Applications", appsArray);
    return wrapApps(appsElement, filter);
  }

  @Override
  protected FileSystemView makeView(DiskUsage diskUsage, FileSystemEntry root) {
    return new AppView(this, root);
  }
  
  @Override
  protected void onResume() {
    super.onResume();
    if (view == null) return;
    FileSystemEntry root = view.masterRoot;
    if (root == null) return;
    FileSystemEntry apps = root.children[0].children[0];
    if (apps instanceof FileSystemPackage) {
      apps = apps.parent;
    }
    FileSystemSpecial appsElement = (FileSystemSpecial) apps;
    AppFilter filter = AppFilter.loadSavedAppFilter(this);
    long size = 0;
    for (FileSystemEntry entry : appsElement.children) {
      FileSystemPackage pkg = (FileSystemPackage) entry;
      pkg.applyFilter(filter);
      size += pkg.size;
    }
    java.util.Arrays.sort(appsElement.children, FileSystemEntry.COMPARE);
    appsElement.size = size;
    appsElement.sizeString = null;
    FileSystemEntry newRoot = wrapApps(appsElement, filter);
    view.rescanFinished(newRoot);
  }
}
