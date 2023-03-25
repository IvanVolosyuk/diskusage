package com.volosyukivan.diskusage.datasource;

public interface StatFsSource {

  @Deprecated
  int getAvailableBlocks();

  public long getAvailableBlocksLong();

  public long getAvailableBytes();

  @Deprecated
  public int getBlockCount();

  public long getBlockCountLong();

  @Deprecated
  public int getBlockSize();

  public long getBlockSizeLong();

  public long getFreeBytes();

  @Deprecated
  public int getFreeBlocks();

  public long getFreeBlocksLong();

  public long getTotalBytes();
}