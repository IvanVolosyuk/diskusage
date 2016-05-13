package com.google.android.diskusage.datasource;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Method;
import java.util.List;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import com.google.android.diskusage.datasource.fast.DefaultDataSource;

public abstract class DataSource {
  private static DataSource currentDataSource = new DefaultDataSource();

  public static DataSource get() {
    return currentDataSource;
  }

  public abstract int getAndroidVersion();

  public abstract void getPackageSizeInfo(
      Method getPackageSizeInfo,
      PackageManager pm,
      String pkg,
      AppStatsCallback callback) throws Exception;

  public abstract List<PkgInfo> getInstalledPackages(PackageManager pm);

  public abstract StatFsSource statFs(String mountPoint);

  public abstract Env getEnvironment();

  @TargetApi(Build.VERSION_CODES.FROYO)
  public abstract File getExternalFilesDir(Context context);
  @TargetApi(Build.VERSION_CODES.KITKAT)
  public abstract File[] getExternalFilesDirs(Context context);

  public abstract InputStream createNativeScanner(
      Context context, String path,
      boolean rootRequired) throws IOException, InterruptedException;

  public abstract boolean isDeviceRooted();

  public abstract LegacyFile createLegacyScanFile(String root);

  public abstract FileReader getProc() throws IOException;
}
