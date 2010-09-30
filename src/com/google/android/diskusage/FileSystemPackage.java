package com.google.android.diskusage;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import android.content.pm.PackageStats;
import android.util.Log;

public class FileSystemPackage extends FileSystemEntry {
  final String pkg;
  final int codeSize;
  final int dataSize;
  final int cacheSize;
  final int dalvikCacheSize;
  final int flags;
  
  // ApplicationInfo.FLAG_EXTERNAL_STORAGE
  private static final int SDCARD_FLAG = 0x40000;
  
  
  private File cacheFile(String pattern, Object... args) {
    return new File("/data/dalvik-cache/" + String.format(pattern, args));
  }
  
  
  private File getDalvikCacheForSystemApp() {
    File cache = cacheFile("system@app@%s.apk@classes.dex", name);
    if (cache.exists()) return cache;
    return null;
  }
  
  private File getDalvikCacheForNumberedPackage(String pattern) {
    for (int i = 0; i < 10; i++) {
      File cache = cacheFile(pattern, pkg, i);
      if (cache.exists()) return cache;
    }
    return null;
  }
  
  private File getDalvikCacheForPkg(String pattern) {
    File cache = cacheFile(pattern, pkg);
    if (cache.exists()) return cache;
    return null;
  }
  
  private long guessDalvikCacheSize() {
    File cache = null;
    if (onSD()) {
      cache = getDalvikCacheForNumberedPackage(
          "mnt@asec@%s-%d@pkg.apk@classes.dex");
    } else {
//      cache = getDalvikCacheForPkg("data@app@%s.apk@classes.dex");
//      if (cache == null)
//        cache = getDalvikCacheForPkg("data@app-private@%s.apk@classes.dex");
//      if (cache == null)
//        cache = getDalvikCacheForSystemApp();
//      if (cache == null)
//        cache = getDalvikCacheForNumberedPackage(
//            "data@app@%s-%d.apk@classes.dex");
//      if (cache == null)
//        cache = getDalvikCacheForNumberedPackage(
//        "data@app-private@%s-%d.apk@classes.dex");
    }
    
    if (cache != null) {
      Log.d("diskusage", cache.getAbsolutePath() + ": " + cache.length());
      return cache.length();
    } else {
//      Log.d("diskusage", "can't guess dalvikCache for " + pkg);
      return 0;
    }
  }
  
  public FileSystemPackage(
      String name, String pkg, PackageStats stats,
      int flags, Long hackApkSize, int blockSize) {
    super(name, 0, blockSize);
    this.pkg = pkg;
    this.cacheSize = (int) stats.cacheSize;
    this.dataSize = (int) stats.dataSize;
    this.flags = flags | (hackApkSize != null ? SDCARD_FLAG : 0);
    this.dalvikCacheSize = (int) guessDalvikCacheSize();
    if (onSD()) {
//      if (hackApkSize != null) {
//        this.codeSize = hackApkSize.intValue();
//      } else {
        this.codeSize = (int) stats.codeSize;
//      }
    } else {
      this.codeSize = (int) stats.codeSize - this.dalvikCacheSize;
    }
  }
  
  public boolean onSD() {
    return (flags & SDCARD_FLAG) != 0;
  }
  
  public void applyFilter(AppFilter filter, int blockSize) {
    sizeString = null;
    long blocks = 0;
    ArrayList<FileSystemEntry> entries = new ArrayList<FileSystemEntry>();
    
    if (onSD() && !filter.useSD) {
      
    } else {
      if (filter.useApk) {
        entries.add(new FileSystemEntry("apk", codeSize, blockSize));
      }
    }
    if (filter.useData) {
      entries.add(new FileSystemEntry("data", dataSize, blockSize));
    }
    if (filter.useDalvikCache) {
      entries.add(new FileSystemEntry("dalvikCache", dalvikCacheSize, blockSize));
    }
    
    if (filter.useCache) {
      entries.add(new FileSystemEntry("cache", cacheSize, blockSize));
    }
    for (FileSystemEntry e : entries) {
      blocks += e.getSizeInBlocks();
    }
    setSizeInBlocks(blocks);
    
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
