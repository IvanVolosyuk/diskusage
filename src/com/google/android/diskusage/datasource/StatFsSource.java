package com.google.android.diskusage.datasource;

public interface StatFsSource {
  int getAvailableBlocks();
  long getAvailableBlocksLong();
  long getAvailableBytes();
  int getBlockCount();
  long getBlockCountLong();
  int getBlockSize();
  long getBlockSizeLong();
  long getFreeBytes();
  int getFreeBlocks();
  long getFreeBlocksLong();
  long getTotalBytes();
}
