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

package com.google.android.diskusage.core;

import android.annotation.SuppressLint;
import android.content.Context;
import androidx.annotation.NonNull;
import com.google.android.diskusage.datasource.DataSource;
import com.google.android.diskusage.filesystem.entity.FileSystemEntry;
import com.google.android.diskusage.filesystem.entity.FileSystemEntrySmall;
import com.google.android.diskusage.filesystem.entity.FileSystemFile;
import com.google.android.diskusage.filesystem.mnt.MountPoint;
import com.google.android.diskusage.ui.DiskUsage.ProgressGenerator;
import com.google.android.diskusage.utils.Logger;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.PriorityQueue;

public class NativeScanner implements ProgressGenerator {
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
  // private volatile int deepDepth = 0;

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
  }

  private InputStream is;
  private final Context context;

  private static final int bufsize = 65536;
  private int offset = 0;
  private int allocated = 0;
  private final byte[] buffer = new byte[bufsize];

  private void move() {
//    Log.d("diskusage", "MOVE!");
    if (offset == 0) throw new RuntimeException("Error: too large entity size");
    System.arraycopy(buffer, offset, buffer, 0, allocated - offset);
    allocated -= offset;
    offset = 0;
  }

  public void read() throws IOException {
//    Log.d("diskusage", "READ!");
    if (allocated == bufsize) {
      move();
    }
    int res = is.read(buffer, allocated, Math.min(bufsize - allocated, 256));
    if (res <= 0) {
      throw new RuntimeException("Error: no more data");
    }
    allocated += res;
  }

  public byte getByte() throws IOException {
    while (true) {
      if (offset < allocated) {
        return buffer[offset++];
      }
      read();
    }
  }

  public long getLong() throws IOException {
    long res = 0;
    byte b;
    while ((b = getByte()) != 0) {
      if (b < '0' || b > '9') throw new RuntimeException("Error: number format error");
      res = res * 10 + (b - '0');
    }
//    Log.d("diskusage", "long = " + res);
    return res;
  }

  public String getString() throws IOException {
    byte[] buffer = this.buffer;
    int startPos = offset;


    while (true) {
      for (int i = startPos; i < allocated; i++) {
        if (buffer[i] == 0) {
          String res = new String(buffer, offset, i - offset, "UTF-8");
          offset = i + 1;
//          Log.d("diskusage", "string = " + res);
          return res;
        }
      }
      int startOffset = startPos - offset;
      read();
      startPos = offset + startOffset;
    }
  }

  enum Type {
    NONE,
    DIR,
    FILE
  }

  public Type getType() throws IOException {
    int c = getByte();
//    Log.d("diskusage", "type = " + (char)c);
    switch (c) {
    case 'D': return Type.DIR;
    case 'F': return Type.FILE;
    case 'Z': return Type.NONE;
    default: throw new RuntimeException("Error: incorrect entity type");
    }
  }

  public NativeScanner(Context context, long blockSize, long allocatedBlocks, int maxHeap) {
    this.blockSize = blockSize;
    this.blockSizeIn512Bytes = blockSize / 512;
    this.sizeThreshold = (allocatedBlocks << FileSystemEntry.blockOffset) / (maxHeap / 2);
    this.maxHeapSize = maxHeap;
    this.context = context;
//    this.blockAllowance = (allocatedBlocks << FileSystemEntry.blockOffset) / 2;
//    this.blockAllowance = (maxHeap / 2) * sizeThreshold;
    Logger.getLOGGER().d("NativeScanner: allocatedBlocks %s", allocatedBlocks);
    Logger.getLOGGER().d("NativeScanner: maxHeap %s", maxHeap);
    Logger.getLOGGER().d("NativeScanner: sizeThreshold = %s", sizeThreshold / (float) (1 << FileSystemEntry.blockOffset));
  }

  private void print(String msg, @NonNull SmallList list) {
    StringBuilder hidden_path = new StringBuilder();
    // FIXME: this is debug
    for(FileSystemEntry p = list.parent; p != null; p = p.parent) {
      hidden_path.insert(0, p.name + "/");
    }
    Logger.getLOGGER().d("%s %s = %s %s", msg, hidden_path, list.heapSize, list.spaceEfficiency);
  }

  public FileSystemEntry scan(@NonNull MountPoint mountPoint) throws IOException, InterruptedException {
    is = DataSource.get().createNativeScanner(
        context, mountPoint.getRoot(), mountPoint.isRootRequired());
    // while (getByte() != 0);

    Type type = getType();
    if (type != Type.DIR) throw new RuntimeException("Error: no mount point");
    scanDirectory(null, getString(), 0);
    Logger.getLOGGER().d("NativeScanner.scan(): Allocated %s B of heap", createdNodeSize);

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
    Logger.getLOGGER().d("allocated " + extraHeap + " B of extra heap");
    Logger.getLOGGER().d("allocated " + (extraHeap + createdNodeSize) + " B total");
    if (offset != allocated) throw new RuntimeException("Error: extra data, " + (allocated - offset) + " bytes");
    is.close();
    return createdNode;
  }


  private static class SoftStack {
    private enum State {
      PRE_LOOP,
      LOOP,
      POST_LOOP
    }
    State state;

    FileSystemEntry parent;
    String name;
    int depth;

    long dirBlockSize;
    FileSystemEntry thisNode;
    int thisNodeSize;
    int thisNodeNumDirs;
    int thisNodeNumFiles;

    int thisNodeSizeSmall;
    int thisNodeNumFilesSmall;
    int thisNodeNumDirsSmall;
    long smallBlocks;

    ArrayList<FileSystemEntry> children;
    ArrayList<FileSystemEntry> smallChildren;
    long blocks;
    Type childType;
    int dirs;
    int files;
    SoftStack prev;
  }

  // Very complicated version of scanDirectory() which uses soft stack instead
  // of real one.
  @SuppressLint("DefaultLocale")
  private void scanDirectorySoftStack(FileSystemEntry parent_, String name_,
                                      int depth_) throws IOException {
    SoftStack s = new SoftStack();
    s.parent = parent_;
    s.name = name_;
    s.depth = depth_;
    s.state = SoftStack.State.PRE_LOOP;

    restart: while(true) {
      switch (s.state) {
      case PRE_LOOP:
        // deepDepth = s.depth;
        s.dirBlockSize = getLong() / blockSizeIn512Bytes;
        /*long dirBytesSize =*/ getLong();  // side-effects
        makeNode(s.parent, s.name);
        createdNodeNumDirs = 1;
        createdNodeNumFiles = 0;

        s.thisNode = createdNode;
        lastCreatedFile = createdNode;
        s.thisNodeSize = createdNodeSize;
        s.thisNodeNumDirs = 1;
        s.thisNodeNumFiles = 0;

        s.thisNodeSizeSmall = 0;
        s.thisNodeNumFilesSmall = 0;
        s.thisNodeNumDirsSmall = 0;
        s.smallBlocks = 0;

        s.children = new ArrayList<>();
        s.smallChildren = new ArrayList<>();


        s.blocks = 0;

      case LOOP:
        s.state = SoftStack.State.LOOP;
        while (true) {
          s.childType = getType();
          if (s.childType == Type.NONE) break;

          //if (isLink(child)) continue;
          //if (isSpecial(child)) continue;

          s.dirs = 0;
          s.files = 1;
          if (s.childType == Type.FILE) {
            makeNode(s.thisNode, getString());
            long childBlocks = getLong() / blockSizeIn512Bytes;
            long childBytes = getLong();
            if (childBlocks == 0) continue;
            createdNode.initSizeInBytesAndBlocks(childBytes, childBlocks, blockSize);
            pos += createdNode.getSizeInBlocks();
            lastCreatedFile = createdNode;
            //Log.d("diskusage", createdNode.path2());
          } else {
            // directory
            SoftStack new_s = new SoftStack();
            new_s.prev = s;
            new_s.parent = s.thisNode;
            new_s.name = getString();
            new_s.depth = s.depth + 1;
            new_s.state = SoftStack.State.PRE_LOOP;
            s = new_s;
            continue restart;
          }

          long createdNodeBlocks = createdNode.getSizeInBlocks();
          s.blocks += createdNodeBlocks;

          if (this.createdNodeSize * sizeThreshold > createdNode.encodedSize) {
            s.smallChildren.add(createdNode);
            s.thisNodeSizeSmall += this.createdNodeSize;
            s.thisNodeNumFilesSmall += s.files;
            s.thisNodeNumDirsSmall += s.dirs;
            s.smallBlocks += createdNodeBlocks;
          } else {
            s.children.add(createdNode);
            s.thisNodeSize += this.createdNodeSize;
            s.thisNodeNumFiles += s.files;
            s.thisNodeNumDirs += s.dirs;
          }
        }
      case POST_LOOP:
        s.state = SoftStack.State.POST_LOOP;
        s.thisNode.setSizeInBlocks(s.blocks + s.dirBlockSize, blockSize);

        s.thisNodeNumDirs += s.thisNodeNumDirsSmall;
        s.thisNodeNumFiles += s.thisNodeNumFilesSmall;

        FileSystemEntry smallFilesEntry = null;

        if ((s.thisNodeSizeSmall + s.thisNodeSize) * sizeThreshold <= s.thisNode.encodedSize
            || s.smallChildren.isEmpty()) {
          s.children.addAll(s.smallChildren);
          s.thisNodeSize += s.thisNodeSizeSmall;
        } else {
          String msg;
          if (s.thisNodeNumDirsSmall == 0) {
            msg = String.format("<%d files>", s.thisNodeNumFilesSmall);
          } else if (s.thisNodeNumFilesSmall == 0) {
            msg = String.format("<%d dirs>", s.thisNodeNumDirsSmall);
          } else {
            msg = String.format("<%d dirs and %d files>",
                s.thisNodeNumDirsSmall, s.thisNodeNumFilesSmall);
          }

          makeNode(s.thisNode, msg);
          // create another one with right type
          createdNode = FileSystemEntrySmall.makeNode(s.thisNode, msg,
              s.thisNodeNumFilesSmall + s.thisNodeNumDirsSmall);
          createdNode.setSizeInBlocks(s.smallBlocks, blockSize);
          smallFilesEntry = createdNode;
          s.children.add(createdNode);
          s.thisNodeSize += createdNodeSize;
          SmallList list = new SmallList(
                  s.thisNode,
                  s.smallChildren.toArray(new FileSystemEntry[0]),
                  s.thisNodeSizeSmall,
                  s.smallBlocks);
          smallLists.add(list);
        }

        // Magic to sort children and keep small files last in the array.
        if (s.children.size() != 0) {
          long smallFilesEntrySize = 0;
          if (smallFilesEntry != null) {
            smallFilesEntrySize = smallFilesEntry.encodedSize;
            smallFilesEntry.encodedSize = -1;
          }
          s.thisNode.children = s.children.toArray(new FileSystemEntry[0]);
          java.util.Arrays.sort(s.thisNode.children, FileSystemEntry.COMPARE);
          if (smallFilesEntry != null) {
            smallFilesEntry.encodedSize = smallFilesEntrySize;
          }
        }
        createdNode = s.thisNode;
        createdNodeSize = s.thisNodeSize;
        createdNodeNumDirs = s.thisNodeNumDirs;
        createdNodeNumFiles = s.thisNodeNumFiles;
      }
      s = s.prev;
      if (s == null) return;
      s.dirs = createdNodeNumDirs;
      s.files = createdNodeNumFiles;
      // Finish missed part of inner loop
      long createdNodeBlocks = createdNode.getSizeInBlocks();
      s.blocks += createdNodeBlocks;

      if (this.createdNodeSize * sizeThreshold > createdNode.encodedSize) {
        s.smallChildren.add(createdNode);
        s.thisNodeSizeSmall += this.createdNodeSize;
        s.thisNodeNumFilesSmall += s.files;
        s.thisNodeNumDirsSmall += s.dirs;
        s.smallBlocks += createdNodeBlocks;
      } else {
        s.children.add(createdNode);
        s.thisNodeSize += this.createdNodeSize;
        s.thisNodeNumFiles += s.files;
        s.thisNodeNumDirs += s.dirs;
      }
    }
  }


  /**
   * Scan directory object.
   * This constructor starts recursive scan to find all descendent files and directories.
   * Stores parent into field, name obtained from file, size of this directory
   * is calculated as a sum of all children.
   * @param parent parent directory object.
   * @param depth current directory tree depth
   * @throws IOException if can not read directory or else
   */
  @SuppressLint("DefaultLocale")
  private void scanDirectory(FileSystemEntry parent, String name,
                             int depth) throws IOException {
    if (depth > 10) {
      scanDirectorySoftStack(parent, name, depth);
      return;
    }
    long dirBlockSize = getLong() / blockSizeIn512Bytes;
    /*long dirBytesSize =*/ getLong();
    makeNode(parent, name);
    createdNodeNumDirs = 1;
    createdNodeNumFiles = 0;

    FileSystemEntry thisNode = createdNode;
    int thisNodeSize = createdNodeSize;
    int thisNodeNumDirs = 1;
    int thisNodeNumFiles = 0;

    int thisNodeSizeSmall = 0;
    int thisNodeNumFilesSmall = 0;
    int thisNodeNumDirsSmall = 0;
    long smallBlocks = 0;

    ArrayList<FileSystemEntry> children = new ArrayList<>();
    ArrayList<FileSystemEntry> smallChildren = new ArrayList<>();


    long blocks = 0;

    while (true) {
      Type childType = getType();
      if (childType == Type.NONE) break;

//      if (isLink(child)) continue;
//      if (isSpecial(child)) continue;

      int dirs = 0, files = 1;
      if (childType == Type.FILE) {
        makeNode(thisNode, getString());
        long childBlocks = getLong() / blockSizeIn512Bytes;
        long childBytes = getLong();
        if (childBlocks == 0) continue;
        createdNode.initSizeInBytesAndBlocks(childBytes, childBlocks, blockSize);
        pos += createdNode.getSizeInBlocks();
        lastCreatedFile = createdNode;
//        Log.d("diskusage", createdNode.path2());
      } else {
        // directory
        scanDirectory(thisNode, getString(), depth + 1);
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
    thisNode.setSizeInBlocks(blocks + dirBlockSize, blockSize);

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
}
