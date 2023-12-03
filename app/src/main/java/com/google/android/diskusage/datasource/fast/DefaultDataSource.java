package com.google.android.diskusage.datasource.fast;

import android.content.Context;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageStats;
import android.os.Environment;
import androidx.annotation.NonNull;
import com.google.android.diskusage.datasource.AppStatsCallback;
import com.google.android.diskusage.datasource.DataSource;
import com.google.android.diskusage.datasource.LegacyFile;
import com.google.android.diskusage.datasource.PkgInfo;
import com.google.android.diskusage.datasource.PortableFile;
import com.google.android.diskusage.datasource.StatFsSource;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class DefaultDataSource extends DataSource {

  @Override
  public List<PkgInfo> getInstalledPackages(@NonNull PackageManager pm) {
    final List<PackageInfo> installedPackages = pm.getInstalledPackages(
        PackageManager.GET_META_DATA | PackageManager.GET_UNINSTALLED_PACKAGES);
    List<PkgInfo> packageInfos = new ArrayList<>();
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
  public PortableFile getExternalFilesDir(Context context) {
    return PortableFileImpl.make(context.getExternalFilesDir(null));
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
