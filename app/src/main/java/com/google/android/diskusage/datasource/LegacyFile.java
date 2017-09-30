package com.google.android.diskusage.datasource;

import java.io.IOException;

public interface LegacyFile {
  String getName();
  String getCannonicalPath() throws IOException;

  boolean isLink();
  boolean isFile();
  long length();

  LegacyFile[] listFiles();
  String[] list();

  LegacyFile getChild(String string);
}