package com.google.android.diskusage;

public class FileSystemSpecial extends FileSystemEntry {
  AppFilter filter;

  public FileSystemSpecial(String name, long size, long blockSize) {
    super(name, size, blockSize);
  }
  
  public FileSystemSpecial(String name, FileSystemEntry[] children, long blockSize) {
    super(name, children, blockSize);
  }
}
