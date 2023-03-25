package com.volosyukivan.diskusage.datasource;

public interface PortableFile {
  boolean isExternalStorageEmulated();
  boolean isExternalStorageRemovable();

  /** Retries with getAbsolutePath() on IOException */
  String getCanonicalPath();
  String getAbsolutePath();

  long getTotalSpace();
}
