package com.google.android.diskusage;

import java.io.File;
import java.util.ArrayList;

import com.google.android.diskusage.FileSystemEntry.ExcludeFilter;

public class Scanner {
  private final int maxdepth;
  private final int blockSize;
  private final ExcludeFilter excludeFilter;
  private final long sizeThreshold;
  
  private FileSystemEntry createdNode;
  private long createdNodeSize;
  
  Scanner(int maxdepth, int blockSize, ExcludeFilter excludeFilter, long sizeThreshold) {
    this.maxdepth = maxdepth;
    this.blockSize = blockSize;
    this.excludeFilter = excludeFilter;
    this.sizeThreshold = sizeThreshold;
  }
  
  FileSystemEntry scan(File file) {
    scanDirectory(null, file, 0);
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
  private void scanDirectory(FileSystemEntry parent, File file, int depth) {
    String name = file.getName();
    makeNode(parent, name);
    
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
      createdNode.setSizeInBlocks(calculateSize(file));
      return;
    }

    File[] list = file.listFiles();
    if (list == null) return;
    FileSystemEntry thisNode = createdNode;
    long thisNodeSize = createdNodeSize;
    long thisSmallChildrenSize = 0;
    long smallBlocks = 0;
    ArrayList<FileSystemEntry> children = new ArrayList<FileSystemEntry>();
    ArrayList<FileSystemEntry> smallChildren = new ArrayList<FileSystemEntry>();


    long blocks = 0;

    for (int i = 0; i < list.length; i++) {
      File childFile = list[i];

//      if (isLink(child)) continue;
//      if (isSpecial(child)) continue;

      if (childFile.isFile()) {
        makeNode(thisNode, childFile.getName());
        createdNode.initSizeInBytes(childFile.length(), blockSize);
      } else {
        // directory
        scanDirectory(thisNode, childFile, depth + 1);
      }

      long createdNodeBlocks = createdNode.getSizeInBlocks(); 
      blocks += createdNodeBlocks;
      
      if (this.createdNodeSize * sizeThreshold > createdNode.encodedSize) {
        smallChildren.add(createdNode);
        thisSmallChildrenSize += this.createdNodeSize;
        smallBlocks += createdNodeBlocks;
      } else {
        children.add(createdNode);
        thisNodeSize += this.createdNodeSize;
      }
    }
    thisNode.setSizeInBlocks(blocks);
    
    if ((thisSmallChildrenSize + thisNodeSize) * sizeThreshold <= thisNode.encodedSize) {
      children.addAll(smallChildren);
      thisNodeSize += thisSmallChildrenSize;
    } else if (smallChildren.size() < 2) {
      children.addAll(smallChildren);
      thisNodeSize += thisSmallChildrenSize;
    } else {
      makeNode(thisNode, "<small files>");
      createdNode.setSizeInBlocks(smallBlocks);
      children.add(createdNode);
      thisNodeSize += createdNodeSize;
    }

    if (children.size() != 0) {
      thisNode.children = children.toArray(new FileSystemEntry[children.size()]);
      java.util.Arrays.sort(thisNode.children, FileSystemEntry.COMPARE);
    }
    createdNode = thisNode;
    createdNodeSize = thisNodeSize;
  }
  
  private void makeNode(FileSystemEntry parent, String name) {
    createdNode = FileSystemEntry.makeNode(parent, name);
    createdNodeSize =
      4 /* ref in FileSystemEntry[] */
      + 16 /* FileSystemEntry */
//      + 4096 /* dummy in FileSystemEntry */
      + 8 + 10 /* aproximation of size string */
      + 8    /* name header */
      + name.length() * 2; /* name length */
  }

  /**
   * Calculate size of the entry reading directory tree
   * @param file is file corresponding to this entry
   * @return size of entry in blocks
   */
  private final long calculateSize(File file) {
    if (isLink(file)) return 0;

    if (file.isFile()) {
      long size = (file.length() + (blockSize - 1)) / blockSize;
      if (size == 0) size = 1;
      return size;
    }

    File[] list = file.listFiles();
    if (list == null) return 0;
    long size = 1;

    for (int i = 0; i < list.length; i++)
      size += calculateSize(list[i]);
    return size;
  }

  private static boolean isLink(File file) {
    try {
      if (file.getCanonicalPath().equals(file.getPath())) return false;
    } catch(Throwable t) {}
    return true;
  }
}
