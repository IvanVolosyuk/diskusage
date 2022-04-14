package com.google.android.diskusage.datasource.debug;

import android.os.Build;
import androidx.annotation.NonNull;
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
    return proto.getCacheSize();
  }

  @Override
  public long getDataSize() {
    return proto.getDataSize();
  }

  @Override
  public long getCodeSize() {
    return proto.getCodeSize();
  }

  @Override
  public long getExternalCacheSize() {
    return proto.getExternalCacheSize();
  }

  @Override
  public long getExternalCodeSize() {
    return proto.getExternalCodeSize();
  }

  @Override
  public long getExternalDataSize() {
    return proto.getExternalDataSize();
  }

  @Override
  public long getExternalMediaSize() {
    return proto.getExternalMediaSize();
  }

  @Override
  public long getExternalObbSize() {
    return proto.getExternalObbSize();
  }

  @NonNull
  static AppStatsProto makeProto(AppStats appStats, boolean isSucceeded) {
    AppStatsProto.Builder proto = AppStatsProto.newBuilder()
            .setCallbackReceived(true)
            .setSucceeded(isSucceeded);
    if (appStats != null) {
      proto.setHasAppStats(true)
          .setCacheSize(appStats.getCacheSize())
          .setCodeSize(appStats.getCodeSize())
          .setDataSize(appStats.getDataSize())
          .setExternalCodeSize(appStats.getExternalCodeSize())
          .setExternalCacheSize(appStats.getExternalCacheSize())
          .setExternalDataSize(appStats.getExternalDataSize())
          .setExternalMediaSize(appStats.getExternalMediaSize())
          .setExternalObbSize(appStats.getExternalObbSize());
    }
    proto.setCallbackParseDone(true);
    return proto.build();
  }
}
