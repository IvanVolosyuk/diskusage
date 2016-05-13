package com.google.android.diskusage.datasource.fast;

import android.annotation.TargetApi;
import android.content.pm.PackageStats;
import android.os.Build;

import com.google.android.diskusage.datasource.AppStats;

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
  };

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  @Override
  public long getExternalCacheSize() {
    return packageStats.externalCacheSize;
  }

  @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
  @Override
  public long getExternalCodeSize() {
    return packageStats.externalCodeSize;
  };

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  @Override
  public long getExternalDataSize() {
    return packageStats.externalDataSize;
  };

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  @Override
  public long getExternalMediaSize() {
    return packageStats.externalMediaSize;
  };

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  @Override
  public long getExternalObbSize() {
    return packageStats.externalObbSize;
  };

  @Override
  public String getPackageName() {
    return packageStats.packageName;
  };
}