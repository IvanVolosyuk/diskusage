package com.google.android.diskusage.filesystem.entity;

import android.support.annotation.NonNull;
import org.jetbrains.annotations.Contract;

public class FileSystemFile extends FileSystemEntry {
  private FileSystemFile(FileSystemEntry parent, String name) {
    super(parent, name);
  }

  @NonNull
  @Contract("_, _ -> new")
  public static FileSystemEntry makeNode(
      FileSystemEntry parent, String name) {
    return new FileSystemFile(parent, name);
  }


  @Override
  public boolean isDeletable() {
    return true;
  }

  @Override
  public FileSystemEntry create() {
    return new FileSystemFile(null, this.name);
  }
}
