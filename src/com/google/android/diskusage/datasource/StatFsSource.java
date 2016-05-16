package com.google.android.diskusage.datasource;

import android.annotation.TargetApi;
import android.os.Build;

public interface StatFsSource {

  @Deprecated
  int getAvailableBlocks();

  @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
  public long getAvailableBlocksLong();

  @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
  public long getAvailableBytes();

  @Deprecated
  public int getBlockCount();

  @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
  public long getBlockCountLong();

  @Deprecated
  public int getBlockSize();

  @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
  public long getBlockSizeLong();

  @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
  public long getFreeBytes();

  @Deprecated
  public int getFreeBlocks();

  @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
  public long getFreeBlocksLong();

  @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
  public long getTotalBytes();
}