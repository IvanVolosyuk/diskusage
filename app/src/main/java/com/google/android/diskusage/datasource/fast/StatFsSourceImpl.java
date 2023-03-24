package com.google.android.diskusage.datasource.fast;

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

  @Override
  public long getAvailableBlocksLong() {
    return statFs.getAvailableBlocksLong();
  }

  @Override
  public long getAvailableBytes() {
    return statFs.getAvailableBytes();
  }

  @Deprecated
  @Override
  public int getBlockCount() {
    return statFs.getBlockCount();
  }

  @Override
  public long getBlockCountLong() {
    return statFs.getBlockCountLong();
  }

  @Deprecated
  @Override
  public int getBlockSize() {
    return statFs.getBlockSize();
  }

  @Override
  public long getBlockSizeLong() {
    return statFs.getBlockSizeLong();
  }

  @Override
  public long getFreeBytes() {
    return statFs.getFreeBytes();
  }

  @Deprecated
  @Override
  public int getFreeBlocks() {
    return statFs.getFreeBlocks();
  }

  @Override
  public long getFreeBlocksLong() {
    return statFs.getFreeBlocksLong();
  }

  @Override
  public long getTotalBytes() {
    return statFs.getTotalBytes();
  }
}
