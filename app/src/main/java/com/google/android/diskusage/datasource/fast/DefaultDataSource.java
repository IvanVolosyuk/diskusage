package com.google.android.diskusage.datasource.fast;

import com.google.android.diskusage.datasource.AppStatsCallback;
import com.google.android.diskusage.datasource.DataSource;
import com.google.android.diskusage.datasource.LegacyFile;
import com.google.android.diskusage.datasource.PkgInfo;
import com.google.android.diskusage.datasource.PortableFile;
import com.google.android.diskusage.datasource.StatFsSource;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageStats;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class DefaultDataSource extends DataSource {

  @Override
  public InputStream getProc() throws IOException {
    return new FileInputStream(new File("/proc/mounts"));
  }

  @Override
  public int getAndroidVersion() {
    return Integer.parseInt(Build.VERSION.SDK);
  }

  @Override
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

  @TargetApi(Build.VERSION_CODES.FROYO)
  @Override
  public PortableFile getExternalFilesDir(Context context) {
    return PortableFileImpl.make(context.getExternalFilesDir(null));
  }

  @TargetApi(Build.VERSION_CODES.KITKAT)
  @Override
  public PortableFile[] getExternalFilesDirs(Context context) {
    File[] externalFilesDirs = context.getExternalFilesDirs(null);
    PortableFile[] result = new PortableFileImpl[externalFilesDirs.length];
    for (int i = 0; i < externalFilesDirs.length; i++) {
      result[i] = PortableFileImpl.make(externalFilesDirs[i]);
    }
    return result;
  }

  @Override
  public InputStream createNativeScanner(
      Context context, String path, boolean rootRequired)
          throws IOException, InterruptedException {
    return new NativeScannerStream.Factory(context).create(path, rootRequired);
  }

  @Override
  public boolean isDeviceRooted() {
    String pathEnv = System.getenv("PATH");
    if (pathEnv != null) {
      String[] searchPaths = pathEnv.split(":");
      for (String path : searchPaths) {
        if (path.length() == 0) {
          continue;
        }
        String suPath = path + "/su";
        File suFile = new File(suPath);
        if (suFile.exists() && !suFile.isDirectory()) {
          return true;
        }
      }
    }

    return new File("/system/bin/su").isFile()
        || new File("/system/xbin/su").isFile();
  }

  @Override
  public LegacyFile createLegacyScanFile(String root) {
    return LegacyFileImpl.createRoot(root);
  }

  @Override
  public void getPackageSizeInfo(
      PkgInfo pkgInfo,
      Method getPackageSizeInfo,
      PackageManager pm,
      final AppStatsCallback callback) throws Exception {
    getPackageSizeInfo.invoke(
        pm,
        pkgInfo.getPackageName(),
        new IPackageStatsObserver.Stub() {
          @Override
          public void onGetStatsCompleted(
              PackageStats pStats, boolean succeeded) {
            callback.onGetStatsCompleted(
                pStats != null ? new AppStatsImpl(pStats) : null, succeeded);
          }
        });
  }

  @Override
  public PortableFile getExternalStorageDirectory() {
    return PortableFileImpl.make(Environment.getExternalStorageDirectory());
  }

  @Override
  public PortableFile getParentFile(PortableFile file) {
    return PortableFileImpl.make(new File(file.getAbsolutePath()).getParentFile());
  }
}
