package com.google.android.diskusage;

public class FileSystemSpecial extends FileSystemEntry {

  public FileSystemSpecial(String name, long size, FileSystemEntry[] children) {
    super(name, size, children);
  }
  
  public FileSystemSpecial(String name, FileSystemEntry[] children) {
    super(name, children);
  }
}
