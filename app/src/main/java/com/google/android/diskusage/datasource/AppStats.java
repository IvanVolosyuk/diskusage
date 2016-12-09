package com.google.android.diskusage.datasource;

import android.annotation.TargetApi;
import android.os.Build;

public interface AppStats {
  long getCacheSize();
  long getDataSize();
  long getCodeSize();
  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  long getExternalCacheSize();
  @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
  long getExternalCodeSize();
  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  long getExternalDataSize();
  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  long getExternalMediaSize();
  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  long getExternalObbSize();
}