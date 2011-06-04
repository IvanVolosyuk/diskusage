package com.google.android.diskusage.entity;

public class FileSystemRoot extends FileSystemEntry {
  final String rootPath;

  protected FileSystemRoot(String name, String rootPath) {
    super(null, name);
    this.rootPath = rootPath;
  }
  
  public static FileSystemRoot makeNode(String name, String rootPath) {
    return new FileSystemRoot(name, rootPath);
  }
}
