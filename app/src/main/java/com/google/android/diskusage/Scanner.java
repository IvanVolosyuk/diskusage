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

package com.google.android.diskusage;

import java.util.ArrayList;
import java.util.PriorityQueue;

import android.annotation.SuppressLint;
import android.system.ErrnoException;
import android.system.Os;
import android.system.StructStat;
import android.util.Log;
import android.support.annotation.NonNull;

import com.google.android.diskusage.datasource.LegacyFile;
import com.google.android.diskusage.entity.FileSystemEntry;
import com.google.android.diskusage.entity.FileSystemEntrySmall;
import com.google.android.diskusage.entity.FileSystemFile;

import java.io.IOException;

public class Scanner implements DiskUsage.ProgressGenerator {
  private final int maxDepth;
  private final long blockSize;
  private final long blockSizeIn512Bytes;
  private final long sizeThreshold;

  private FileSystemEntry createdNode;
  private int createdNodeSize;
  private int createdNodeNumFiles;
  private int createdNodeNumDirs;

  private int heapSize;
  private final int maxHeapSize;
  private final PriorityQueue<SmallList> smallLists = new PriorityQueue<>();
  long pos;
  FileSystemEntry lastCreatedFile;
  private long dev;

  public FileSystemEntry lastCreatedFile() {
    return lastCreatedFile;
  }
  public long pos() {
    return pos;
  }

  private static class SmallList implements Comparable<SmallList> {
    FileSystemEntry parent;
    FileSystemEntry[] children;
    int heapSize;
    float spaceEfficiency;

    SmallList(FileSystemEntry parent, FileSystemEntry[] children, int heapSize, long blocks) {
      this.parent = parent;
      this.children = children;
      this.heapSize = heapSize;
      this.spaceEfficiency = blocks / (float) heapSize;
    }

    @Override
    public int compareTo(@NonNull SmallList that) {
      return Float.compare(spaceEfficiency, that.spaceEfficiency);
    }
  };

  Scanner(int maxDepth, long blockSize, long allocatedBlocks, int maxHeap) {
    this.maxDepth = maxDepth;
    this.blockSize = blockSize;
    this.blockSizeIn512Bytes = blockSize / 512;
    this.sizeThreshold = (allocatedBlocks << FileSystemEntry.blockOffset) / (maxHeap / 2);
    this.maxHeapSize = maxHeap;
//    this.blockAllowance = (allocatedBlocks << FileSystemEntry.blockOffset) / 2;
//    this.blockAllowance = (maxHeap / 2) * sizeThreshold;
    Log.d("diskusage", "allocatedBlocks " + allocatedBlocks);
    Log.d("diskusage", "maxHeap " + maxHeap);
    Log.d("diskusage", "sizeThreshold = " + sizeThreshold / (float) (1 << FileSystemEntry.blockOffset));
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
    int extraHeap = 0;

    // Restoring blocks
    for (SmallList list : smallLists) {
//      print("restored", list);

      FileSystemEntry[] oldChildren = list.parent.children;
      FileSystemEntry[] addChildren = list.children;
      FileSystemEntry[] newChildren =
        new FileSystemEntry[oldChildren.length - 1 + addChildren.length];
      System.arraycopy(addChildren, 0, newChildren, 0, addChildren.length);
      for(int pos = addChildren.length, i = 0; i < oldChildren.length; i++) {
        FileSystemEntry c = oldChildren[i];
        if (! (c instanceof FileSystemEntrySmall)) {
          newChildren[pos++] = c;
        }
      }
      java.util.Arrays.sort(newChildren, FileSystemEntry.COMPARE);
      list.parent.children = newChildren;
      extraHeap += list.heapSize;
    }
    Log.d("diskusage", "allocated " + extraHeap + " B of extra heap");
    Log.d("diskusage", "allocated " + (extraHeap + createdNodeSize) + " B total");
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
  @SuppressLint("DefaultLocale")
  private void scanDirectory(FileSystemEntry parent, LegacyFile file, int depth, long self_blocks) {
    String name = file.getName();
    makeNode(parent, name);
    createdNodeNumDirs = 1;
    createdNodeNumFiles = 0;

    if (depth == maxDepth) {
      createdNode.setSizeInBlocks(calculateSize(file), blockSize);
      // FIXME: get num of dirs and files
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
    int thisNodeSize = createdNodeSize;
    int  thisNodeNumDirs = 1;
    int  thisNodeNumFiles = 0;

    int thisNodeSizeSmall = 0;
    int thisNodeNumFilesSmall = 0;
    int thisNodeNumDirsSmall = 0;
    long smallBlocks = 0;

    ArrayList<FileSystemEntry> children = new ArrayList<>();
    ArrayList<FileSystemEntry> smallChildren = new ArrayList<>();


    long blocks = self_blocks;

    for (String listName : listNames) {
      LegacyFile childFile = file.getChild(listName);

//      if (isLink(child)) continue;
//      if (isSpecial(child)) continue;
      long st_blocks;
      long st_size;
      try {
        StructStat res = Os.stat(childFile.getCannonicalPath());
        // Not regular file and not folder
//        if ((res.st_mode & 0x0100000) == 0 && (res.st_mode & 0x0040000) == 0) continue;
        st_blocks = res.st_blocks;
        st_size = res.st_size;
      } catch (ErrnoException | IOException e) {
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

      if (this.createdNodeSize * sizeThreshold > createdNode.encodedSize) {
        smallChildren.add(createdNode);
        thisNodeSizeSmall += this.createdNodeSize;
        thisNodeNumFilesSmall += files;
        thisNodeNumDirsSmall += dirs;
        smallBlocks += createdNodeBlocks;
      } else {
        children.add(createdNode);
        thisNodeSize += this.createdNodeSize;
        thisNodeNumFiles += files;
        thisNodeNumDirs += dirs;
      }
    }
    thisNode.setSizeInBlocks(blocks, blockSize);

    thisNodeNumDirs += thisNodeNumDirsSmall;
    thisNodeNumFiles += thisNodeNumFilesSmall;

    FileSystemEntry smallFilesEntry = null;

    if ((thisNodeSizeSmall + thisNodeSize) * sizeThreshold <= thisNode.encodedSize
        || smallChildren.isEmpty()) {
      children.addAll(smallChildren);
      thisNodeSize += thisNodeSizeSmall;
    } else {
      String msg;
      if (thisNodeNumDirsSmall == 0) {
        msg = String.format("<%d files>", thisNodeNumFilesSmall);
      } else if (thisNodeNumFilesSmall == 0) {
        msg = String.format("<%d dirs>", thisNodeNumDirsSmall);
      } else {
        msg = String.format("<%d dirs and %d files>",
            thisNodeNumDirsSmall, thisNodeNumFilesSmall);
      }

//        String hidden_path = msg;
//        // FIXME: this is debug
//        for(FileSystemEntry p = thisNode; p != null; p = p.parent) {
//          hidden_path = p.name + "/" + hidden_path;
//        }
//        Log.d("diskusage", hidden_path + " = " + thisNodeSizeSmall);

      makeNode(thisNode, msg);
      // create another one with right type
      createdNode = FileSystemEntrySmall.makeNode(thisNode, msg,
          thisNodeNumFilesSmall + thisNodeNumDirsSmall);
      createdNode.setSizeInBlocks(smallBlocks, blockSize);
      smallFilesEntry = createdNode;
      children.add(createdNode);
      thisNodeSize += createdNodeSize;
      SmallList list = new SmallList(
              thisNode,
              smallChildren.toArray(new FileSystemEntry[0]),
              thisNodeSizeSmall,
              smallBlocks);
      smallLists.add(list);
    }

    // Magic to sort children and keep small files last in the array.
    if (children.size() != 0) {
      long smallFilesEntrySize = 0;
      if (smallFilesEntry != null) {
        smallFilesEntrySize = smallFilesEntry.encodedSize;
        smallFilesEntry.encodedSize = -1;
      }
      thisNode.children = children.toArray(new FileSystemEntry[0]);
      java.util.Arrays.sort(thisNode.children, FileSystemEntry.COMPARE);
      if (smallFilesEntry != null) {
        smallFilesEntry.encodedSize = smallFilesEntrySize;
      }
    }
    createdNode = thisNode;
    createdNodeSize = thisNodeSize;
    createdNodeNumDirs = thisNodeNumDirs;
    createdNodeNumFiles = thisNodeNumFiles;
  }

  private void makeNode(FileSystemEntry parent, String name) {
    createdNode = FileSystemFile.makeNode(parent, name);
    createdNodeSize =
      4 /* ref in FileSystemEntry[] */
      + 16 /* FileSystemEntry */
//      + 10000 /* dummy in FileSystemEntry */
      + 8 + 10 /* aproximation of size string */
      + 8    /* name header */
      + name.length() * 2; /* name length */
    heapSize += createdNodeSize;
    while (heapSize > maxHeapSize && !smallLists.isEmpty()) {
      SmallList removed = smallLists.remove();
      heapSize -= removed.heapSize;
//      print("killed", removed);
    }
  }

  /**
   * Calculate size of the entry reading directory tree
   * @param file is file corresponding to this entry
   * @return size of entry in blocks
   */
  private long calculateSize(@NonNull LegacyFile file) {
    if (file.isLink()) return 0;

    if (file.isFile()) {
      try {
        StructStat res = Os.stat(file.getCannonicalPath());
        return res.st_blocks;
      } catch (ErrnoException|IOException e) {
        return 0;
      }
    }

    LegacyFile[] list = null;
    try {
      list = file.listFiles();
    } catch (SecurityException io) {
      Log.e("diskusage", "list files", io);
    }
    if (list == null) return 0;
    long size = 1;

    for (LegacyFile legacyFile : list) size += calculateSize(legacyFile);
    return size;
  }
}
