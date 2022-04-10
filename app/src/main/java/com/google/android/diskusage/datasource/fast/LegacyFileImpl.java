package com.google.android.diskusage.datasource.fast;

import android.support.annotation.NonNull;
import java.io.File;
import java.io.IOException;

import com.google.android.diskusage.datasource.LegacyFile;

import org.jetbrains.annotations.Contract;

public class LegacyFileImpl implements LegacyFile {

  private final File file;

  private LegacyFileImpl(File file) {
    this.file = file;
  }

  @NonNull
  @Contract("_ -> new")
  static LegacyFile createRoot(String root) {
    return new LegacyFileImpl(new File(root));
  }

  @Override
  public String getName() {
    return file.getName();
  }

  @Override
  public String getCannonicalPath() throws IOException {
    return file.getCanonicalPath();
  }

  @Override
  public boolean isLink() {
    try {
      if (file.getCanonicalPath().equals(file.getPath())) return false;
    } catch(Throwable ignored) {}
    return true;
  }

  @Override
  public boolean isFile() {
    return file.isFile();
  }

  @Override
  public long length() {
    return file.length();
  }

  @Override
  public LegacyFile[] listFiles() {
    File[] children = file.listFiles();
    LegacyFile[] res = new LegacyFile[children.length];
    for (int i = 0; i < children.length; i++) {
      res[i] = new LegacyFileImpl(children[i]);
    }
    return res;
  }

  @Override
  public String[] list() {
    return file.list();
  }

  @Override
  public LegacyFile getChild(String childName) {
    return new LegacyFileImpl(new File(file, childName));
  }
}
