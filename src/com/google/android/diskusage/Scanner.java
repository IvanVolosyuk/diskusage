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

import java.util.ArrayList;
import java.util.PriorityQueue;

import android.util.Log;

import com.google.android.diskusage.DiskUsage.ProgressGenerator;
import com.google.android.diskusage.datasource.LegacyFile;
import com.google.android.diskusage.entity.FileSystemEntry;
import com.google.android.diskusage.entity.FileSystemEntry.ExcludeFilter;
import com.google.android.diskusage.entity.FileSystemEntrySmall;
import com.google.android.diskusage.entity.FileSystemFile;

public class Scanner implements ProgressGenerator {
  private final int maxdepth;
  private final int blockSize;
  private final ExcludeFilter excludeFilter;
  private final long sizeThreshold;

  private FileSystemEntry createdNode;
  private int createdNodeSize;
  private int createdNodeNumFiles;
  private int createdNodeNumDirs;

  private int heapSize;
  private final int maxHeapSize;
  private final PriorityQueue<SmallList> smallLists = new PriorityQueue<SmallList>();
  long pos;
  FileSystemEntry lastCreatedFile;

  public FileSystemEntry lastCreatedFile() {
    return lastCreatedFile;
  }
  public long pos() {
    return pos;
  }

  private class SmallList implements Comparable<SmallList> {
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
    public int compareTo(SmallList that) {
      return spaceEfficiency < that.spaceEfficiency ? -1 : (spaceEfficiency == that.spaceEfficiency ? 0 : 1);
    }
  };

  Scanner(int maxdepth, int blockSize, ExcludeFilter excludeFilter,
      long allocatedBlocks, int maxHeap) {
    this.maxdepth = maxdepth;
    this.blockSize = blockSize;
    this.excludeFilter = excludeFilter;
    this.sizeThreshold = (allocatedBlocks << FileSystemEntry.blockOffset) / (maxHeap / 2);
    this.maxHeapSize = maxHeap;
//    this.blockAllowance = (allocatedBlocks << FileSystemEntry.blockOffset) / 2;
//    this.blockAllowance = (maxHeap / 2) * sizeThreshold;
    Log.d("diskusage", "allocatedBlocks " + allocatedBlocks);
    Log.d("diskusage", "maxHeap " + maxHeap);
    Log.d("diskusage", "sizeThreshold = " + sizeThreshold / (float) (1 << FileSystemEntry.blockOffset));
  }

  private void print(String msg, SmallList list) {
    String hidden_path = "";
    // FIXME: this is debug
    for(FileSystemEntry p = list.parent; p != null; p = p.parent) {
      hidden_path = p.name + "/" + hidden_path;
    }
    Log.d("diskusage", msg + " " + hidden_path + " = " + list.heapSize + " " + list.spaceEfficiency);
  }

  FileSystemEntry scan(LegacyFile file) {
//    file = new NativeFile(file);

    scanDirectory(null, file, 0, excludeFilter);
    Log.d("diskusage", "allocated " + createdNodeSize + " B of heap");

    int extraHeap = 0;

    // Restoring blocks
    for (SmallList list : smallLists) {
      print("restored", list);

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
   * @param maxdepth maximum directory tree depth
   */
  private void scanDirectory(FileSystemEntry parent, LegacyFile file,
                             int depth, ExcludeFilter excludeFilter) {
    String name = file.getName();
    makeNode(parent, name);
    createdNodeNumDirs = 1;
    createdNodeNumFiles = 0;

    ExcludeFilter childFilter = null;
    if (excludeFilter != null) {
      // this path is requested for exclusion
      if (excludeFilter.childFilter == null) {
        return;
      }
      childFilter = excludeFilter.childFilter.get(name);
      if (childFilter != null && childFilter.childFilter == null) {
        return;
      }
    }

    if (depth == maxdepth) {
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

    ArrayList<FileSystemEntry> children = new ArrayList<FileSystemEntry>();
    ArrayList<FileSystemEntry> smallChildren = new ArrayList<FileSystemEntry>();


    long blocks = 0;

    for (int i = 0; i < listNames.length; i++) {
      LegacyFile childFile = file.getChild(listNames[i]);

//      if (isLink(child)) continue;
//      if (isSpecial(child)) continue;

      int dirs = 0, files = 1;
      if (childFile.isFile()) {
        makeNode(thisNode, childFile.getName());
        createdNode.initSizeInBytes(childFile.length(), blockSize);
        pos += createdNode.getSizeInBlocks();
        lastCreatedFile = createdNode;
      } else {
        // directory
        scanDirectory(thisNode, childFile, depth + 1, childFilter);
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
      String msg = null;
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
          smallChildren.toArray(new FileSystemEntry[smallChildren.size()]),
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
      thisNode.children = children.toArray(new FileSystemEntry[children.size()]);
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
//    try {
//      Thread.sleep(10);
//    } catch (Throwable t) {}

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
      print("killed", removed);

    }
  }

  /**
   * Calculate size of the entry reading directory tree
   * @param file is file corresponding to this entry
   * @return size of entry in blocks
   */
  private final long calculateSize(LegacyFile file) {
    if (file.isLink()) return 0;

    if (file.isFile()) {
      long size = (file.length() + (blockSize - 1)) / blockSize;
      if (size == 0) size = 1;
      return size;
    }

    LegacyFile[] list = null;
    try {
      list = file.listFiles();
    } catch (SecurityException io) {
      Log.e("diskusage", "list files", io);
    }
    if (list == null) return 0;
    long size = 1;

    for (int i = 0; i < list.length; i++)
      size += calculateSize(list[i]);
    return size;
  }
}
