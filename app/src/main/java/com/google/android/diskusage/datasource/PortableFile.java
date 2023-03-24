package com.google.android.diskusage.datasource;

public interface PortableFile {
  boolean isExternalStorageEmulated();
  boolean isExternalStorageRemovable();

  /** Retries with getAbsolutePath() on IOException */
  String getCanonicalPath();
  String getAbsolutePath();

  long getTotalSpace();
}
