/*
 * DiskUsage - displays sdcard usage on android.
 * Copyright (C) 2008-2011 Ivan Volosyuk
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package com.google.android.diskusage.filesystem;

import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.storage.StorageManager;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import com.google.android.diskusage.filesystem.entity.FileSystemEntry;
import com.google.android.diskusage.filesystem.entity.FileSystemPackage;
import com.google.android.diskusage.ui.DiskUsage;
import com.google.android.diskusage.ui.common.ScanProgressDialog;
import timber.log.Timber;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Collects the storage footprint of every installed application.
 *
 * <p>Historically this used {@code UsageStatsManager.queryUsageStats()} which
 * only returns applications the user interacted with recently. On modern
 * Android that made most applications disappear from the graph. The package
 * manager is the correct source; {@link StorageStatsManager} (API 26+) then
 * provides accurate code/data/cache sizes. If the usage-access app-op has not
 * been granted, the loader degrades gracefully to the APK size on disk instead
 * of dropping the application entirely.
 */
public class Apps2SDLoader {

  private final DiskUsage diskUsage;
  private CharSequence lastAppName = "";
  private boolean switchToSecondary = true;
  private int numLoadedPackages = 0;

  public Apps2SDLoader(DiskUsage activity) {
    this.diskUsage = activity;
  }

  public FileSystemEntry[] load(final long blockSize) throws Throwable {
    final PackageManager packageManager = diskUsage.getApplicationContext().getPackageManager();
    final List<PackageInfo> packages;
    try {
      packages = packageManager.getInstalledPackages(0);
    } catch (Throwable t) {
      Timber.e(t, "load: cannot list installed packages");
      return new FileSystemEntry[0];
    }

    final Handler handler = diskUsage.handler;
    final int total = packages.size();
    Runnable progressUpdater = new Runnable() {
      @Override
      public void run() {
        ScanProgressDialog dialog = diskUsage.getPersistantState().loading;
        if (dialog != null) {
          if (switchToSecondary) {
            dialog.switchToSecondary();
            switchToSecondary = false;
          }
          dialog.setMax(total);
          final CharSequence appName;
          synchronized (Apps2SDLoader.this) {
            appName = lastAppName;
          }
          dialog.setProgress(numLoadedPackages, appName);
        }
        handler.postDelayed(this, 50);
      }
    };
    handler.post(progressUpdater);

    final ArrayList<FileSystemEntry> entries = new ArrayList<>(total);
    boolean statsAccessDenied = false;

    try {
      for (PackageInfo info : packages) {
        ApplicationInfo metadata = info.applicationInfo;
        if (metadata == null) continue;
        // Do not try to report on apps that are hidden from the launcher and
        // have no data at all (instant apps, stubs, ...).
        String pkg = info.packageName;
        if (pkg == null) continue;

        final String appName;
        try {
          appName = metadata.loadLabel(packageManager).toString();
        } catch (Throwable t) {
          continue;
        }
        synchronized (this) {
          lastAppName = appName;
        }

        long apkBytes = apkSizeOf(metadata);
        long dataBytes = 0;
        long cacheBytes = 0;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !statsAccessDenied) {
          try {
            long[] stats = queryStorageStats(pkg, apkBytes);
            apkBytes = stats[0];
            dataBytes = stats[1];
            cacheBytes = stats[2];
            if (numLoadedPackages == 0) {
              Timber.d("load: storage stats available, first app %s -> apk=%s data=%s cache=%s",
                  pkg, apkBytes, dataBytes, cacheBytes);
            }
          } catch (SecurityException e) {
            // Usage access not granted: keep listing apps, only sizes degrade.
            statsAccessDenied = true;
            Timber.w("load: no usage access, falling back to APK sizes");
          } catch (Throwable t) {
            Timber.d(t, "load: no storage stats for %s", pkg);
          }
        }

        if (apkBytes + dataBytes + cacheBytes <= 0) {
          numLoadedPackages++;
          continue;
        }

        FileSystemPackage p = new FileSystemPackage(
            appName, pkg, apkBytes, dataBytes, cacheBytes, metadata.flags);
        p.applyFilter(blockSize);
        entries.add(p);
        numLoadedPackages++;
      }
    } finally {
      handler.removeCallbacks(progressUpdater);
    }

    FileSystemEntry[] result = entries.toArray(new FileSystemEntry[0]);
    Arrays.sort(result, FileSystemEntry.COMPARE);
    return result;
  }

  /** @return {apkBytes, dataBytes, cacheBytes} */
  @RequiresApi(Build.VERSION_CODES.O)
  private long[] queryStorageStats(String pkg, long apkBytes) throws Exception {
    StorageStatsManager manager =
        (StorageStatsManager) diskUsage.getSystemService(Context.STORAGE_STATS_SERVICE);
    if (manager == null) return new long[] {apkBytes, 0, 0};
    StorageStats stats = manager.queryStatsForPackage(
        StorageManager.UUID_DEFAULT, pkg, android.os.Process.myUserHandle());
    return new long[] {
        Math.max(apkBytes, stats.getAppBytes()),
        stats.getDataBytes(),
        stats.getCacheBytes()
    };
  }

  private static long apkSizeOf(@NonNull ApplicationInfo info) {
    long size = 0;
    if (info.sourceDir != null) size += new File(info.sourceDir).length();
    if (info.splitSourceDirs != null) {
      for (String split : info.splitSourceDirs) {
        if (split != null) size += new File(split).length();
      }
    }
    return Math.max(size, 0);
  }
}
