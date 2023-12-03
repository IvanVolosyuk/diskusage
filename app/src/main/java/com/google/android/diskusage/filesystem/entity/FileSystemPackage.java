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

package com.google.android.diskusage.filesystem.entity;

import android.content.pm.ApplicationInfo;
import androidx.annotation.NonNull;
import timber.log.Timber;
import java.util.ArrayList;
import java.util.Arrays;

public class FileSystemPackage extends FileSystemEntry {
  public final String pkg;
  long codeSize;
  long dataSize;
  long cacheSize;
  final int flags;
  ArrayList<FileSystemRoot> publicChildren = new ArrayList<>();

  public enum ChildType {
    CODE,
    DATA,
    CACHE
  }

  public FileSystemPackage(
      String name, String pkg, long codeSize, long dataSize, long cacheSize, int flags) {
    super(null, name);
    this.pkg = pkg;
    this.cacheSize = cacheSize;
    this.dataSize = dataSize - cacheSize;
    this.flags = flags;
    if ((flags & ApplicationInfo.FLAG_SYSTEM) != 0
        && (flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0) {
      codeSize = 0;
    }

    // TODO: not sure what happens here
    if ((flags & ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0) {
      codeSize = 0;
    }
    this.codeSize = codeSize;
  }

  public void applyFilter(long blockSize) {
    clearDrawingCache();
    long blocks = 0;
    ArrayList<FileSystemEntry> entries = new ArrayList<>(publicChildren);
    entries.add(FileSystemEntry.makeNode(null, "apk")
            .initSizeInBytes(codeSize, blockSize));
    entries.add(FileSystemEntry.makeNode(null, "data")
            .initSizeInBytes(dataSize, blockSize));
    entries.add(FileSystemEntry.makeNode(null, "cache")
            .initSizeInBytes(cacheSize, blockSize));

    for (FileSystemEntry e : entries) {
      blocks += e.getSizeInBlocks();
    }
    setSizeInBlocks(blocks, blockSize);

    for (FileSystemEntry e : entries) {
      e.parent = this;
    }
    children = entries.toArray(new FileSystemEntry[] {});
    Arrays.sort(children, FileSystemEntry.COMPARE);
  }

  @Override
  public FileSystemEntry create() {
    return new FileSystemPackage(this.name, this.pkg, this.codeSize, this.dataSize, this.cacheSize,
                                 this.flags);
  }

  public void addPublicChild(FileSystemRoot child, @NonNull ChildType type, long blockSize) {
    publicChildren.add(child);
    switch (type) {
      case CODE: codeSize -= child.getSizeInBlocks() * blockSize;
        if (codeSize < 0) {
          Timber.d("FileSystemPackage.addPublicChild(): Code size negative %s for %s", codeSize, pkg);
          codeSize = 0;
        }
        break;
      case DATA: dataSize -= child.getSizeInBlocks() * blockSize;
        if (dataSize < 0) {
          Timber.d("FileSystemPackage.addPublicChild(): Data size negative %s for %s", dataSize, pkg);
          dataSize = 0;
        }
        break;
      case CACHE: cacheSize -= child.getSizeInBlocks() * blockSize;
        if (cacheSize < 0) {
          Timber.d("FileSystemPackage.addPublicChild(): Cache size negative %s for %s", cacheSize, pkg);
          cacheSize = 0;
        }
        break;
    }
  }
}
