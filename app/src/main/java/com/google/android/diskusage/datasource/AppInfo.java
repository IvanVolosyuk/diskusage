package com.google.android.diskusage.datasource;

import android.annotation.TargetApi;
import android.os.Build;

public interface AppInfo {
  int getFlags();
  String getDataDir();
  boolean isEnabled();
  String getName();
  String getPackageName();
  String getPublicSourceDir();
  String getSourceDir();
  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  String[] getSplitSourceDirs();
  public String getApplicationLabel();
}