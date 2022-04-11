package com.google.android.diskusage.datasource.debug;

import android.os.Build;

import com.google.android.diskusage.datasource.AppInfo;
import com.google.android.diskusage.datasource.PkgInfo;
import com.google.android.diskusage.proto.AppInfoProto;

import java.util.Arrays;

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
    return proto.getFlags();
  }

  @Override
  public String getDataDir() {
    return load(proto.getDataDir());
  }

  @Override
  public boolean isEnabled() {
    return proto.getIsEnable();
  }

  @Override
  public String getName() {
    return load(proto.getName());
  }

  @Override
  public String getPackageName() {
    return load(proto.getPackageName());
  }

  @Override
  public String getPublicSourceDir() {
    return load(proto.getPublicSourceDir());
  }

  @Override
  public String getSourceDir() {
    return load(proto.getSourceDir());
  }

  @Override
  public String[] getSplitSourceDirs() {
    if (androidVersion < Build.VERSION_CODES.LOLLIPOP) {
      throw new NoClassDefFoundError("Not available pre-L/Android-21");
    }
    return proto.getSplitSourceDirsList().toArray(new String[0]);
  }

  @Override
  public String getApplicationLabel() {
    return load(proto.getApplicationLabel());
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
    AppInfo appInfo = pkgInfo.getApplicationInfo();
    AppInfoProto.Builder proto = AppInfoProto.newBuilder()
            .setPackageName(save(pkgInfo.getPackageName()))
            .setApplicationLabel(save(appInfo.getApplicationLabel()))
            .setDataDir(save(appInfo.getDataDir()))
            .setFlags(appInfo.getFlags())
            .setIsEnable(appInfo.isEnabled())
            .setName(save(appInfo.getName()))
            .setPublicSourceDir(save(appInfo.getPublicSourceDir()))
            .setSourceDir(save(appInfo.getSourceDir()));
    if (androidVersion >= Build.VERSION_CODES.LOLLIPOP) {
      proto.addAllSplitSourceDirs(Arrays.asList(appInfo.getSplitSourceDirs()));
    }
    return proto.build();
  }
}
