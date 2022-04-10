package com.google.android.diskusage.datasource.debug;

import android.os.Build;
import android.support.annotation.NonNull;
import com.google.android.diskusage.datasource.AppStats;
import com.google.android.diskusage.proto.AppStatsProto;

class AppStatsProtoImpl implements AppStats {
  private final AppStatsProto proto;
  private final int androidVersion;

  AppStatsProtoImpl(AppStatsProto proto, int androidVersion) {
    this.proto = proto;
    this.androidVersion = androidVersion;
  }

  private void versionCheck(int api) {
    if (androidVersion < api) {
      throw new NoSuchFieldError("Not available pre " + api);
    }
  }

  @Override
  public long getCacheSize() {
    return proto.cacheSize;
  }

  @Override
  public long getDataSize() {
    return proto.dataSize;
  }

  @Override
  public long getCodeSize() {
    return proto.codeSize;
  }

  @Override
  public long getExternalCacheSize() {
    versionCheck(Build.VERSION_CODES.HONEYCOMB);
    return proto.externalCacheSize;
  }

  @Override
  public long getExternalCodeSize() {
    versionCheck(Build.VERSION_CODES.ICE_CREAM_SANDWICH);
    return proto.externalCodeSize;
  }

  @Override
  public long getExternalDataSize() {
    versionCheck(Build.VERSION_CODES.HONEYCOMB);
    return proto.externalDataSize;
  }

  @Override
  public long getExternalMediaSize() {
    versionCheck(Build.VERSION_CODES.HONEYCOMB);
    return proto.externalMediaSize;
  }

  @Override
  public long getExternalObbSize() {
    versionCheck(Build.VERSION_CODES.HONEYCOMB);
    return proto.externalObbSize;
  }

  @NonNull
  static AppStatsProto makeProto(
      AppStats appStats, boolean isSucceeded, int androidVersion) {
    AppStatsProto proto = new AppStatsProto();
    proto.callbackReceived = true;
    proto.succeeded = isSucceeded;
    if (appStats != null) {
      proto.hasAppStats = true;
      proto.cacheSize = appStats.getCacheSize();
      proto.codeSize = appStats.getCodeSize();
      proto.dataSize = appStats.getDataSize();
      if (androidVersion >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
        proto.externalCodeSize = appStats.getExternalCodeSize();
      }
      if (androidVersion >= Build.VERSION_CODES.HONEYCOMB) {
        proto.externalCacheSize = appStats.getExternalCacheSize();
        proto.externalDataSize = appStats.getExternalDataSize();
        proto.externalMediaSize = appStats.getExternalMediaSize();
        proto.externalObbSize = appStats.getExternalObbSize();
      }
    }
    proto.callbackParseDone = true;
    return proto;
  }
}
