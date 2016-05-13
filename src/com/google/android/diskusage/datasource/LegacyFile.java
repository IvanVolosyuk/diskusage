package com.google.android.diskusage.datasource;

public interface LegacyFile {
  String getName();

  boolean isLink();
  boolean isFile();
  long length();

  LegacyFile[] listFiles();
  String[] list();

  LegacyFile getChild(String string);
}
