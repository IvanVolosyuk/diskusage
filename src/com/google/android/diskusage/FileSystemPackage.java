package com.google.android.diskusage;

public class FileSystemPackage extends FileSystemEntry {

  String pkg;
  
  public FileSystemPackage(FileSystemEntry parent, String name, String pkg, int size,
      FileSystemEntry[] children) {
    super(parent, name, size, children);
    this.pkg = pkg;
  }
}
