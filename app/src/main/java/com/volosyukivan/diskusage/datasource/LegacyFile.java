package com.volosyukivan.diskusage.datasource;

import java.io.IOException;

public interface LegacyFile {
  String getName();
  String getCanonicalPath() throws IOException;

  boolean isLink();
  boolean isFile();
  long length();

  LegacyFile[] listFiles();
  String[] list();

  LegacyFile getChild(String string);
}