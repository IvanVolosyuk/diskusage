package com.google.android.diskusage;

public class FileSystemFreeSpace extends FileSystemSystemSpace {
  public FileSystemFreeSpace(String name, long size, long blockSize) {
    super(name, size, blockSize);
  }
}
