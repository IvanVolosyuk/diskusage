package com.google.android.diskusage;

public class FileSystemFreeSpace extends FileSystemSpecial {
  public FileSystemFreeSpace(String name, long size, int blockSize) {
    super(name, size, blockSize);
  }
}
