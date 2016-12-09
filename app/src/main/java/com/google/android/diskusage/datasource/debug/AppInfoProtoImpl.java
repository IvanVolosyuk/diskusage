package com.google.android.diskusage.datasource.debug;

import android.os.Build;

import com.google.android.diskusage.datasource.AppInfo;
import com.google.android.diskusage.datasource.PkgInfo;
import com.google.android.diskusage.proto.AppInfoProto;

public class AppInfoProtoImpl implements AppInfo, PkgInfo {
  private static final String NULL = "##NULL##";
  final AppInfoProto proto;
  private final int androidVersion;

  public AppInfoProtoImpl(AppInfoProto proto, int androidVersion) {
    this.androidVersion = androidVersion;
    this.proto = Precondition.checkNotNull(proto);
  }

  @Override
  public int getFlags() {
    return proto.flags;
  }

  @Override
  public String getDataDir() {
    return load(proto.dataDir);
  }

  @Override
  public boolean isEnabled() {
    return proto.isEnable;
  }

  @Override
  public String getName() {
    return load(proto.name);
  }

  @Override
  public String getPackageName() {
    return load(proto.packageName);
  }

  @Override
  public String getPublicSourceDir() {
    return load(proto.publicSourceDir);
  }

  @Override
  public String getSourceDir() {
    return load(proto.sourceDir);
  }

  @Override
  public String[] getSplitSourceDirs() {
    if (androidVersion < Build.VERSION_CODES.LOLLIPOP) {
      throw new NoClassDefFoundError("Not available pre-L/Android-21");
    }
    return proto.splitSourceDirs;
  }

  @Override
  public String getApplicationLabel() {
    return load(proto.applicationLabel);
  }

  @Override
  public AppInfo getApplicationInfo() {
    return this;
  }

  private static String save(String a) {
    if (a == null) {
      a = NULL;
    }
    return a;
  }

  private static String load(String a) {
    if (a.equals(NULL)) {
      a = null;
    }
    return a;
  }

  static AppInfoProto makeProto(PkgInfo pkgInfo, int androidVersion) {
    AppInfoProto proto = new AppInfoProto();
    proto.packageName = save(pkgInfo.getPackageName());
    AppInfo appInfo = pkgInfo.getApplicationInfo();
    proto.applicationLabel = save(appInfo.getApplicationLabel());
    proto.dataDir = save(appInfo.getDataDir());
    proto.flags = appInfo.getFlags();
    proto.isEnable = appInfo.isEnabled();
    proto.name = save(appInfo.getName());
    proto.publicSourceDir = save(appInfo.getPublicSourceDir());
    proto.sourceDir = save(appInfo.getSourceDir());
    if (androidVersion >= Build.VERSION_CODES.LOLLIPOP) {
      proto.splitSourceDirs = appInfo.getSplitSourceDirs();
    }
    return proto;
  }
}
