package com.google.android.diskusage.datasource.fast;

import android.annotation.TargetApi;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import com.google.android.diskusage.datasource.AppInfo;

public class AppInfoImpl implements AppInfo {
  private final ApplicationInfo app;
  private final PackageManager pm;

  AppInfoImpl(ApplicationInfo app, PackageManager pm) {
    this.app = app;
    this.pm = pm;
  }

  @Override
  public int getFlags() {
    return app.flags;
  }

  @Override
  public String getDataDir() {
    return app.dataDir;
  }

  @Override
  public boolean isEnabled() {
    return app.enabled;
  }

  @Override
  public String getName() {
    return app.name;
  }

  @Override
  public String getPackageName() {
    return app.packageName;
  }

  @Override
  public String getPublicSourceDir() {
    return app.publicSourceDir;
  }

  @Override
  public String getSourceDir() {
    return app.sourceDir;
  }

  @Override
  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  public String[] getSplitSourceDirs() {
    return app.splitSourceDirs;
  }

  @Override
  public String getApplicationLabel() {
    return pm.getApplicationLabel(app).toString();
  }
}