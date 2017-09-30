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

package com.google.android.diskusage;

import android.os.StatFs;
import android.system.ErrnoException;
import android.system.Os;
import android.system.StructStat;
import android.util.Log;

import com.google.android.diskusage.datasource.LegacyFile;
import com.google.android.diskusage.entity.FileSystemEntry;
import com.google.android.diskusage.entity.FileSystemEntry.ExcludeFilter;
import com.google.android.diskusage.entity.FileSystemEntrySmall;
import com.google.android.diskusage.entity.FileSystemFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.PriorityQueue;

public class Scanner implements DiskUsage.ProgressGenerator {
  private final int maxdepth;
  private final int blockSize;
  private final int blockSizeIn512Bytes;

  private FileSystemEntry createdNode;
  private int createdNodeNumFiles;
  private int createdNodeNumDirs;

  long pos;
  FileSystemEntry lastCreatedFile;
  private long dev;

  public FileSystemEntry lastCreatedFile() {
    return lastCreatedFile;
  }
  public long pos() {
    return pos;
  }

  public Scanner(int maxdepth, int blockSize) {
    this.maxdepth = maxdepth;
    this.blockSize = blockSize;
    this.blockSizeIn512Bytes = blockSize / 512;
  }

  public FileSystemEntry scan(LegacyFile file) throws IOException {
    long st_blocks;
    try {
      StructStat stat = Os.stat(file.getCannonicalPath());
      dev = stat.st_dev;
      st_blocks = stat.st_blocks;
    } catch (ErrnoException e) {
      throw new IOException("Failed to find root folder", e);
    }
    scanDirectory(null, file, 0, st_blocks / blockSizeIn512Bytes);
    return createdNode;
  }

  /**
   * Scan directory object.
   * This constructor starts recursive scan to find all descendent files and directories.
   * Stores parent into field, name obtained from file, size of this directory
   * is calculated as a sum of all children.
   * @param parent parent directory object.
   * @param file corresponding File object
   * @param depth current directory tree depth
   */
  private void scanDirectory(FileSystemEntry parent, LegacyFile file, int depth, long self_blocks) {
    String name = file.getName();
    makeNode(parent, name);
    createdNodeNumDirs = 1;
    createdNodeNumFiles = 0;

    if (depth == maxdepth) {
      createdNode.setSizeInBlocks(0, blockSize);
      return;
    }

    String[] listNames = null;

    try {
      listNames = file.list();
    } catch (SecurityException io) {
      Log.d("diskusage", "list files", io);
    }

    if (listNames == null) return;
    FileSystemEntry thisNode = createdNode;
    int  thisNodeNumDirs = 1;
    int  thisNodeNumFiles = 0;

    ArrayList<FileSystemEntry> children = new ArrayList<FileSystemEntry>();


    long blocks = self_blocks;

    for (int i = 0; i < listNames.length; i++) {
      LegacyFile childFile = file.getChild(listNames[i]);

      long st_blocks;
      long st_size;
      try {
        StructStat res = Os.stat(childFile.getCannonicalPath());
        // Not regular file and not folder
//        if ((res.st_mode & 0x0100000) == 0 && (res.st_mode & 0x0040000) == 0) continue;
        st_blocks = res.st_blocks;
        st_size = res.st_size;
      } catch (ErrnoException|IOException e) {
        continue;
      }

      int dirs = 0, files = 1;
      if (childFile.isFile()) {
        makeNode(thisNode, childFile.getName());
        createdNode.initSizeInBytesAndBlocks(st_size, st_blocks / blockSizeIn512Bytes, blockSize);
        pos += createdNode.getSizeInBlocks();
        lastCreatedFile = createdNode;
      } else {
        // directory
        scanDirectory(thisNode, childFile, depth + 1, st_blocks / blockSizeIn512Bytes);
        dirs = createdNodeNumDirs;
        files = createdNodeNumFiles;
      }

      long createdNodeBlocks = createdNode.getSizeInBlocks();
      blocks += createdNodeBlocks;

      children.add(createdNode);
      thisNodeNumFiles += files;
      thisNodeNumDirs += dirs;
    }
    thisNode.setSizeInBlocks(blocks, blockSize);

    thisNode.children = children.toArray(new FileSystemEntry[children.size()]);
    java.util.Arrays.sort(thisNode.children, FileSystemEntry.COMPARE);

    createdNode = thisNode;
    createdNodeNumDirs = thisNodeNumDirs;
    createdNodeNumFiles = thisNodeNumFiles;
  }

  private void makeNode(FileSystemEntry parent, String name) {
    createdNode = FileSystemFile.makeNode(parent, name);
  }
}
