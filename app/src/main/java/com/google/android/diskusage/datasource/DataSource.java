package com.google.android.diskusage.datasource;

import android.content.Context;
import android.content.pm.PackageManager;
import com.google.android.diskusage.datasource.fast.DefaultDataSource;
import com.google.android.diskusage.ui.LoadableActivity;
import java.lang.reflect.Method;
import java.util.List;

public abstract class DataSource {
  private static DataSource currentDataSource = new DefaultDataSource();

  public static DataSource get() {
    return currentDataSource;
  }

  public static void override(DataSource dataSource) {
    LoadableActivity.resetStoredStates();
    currentDataSource = dataSource;
  }

  public abstract List<PkgInfo> getInstalledPackages(PackageManager pm);

  public abstract StatFsSource statFs(String mountPoint);

  public abstract PortableFile getExternalFilesDir(Context context);

  public abstract PortableFile getExternalStorageDirectory();

  public abstract LegacyFile createLegacyScanFile(String root);

  public abstract void getPackageSizeInfo(
      PkgInfo pkgInfo,
      Method getPackageSizeInfo,
      PackageManager pm,
      AppStatsCallback callback) throws Exception;

  public abstract PortableFile getParentFile(PortableFile file);
}
