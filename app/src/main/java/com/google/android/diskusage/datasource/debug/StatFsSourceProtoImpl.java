package com.google.android.diskusage.datasource.debug;

import android.os.Build;
import android.support.annotation.NonNull;
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
    return proto.getAvailableBlocks();
  }

  private void jellyBeanMR2AndAbove() {
    if (androidVersion < Build.VERSION_CODES.JELLY_BEAN_MR2) {
      throw new NoClassDefFoundError("Unavailable before JELLY_BEAN_MR2");
    }
  }

  @Override
  public long getAvailableBlocksLong() {
    jellyBeanMR2AndAbove();
    return proto.getAvailableBlocksLong();
  }

  @Override
  public long getAvailableBytes() {
    jellyBeanMR2AndAbove();
    return proto.getAvailableBytes();
  }

  @Override
  public int getBlockCount() {
    return proto.getBlockCount();
  }

  @Override
  public long getBlockCountLong() {
    jellyBeanMR2AndAbove();
    return proto.getBlockCountLong();
  }

  @Override
  public int getBlockSize() {
    return proto.getBlockSize();
  }

  @Override
  public long getBlockSizeLong() {
    jellyBeanMR2AndAbove();
    return proto.getBlockSizeLong();
  }

  @Override
  public long getFreeBytes() {
    jellyBeanMR2AndAbove();
    return proto.getFreeBytes();
  }

  @Override
  public int getFreeBlocks() {
    return proto.getFreeBlocks();
  }

  @Override
  public long getFreeBlocksLong() {
    jellyBeanMR2AndAbove();
    return proto.getFreeBlocksLong();
  }

  @Override
  public long getTotalBytes() {
    jellyBeanMR2AndAbove();
    return proto.getTotalBytes();
  }

  @NonNull
  static StatFsProto makeProto(String mountPoint, @NonNull StatFsSource s, int androidVersion) {
    StatFsProto.Builder p = StatFsProto.newBuilder();
    p.setMountPoint(mountPoint)
            .setAvailableBlocks(s.getAvailableBlocks())
            .setBlockSize(s.getBlockSize())
            .setFreeBlocks(s.getFreeBlocks())
            .setBlockCount(s.getBlockCount());
    if (androidVersion >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      p.setAvailableBlocksLong(s.getAvailableBlocksLong())
              .setAvailableBytes(s.getAvailableBytes())
              .setBlockCountLong(s.getBlockCountLong())
              .setBlockSizeLong(s.getBlockSizeLong())
              .setFreeBlocksLong(s.getFreeBlocksLong())
              .setFreeBytes(s.getFreeBytes())
              .setTotalBytes(s.getTotalBytes());
    }
    return p.build();
  }

}
