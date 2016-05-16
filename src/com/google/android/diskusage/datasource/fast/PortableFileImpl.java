package com.google.android.diskusage.datasource.fast;

import java.io.File;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Environment;

import com.google.android.diskusage.datasource.PortableFile;

public class PortableFileImpl implements PortableFile {
  private final File file;

  PortableFileImpl(File file) {
    this.file = file;
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  @Override
  public boolean isExternalStorageEmulated() {
    return Environment.isExternalStorageEmulated(file);
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  @Override
  public boolean isExternalStorageRemovable() {
    return Environment.isExternalStorageRemovable(file);
  }

  @Override
  public String getCanonicalPath() {
    try {
      return file.getCanonicalPath();
    } catch (Exception e) {
      return file.getAbsolutePath();
    }
  }

  @Override
  public String getAbsolutePath() {
    return file.getAbsolutePath();
  }

  @TargetApi(Build.VERSION_CODES.GINGERBREAD)
  @Override
  public long getTotalSpace() {
    return file.getTotalSpace();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof PortableFile)) {
      return false;
    }
    PortableFile other = (PortableFile) o;
    return other.getAbsolutePath().equals(getAbsolutePath());
  }

  @Override
  public int hashCode() {
    return getAbsolutePath().hashCode();
  }
}