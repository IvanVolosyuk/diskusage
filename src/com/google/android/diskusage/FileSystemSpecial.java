package com.google.android.diskusage;

public class FileSystemSpecial extends FileSystemEntry {
  AppFilter filter;

  public FileSystemSpecial(String name, long size, int blockSize) {
    super(null, name);
    initSizeInBytes(size, blockSize);
  }
  
  public FileSystemSpecial(String name, FileSystemEntry[] children, int blockSize) {
    super(null, name);
    this.setChildren(children);
  }
}
