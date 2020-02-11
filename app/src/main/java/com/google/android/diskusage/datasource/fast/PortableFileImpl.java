package com.google.android.diskusage.datasource.fast;

import java.io.File;
import java.io.IOException;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Environment;

import com.google.android.diskusage.datasource.PortableFile;

public class PortableFileImpl implements PortableFile {
  private final File file;

  private PortableFileImpl(File file) {
    this.file = file;
  }

  public static PortableFileImpl make(File file) {
    if (file == null) {
      return null;
    }
    return new PortableFileImpl(file);
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  @Override
  public boolean isExternalStorageEmulated() {
    try {
      return Environment.isExternalStorageEmulated(file);
    } catch (Exception e) {
      return false;
    }
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  @Override
  public boolean isExternalStorageRemovable() {
    try {
      return Environment.isExternalStorageRemovable(file);
    } catch (Exception e) {
      return false;
    }

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