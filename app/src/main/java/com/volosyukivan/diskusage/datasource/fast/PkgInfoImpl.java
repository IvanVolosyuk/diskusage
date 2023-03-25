package com.volosyukivan.diskusage.datasource.fast;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.volosyukivan.diskusage.datasource.AppInfo;
import com.volosyukivan.diskusage.datasource.PkgInfo;

public class PkgInfoImpl implements PkgInfo {
  private final PackageInfo info;
  private final PackageManager pm;

  public PkgInfoImpl(PackageInfo info, PackageManager pm) {
    this.info = info;
    this.pm = pm;
  }

  @Override
  public String getPackageName() {
    return info.packageName;
  }

  @Override
  public AppInfo getApplicationInfo() {
    return new AppInfoImpl(info.applicationInfo, pm);
  }
}
