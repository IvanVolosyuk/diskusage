package com.google.android.diskusage.datasource.fast;

import android.os.Environment;
import androidx.annotation.NonNull;
import com.google.android.diskusage.datasource.PortableFile;
import com.google.android.diskusage.utils.PathHelper;
import java.io.File;

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

  @NonNull
  public static PortableFile[] getExternalAppFilesDirs() {
    final File[] externalAppFilesPaths = PathHelper.getExternalAppFilesPaths();
    final PortableFile[] result = new PortableFile[externalAppFilesPaths.length];
    int i = 0;
    for (final File dir : externalAppFilesPaths) {
      result[i++] = make(dir);
    }
    return result;
  }

  @Override
  public boolean isExternalStorageEmulated() {
    try {
      return Environment.isExternalStorageEmulated(file);
    } catch (Exception e) {
      return false;
    }
  }

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