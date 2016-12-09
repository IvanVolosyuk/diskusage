package com.google.android.diskusage.datasource.fast;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.StatFs;

import com.google.android.diskusage.datasource.StatFsSource;

public class StatFsSourceImpl implements StatFsSource {

  private final StatFs statFs;

  public StatFsSourceImpl(String path) {
    this.statFs = new StatFs(path);
  }

  @Deprecated
  @Override
  public int getAvailableBlocks() {
    return statFs.getAvailableBlocks();
  }

  @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
  @Override
  public long getAvailableBlocksLong() {
    return statFs.getAvailableBlocksLong();
  }

  @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
  @Override
  public long getAvailableBytes() {
    return statFs.getAvailableBytes();
  }

  @Deprecated
  @Override
  public int getBlockCount() {
    return statFs.getBlockCount();
  }

  @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
  @Override
  public long getBlockCountLong() {
    return statFs.getBlockCountLong();
  }

  @Deprecated
  @Override
  public int getBlockSize() {
    return statFs.getBlockSize();
  }

  @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
  @Override
  public long getBlockSizeLong() {
    return statFs.getBlockSizeLong();
  }

  @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
  @Override
  public long getFreeBytes() {
    return statFs.getFreeBytes();
  }

  @Deprecated
  @Override
  public int getFreeBlocks() {
    return statFs.getFreeBlocks();
  }

  @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
  @Override
  public long getFreeBlocksLong() {
    return statFs.getFreeBlocksLong();
  }

  @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
  @Override
  public long getTotalBytes() {
    return statFs.getTotalBytes();
  }
}
