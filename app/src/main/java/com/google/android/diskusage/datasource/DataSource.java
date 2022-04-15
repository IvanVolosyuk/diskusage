package com.google.android.diskusage.datasource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.List;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import com.google.android.diskusage.ui.LoadableActivity;
import com.google.android.diskusage.datasource.fast.DefaultDataSource;

public abstract class DataSource {
  private static DataSource currentDataSource = new DefaultDataSource();

  public static DataSource get() {
    return currentDataSource;
  }

  public static void override(DataSource dataSource) {
    LoadableActivity.resetStoredStates();
    currentDataSource = dataSource;
  }

  public abstract int getAndroidVersion();

  public abstract List<PkgInfo> getInstalledPackages(PackageManager pm);

  public abstract StatFsSource statFs(String mountPoint);

  @TargetApi(Build.VERSION_CODES.FROYO)
  public abstract PortableFile getExternalFilesDir(Context context);
  @TargetApi(Build.VERSION_CODES.KITKAT)
  public abstract PortableFile[] getExternalFilesDirs(Context context);

  public abstract PortableFile getExternalStorageDirectory();

  public abstract InputStream createNativeScanner(
      Context context, String path,
      boolean rootRequired) throws IOException, InterruptedException;

  public abstract boolean isDeviceRooted();

  public abstract LegacyFile createLegacyScanFile(String root);

  public final BufferedReader getProcReader() throws IOException {
    return new BufferedReader(new InputStreamReader(getProc()));
  }

  public abstract InputStream getProc() throws IOException;

  public abstract void getPackageSizeInfo(
      PkgInfo pkgInfo,
      Method getPackageSizeInfo,
      PackageManager pm,
      AppStatsCallback callback) throws Exception;

  public abstract PortableFile getParentFile(PortableFile file);
}
