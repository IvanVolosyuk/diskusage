package com.google.android.diskusage.datasource.debug;

import androidx.annotation.NonNull;
import com.google.android.diskusage.datasource.StatFsSource;
import com.google.android.diskusage.proto.StatFsProto;

public class StatFsSourceProtoImpl implements StatFsSource {
  private final StatFsProto proto;

  StatFsSourceProtoImpl(StatFsProto proto) {
    this.proto = proto;
  }

  @Override
  public int getAvailableBlocks() {
    return proto.getAvailableBlocks();
  }

  @Override
  public long getAvailableBlocksLong() {
    return proto.getAvailableBlocksLong();
  }

  @Override
  public long getAvailableBytes() {
    return proto.getAvailableBytes();
  }

  @Override
  public int getBlockCount() {
    return proto.getBlockCount();
  }

  @Override
  public long getBlockCountLong() {
    return proto.getBlockCountLong();
  }

  @Override
  public int getBlockSize() {
    return proto.getBlockSize();
  }

  @Override
  public long getBlockSizeLong() {
    return proto.getBlockSizeLong();
  }

  @Override
  public long getFreeBytes() {
    return proto.getFreeBytes();
  }

  @Override
  public int getFreeBlocks() {
    return proto.getFreeBlocks();
  }

  @Override
  public long getFreeBlocksLong() {
    return proto.getFreeBlocksLong();
  }

  @Override
  public long getTotalBytes() {
    return proto.getTotalBytes();
  }

  @NonNull
  static StatFsProto makeProto(String mountPoint, @NonNull StatFsSource s) {
    StatFsProto.Builder p = StatFsProto.newBuilder();
    p.setMountPoint(mountPoint)
            .setAvailableBlocksLong(s.getAvailableBlocksLong())
            .setAvailableBytes(s.getAvailableBytes())
            .setBlockCountLong(s.getBlockCountLong())
            .setBlockSizeLong(s.getBlockSizeLong())
            .setFreeBlocksLong(s.getFreeBlocksLong())
            .setFreeBytes(s.getFreeBytes())
            .setTotalBytes(s.getTotalBytes());
    return p.build();
  }

}
