package com.google.android.diskusage.entity;

public class FileSystemFile extends FileSystemEntry {
  private FileSystemFile(FileSystemEntry parent, String name) {
    super(parent, name);
  }
  
  public static FileSystemEntry makeNode(
      FileSystemEntry parent, String name) {
    return new FileSystemFile(parent, name);
  }
}
