package com.google.android.diskusage;

public class FileSystemSpecial extends FileSystemEntry {
  AppFilter filter;

  public FileSystemSpecial(String name, long size, int blockSize) {
    super(name, size, blockSize);
  }
  
  public FileSystemSpecial(String name, FileSystemEntry[] children, int blockSize) {
    super(name, children, blockSize);
  }
}
