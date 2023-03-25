package com.volosyukivan.diskusage.datasource.fast;

import android.content.pm.PackageStats;
import com.volosyukivan.diskusage.datasource.AppStats;

class AppStatsImpl implements AppStats {
  private final PackageStats packageStats;

  public AppStatsImpl(PackageStats packageStats) {
    this.packageStats = packageStats;
  }

  @Override
  public long getCacheSize() {
    return packageStats.cacheSize;
  }

  @Override
  public long getDataSize() {
    return packageStats.dataSize;
  }

  @Override
  public long getCodeSize() {
    return packageStats.codeSize;
  }

  @Override
  public long getExternalCacheSize() {
    return packageStats.externalCacheSize;
  }

  @Override
  public long getExternalCodeSize() {
    return packageStats.externalCodeSize;
  }

  @Override
  public long getExternalDataSize() {
    return packageStats.externalDataSize;
  }

  @Override
  public long getExternalMediaSize() {
    return packageStats.externalMediaSize;
  }

  @Override
  public long getExternalObbSize() {
    return packageStats.externalObbSize;
  }
}
