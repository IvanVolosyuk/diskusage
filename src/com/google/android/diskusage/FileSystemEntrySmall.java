package com.google.android.diskusage;

public class FileSystemEntrySmall extends FileSystemEntry {
  int numFiles;
  public FileSystemEntrySmall(FileSystemEntry parent, String name, int numFiles) {
    super(parent, name);
    this.numFiles = numFiles;
  }
  
  public static FileSystemEntrySmall makeNode(
      FileSystemEntry parent, String name, int numFiles) {
    return new FileSystemEntrySmall(parent, name, numFiles);
  }
}
