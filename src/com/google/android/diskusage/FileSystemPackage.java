package com.google.android.diskusage;

import java.util.ArrayList;
import java.util.Arrays;

import android.content.pm.PackageStats;

public class FileSystemPackage extends FileSystemEntry {
  final String pkg;
  final int codeSize;
  final int dataSize;
  final int cacheSize;
  final int flags;
  
  // ApplicationInfo.FLAG_EXTERNAL_STORAGE
  private static final int SDCARD_FLAG = 0x40000;
  
  public FileSystemPackage(
      String name, String pkg, PackageStats stats,
      int flags, Long hackApkSize) {
    super(name, 0, null);
    this.pkg = pkg;
    this.cacheSize = (int) stats.cacheSize;
    this.dataSize = (int) stats.dataSize;
    this.flags = flags | (hackApkSize != null ? SDCARD_FLAG : 0);
    if (hackApkSize != null) {
      this.codeSize = hackApkSize.intValue();
    } else {
      this.codeSize = (int) stats.codeSize;
    }
  }
  
  public boolean onSD() {
    return (flags & SDCARD_FLAG) != 0;
  }
  
  public void applyFilter(AppFilter filter) {
    sizeString = null;
    size = 0;
    ArrayList<FileSystemEntry> entries = new ArrayList<FileSystemEntry>();
    
    if (onSD() && !filter.useSD) {
      
    } else {
      if (filter.useApk) {
        entries.add(new FileSystemEntry("apk", codeSize, null));
        size += codeSize;
      }
    }
    if (filter.useData) {
      entries.add(new FileSystemEntry("data", dataSize, null));
      size += dataSize;
    }
    if (filter.useCache) {
      entries.add(new FileSystemEntry("cache", cacheSize, null));
      size += cacheSize;
    }
    if (filter.enableChildren) {
      for (FileSystemEntry e : entries) {
        e.parent = this;
      }
      children = entries.toArray(new FileSystemEntry[] {});
      Arrays.sort(children, FileSystemEntry.COMPARE);
    } else {
      children = null;
    }
  }
}
