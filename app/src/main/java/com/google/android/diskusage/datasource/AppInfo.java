package com.google.android.diskusage.datasource;

public interface AppInfo {
  int getFlags();
  String getDataDir();
  boolean isEnabled();
  String getName();
  String getPackageName();
  String getPublicSourceDir();
  String getSourceDir();
  String[] getSplitSourceDirs();
  public String getApplicationLabel();
}