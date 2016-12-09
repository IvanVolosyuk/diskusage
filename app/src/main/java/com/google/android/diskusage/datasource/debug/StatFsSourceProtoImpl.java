package com.google.android.diskusage.datasource.debug;

import android.os.Build;

import com.google.android.diskusage.datasource.StatFsSource;
import com.google.android.diskusage.proto.StatFsProto;

public class StatFsSourceProtoImpl implements StatFsSource {
  private final StatFsProto proto;
  private final int androidVersion;

  StatFsSourceProtoImpl(StatFsProto proto, int androidVersion) {
    this.proto = proto;
    this.androidVersion = androidVersion;
  }

  @Override
  public int getAvailableBlocks() {
    return proto.availableBlocks;
  }

  private void jellyBeanMR2AndAbove() {
    if (androidVersion < Build.VERSION_CODES.JELLY_BEAN_MR2) {
      throw new NoClassDefFoundError("Unavailable before JELLY_BEAN_MR2");
    }
  }

  @Override
  public long getAvailableBlocksLong() {
    jellyBeanMR2AndAbove();
    return proto.availableBlocksLong;
  }

  @Override
  public long getAvailableBytes() {
    jellyBeanMR2AndAbove();
    return proto.availableBytes;
  }

  @Override
  public int getBlockCount() {
    return proto.blockCount;
  }

  @Override
  public long getBlockCountLong() {
    jellyBeanMR2AndAbove();
    return proto.blockCountLong;
  }

  @Override
  public int getBlockSize() {
    return proto.blockSize;
  }

  @Override
  public long getBlockSizeLong() {
    jellyBeanMR2AndAbove();
    return proto.blockSizeLong;
  }

  @Override
  public long getFreeBytes() {
    jellyBeanMR2AndAbove();
    return proto.freeBytes;
  }

  @Override
  public int getFreeBlocks() {
    return proto.freeBlocks;
  }

  @Override
  public long getFreeBlocksLong() {
    jellyBeanMR2AndAbove();
    return proto.freeBlocksLong;
  }

  @Override
  public long getTotalBytes() {
    jellyBeanMR2AndAbove();
    return proto.totalBytes;
  }

  static StatFsProto makeProto(String mountPoint, StatFsSource s, int androidVersion) {
    StatFsProto p = new StatFsProto();
    p.mountPoint = mountPoint;
    p.availableBlocks = s.getAvailableBlocks();
    p.blockSize = s.getBlockSize();
    p.freeBlocks = s.getFreeBlocks();
    p.blockCount = s.getBlockCount();
    if (androidVersion >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      p.availableBlocksLong = s.getAvailableBlocksLong();
      p.availableBytes = s.getAvailableBytes();
      p.blockCountLong = s.getBlockCountLong();
      p.blockSizeLong = s.getBlockSizeLong();
      p.freeBlocksLong = s.getFreeBlocksLong();
      p.freeBytes = s.getFreeBytes();
      p.totalBytes = s.getTotalBytes();
    }
    return p;
  }

}
