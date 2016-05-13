package com.google.android.diskusage.datasource.fast;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageStats;
import android.os.Build;

import com.google.android.diskusage.datasource.AppStatsCallback;
import com.google.android.diskusage.datasource.DataSource;
import com.google.android.diskusage.datasource.Env;
import com.google.android.diskusage.datasource.LegacyFile;
import com.google.android.diskusage.datasource.PkgInfo;
import com.google.android.diskusage.datasource.StatFsSource;

public class DefaultDataSource extends DataSource {
  private final Env env = new EnvImpl();

  @Override
  public FileReader getProc() throws IOException {
    return new FileReader("/proc/mounts");
  }

  @Override
  public int getAndroidVersion() {
    return Integer.parseInt(Build.VERSION.SDK);
  }

  @Override
  public void getPackageSizeInfo(
      Method getPackageSizeInfo,
      PackageManager pm,
      String pkg,
      final AppStatsCallback callback) throws Exception {
    getPackageSizeInfo.invoke(pm, pkg, new IPackageStatsObserver.Stub() {

      @Override
      public void onGetStatsCompleted(PackageStats pStats, boolean succeeded) {
        callback.onGetStatsCompleted(
            pStats != null ? new AppStatsImpl(pStats) : null, succeeded);
      }
    });
  }

  public List<PkgInfo> getInstalledPackages(PackageManager pm) {
    final List<PackageInfo> installedPackages = pm.getInstalledPackages(
        PackageManager.GET_META_DATA | PackageManager.GET_UNINSTALLED_PACKAGES);
    List<PkgInfo> packageInfos = new ArrayList<PkgInfo>();
    for (PackageInfo info : installedPackages) {
      packageInfos.add(new PkgInfoImpl(info, pm));
    }
    return packageInfos;
  }

  @Override
  public StatFsSource statFs(String mountPoint) {
    return new StatFsSourceImpl(mountPoint);
  }

  @Override
  public Env getEnvironment() {
    return env;
  }

  @TargetApi(Build.VERSION_CODES.FROYO)
  @Override
  public File getExternalFilesDir(Context context) {
    return context.getExternalFilesDir(null);
  }

  @TargetApi(Build.VERSION_CODES.KITKAT)
  @Override
  public File[] getExternalFilesDirs(Context context) {
    return context.getExternalFilesDirs(null);
  }

  @Override
  public InputStream createNativeScanner(
      Context context, String path, boolean rootRequired)
          throws IOException, InterruptedException {
    return new NativeScannerStream.Factory(context).create(path, rootRequired);
  }

  @Override
  public boolean isDeviceRooted() {
      return new File("/system/bin/su").isFile()
          || new File("/system/xbin/su").isFile();
  }

  @Override
  public LegacyFile createLegacyScanFile(String root) {
    return LegacyFileImpl.createRoot(root);
  }
}
