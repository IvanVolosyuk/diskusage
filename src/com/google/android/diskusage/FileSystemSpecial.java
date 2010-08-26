package com.google.android.diskusage;

public class FileSystemSpecial extends FileSystemEntry {
  AppFilter filter;

  public FileSystemSpecial(String name, long size) {
    super(name, size);
  }
  
  public FileSystemSpecial(String name, FileSystemEntry[] children) {
    super(name, children);
  }
}
