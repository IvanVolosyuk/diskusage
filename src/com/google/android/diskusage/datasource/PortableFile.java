package com.google.android.diskusage.datasource;

import android.annotation.TargetApi;
import android.os.Build;

public interface PortableFile {
  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  boolean isExternalStorageEmulated();
  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  boolean isExternalStorageRemovable();

  /** Retries with getAbsolutePath() on IOException */
  String getCanonicalPath();
  String getAbsolutePath();

  @TargetApi(Build.VERSION_CODES.GINGERBREAD)
  long getTotalSpace();
}
