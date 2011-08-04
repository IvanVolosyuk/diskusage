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

  @Override
  public FileSystemEntry create() {
    return new FileSystemRoot(this.name, this.rootPath);
  }

  @Override
  public FileSystemEntry filter(CharSequence pattern, int blockSize) {
    // don't match name
    return filterChildren(pattern, blockSize);
  }
}
