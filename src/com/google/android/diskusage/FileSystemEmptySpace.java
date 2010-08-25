package com.google.android.diskusage;

public class FileSystemEmptySpace extends FileSystemSpecial {
  public FileSystemEmptySpace(String name, long size) {
    super(name, size, null);
  }
}
