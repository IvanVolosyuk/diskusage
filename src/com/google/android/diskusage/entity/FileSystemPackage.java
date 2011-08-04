/**
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

package com.google.android.diskusage.entity;

import android.content.pm.PackageStats;
import android.util.Log;
import com.google.android.diskusage.AppFilter;
import com.google.android.diskusage.AppFilter.App2SD;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

public class FileSystemPackage extends FileSystemEntry {
  public final String pkg;
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
    super(null, name);
    this.pkg = pkg;
    this.cacheSize = (int) stats.cacheSize;
    this.dataSize = (int) stats.dataSize;
    this.flags = flags | (hackApkSize != null ? SDCARD_FLAG : 0);
    this.dalvikCacheSize = (int) guessDalvikCacheSize();
    if (onSD()) {
      if (hackApkSize != null) {
        this.codeSize = hackApkSize.intValue();
      } else {
        this.codeSize = (int) stats.codeSize;
      }
    } else {
      this.codeSize = (int) stats.codeSize - this.dalvikCacheSize;
    }
  }
  
  public boolean onSD() {
    return (flags & SDCARD_FLAG) != 0;
  }
  
  private boolean select(App2SD selector, boolean isOnSD) {
    return selector == App2SD.BOTH
    || (isOnSD == (selector == App2SD.APPS2SD));
  }
  
  public void applyFilter(AppFilter filter, int blockSize) {
    clearDrawingCache();
    long blocks = 0;
    ArrayList<FileSystemEntry> entries = new ArrayList<FileSystemEntry>();
    
    if (select(filter.apps, onSD())) {
      if (filter.showApk && select(filter.memory, onSD())) {
        entries.add(FileSystemEntry.makeNode(null, "apk")
            .initSizeInBytes(codeSize, blockSize));
      }
      if (filter.showData && select(filter.memory, false)) {
        entries.add(FileSystemEntry.makeNode(null, "data")
            .initSizeInBytes(dataSize, blockSize));
      }
      if (filter.showDalvikCache && select(filter.memory, false)) {
        entries.add(FileSystemEntry.makeNode(null, "dalvikCache")
            .initSizeInBytes(dalvikCacheSize, blockSize));
      }
    }

    for (FileSystemEntry e : entries) {
      blocks += e.getSizeInBlocks();
    }
    setSizeInBlocks(blocks, blockSize);

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

  private FileSystemPackage(String name, String pkg, int codeSize, int dataSize, int cacheSize,
                            int dalvikCacheSize, int flags) {
    super(null, name);
    this.pkg = pkg;
    this.codeSize = codeSize;
    this.dataSize = dataSize;
    this.cacheSize = cacheSize;
    this.dalvikCacheSize = dalvikCacheSize;
    this.flags = flags;
  }

  @Override
  public FileSystemEntry create() {
    return new FileSystemPackage(this.name, this.pkg, this.codeSize, this.dataSize, this.cacheSize,
                                 this.dalvikCacheSize, this.flags);
  }
}
