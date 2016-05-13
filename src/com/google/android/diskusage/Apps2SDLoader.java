/**
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

package com.google.android.diskusage;

import java.io.BufferedReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import android.content.pm.IPackageStatsObserver;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.util.Log;

import com.google.android.diskusage.datasource.AppInfo;
import com.google.android.diskusage.datasource.AppStats;
import com.google.android.diskusage.datasource.AppStatsCallback;
import com.google.android.diskusage.datasource.DataSource;
import com.google.android.diskusage.datasource.PkgInfo;
import com.google.android.diskusage.datasource.StatFsSource;
import com.google.android.diskusage.entity.FileSystemEntry;
import com.google.android.diskusage.entity.FileSystemPackage;

public class Apps2SDLoader {

  private final DiskUsage diskUsage;
  private int numLookups;
  private int numLoadedPackages;

  public Apps2SDLoader(DiskUsage activity) {
    this.diskUsage = activity;
  }

  private synchronized void changeNumLookups(int change) {
    numLookups += change;
  }

  private Map<String, Long> getDfSizes() {
    TreeMap<String, Long> map = new TreeMap<String, Long>();
    try {
      // FIXME: debug
      BufferedReader reader = new BufferedReader(DataSource.get().getProc());
      String line;
      while ((line = reader.readLine()) != null) {
        String[] parts = line.split(" +");
        if (parts.length < 3) continue;
        String mountPoint = parts[1];
        String fsType = parts[2];
        if (fsType.equals("tmpfs")) {
          continue;
        }
        if (!mountPoint.startsWith("/mnt/asec/")) {
          continue;
        }
        String packageNameNum = mountPoint.substring(mountPoint.lastIndexOf('/') + 1);
        String packageName = packageNameNum.substring(0, packageNameNum.indexOf('-'));
        StatFsSource stat = DataSource.get().statFs(mountPoint);
        long size = (stat.getBlockCount() - stat.getAvailableBlocks()) * stat.getBlockSize();
        map.put(packageName, size);
        Log.d("diskusage", "external size (" + packageName + ") = " + size / 1024 + " kb");
      }
      reader.close();
    } catch (Throwable t) {
      Log.e("disksusage", "failed to parse /proc/mounts", t);
    }
    return map;
  }

  CharSequence currentAppName = "";
  boolean switchToSecondary = true;

  public FileSystemEntry[] load(
      boolean sdOnly,
      final AppFilter appFilter,
      final int blockSize) throws Throwable {
    final Map<String, Long> dfSizes = getDfSizes();
    final ArrayList<FileSystemEntry> entries = new ArrayList<FileSystemEntry>();

    PackageManager pm = diskUsage.getPackageManager();
    Method getPackageSizeInfo = pm.getClass().getMethod(
        "getPackageSizeInfo", String.class, IPackageStatsObserver.class);
    final List<PkgInfo> installedPackages = DataSource.get().getInstalledPackages(pm);

    final Handler handler = diskUsage.handler;
    Runnable progressUpdater = new Runnable() {
      @Override
      public void run() {
        MyProgressDialog dialog = diskUsage.getPersistantState().loading;
        if (dialog != null) {
          if (switchToSecondary) {
            dialog.switchToSecondary();
            switchToSecondary = false;
          }
          dialog.setMax(installedPackages.size());
          dialog.setProgress(numLoadedPackages, currentAppName);
        }
        diskUsage.handler.postDelayed(this, 50);
      }
    };
    handler.post(progressUpdater);


    for (final PkgInfo info : installedPackages) {
//      Log.d("diskusage", "Got package: " + info.packageName);
      if (info.getApplicationInfo() == null) {
        Log.d("diskusage", "No applicationInfo");
        continue;
      }

      int flag = 0x40000; // ApplicationInfo.FLAG_EXTERNAL_STORAGE
      final AppInfo appInfo = info.getApplicationInfo();
      boolean on_sdcard = (appInfo.getFlags() & flag) != 0;
      if (on_sdcard || !sdOnly) {
        changeNumLookups(1);
        final String pkg = info.getPackageName();
        final String name = appInfo.getApplicationLabel();
        currentAppName = name;
        DataSource.get().getPackageSizeInfo(getPackageSizeInfo, pm, pkg, new AppStatsCallback() {
          @Override
          public void onGetStatsCompleted(AppStats stats, boolean succeeded) {
            synchronized (Apps2SDLoader.this) {
              numLoadedPackages++;
              changeNumLookups(-1);
              if (succeeded) {
                FileSystemPackage p = new FileSystemPackage(
                    name,
                    pkg,
                    stats,
                    appInfo.getFlags(),
                    dfSizes.get(pkg),
                    blockSize);
                p.applyFilter(appFilter, blockSize);
                entries.add(p);
//                Log.i("diskusage", "codeSize: " + pStats.codeSize);
              }
              Apps2SDLoader.this.notify();
            }

          }
        });
      } else {
        synchronized (this) {
          currentAppName = appInfo.getApplicationLabel();
          numLoadedPackages++;
        }
      }
    }
    while (true) {
      synchronized (this) {
        if (numLookups != 0) {
          wait();
        } else {
          break;
        }
      }
    }
    if (entries.size() == 0) return null;
    FileSystemEntry[] result = entries.toArray(new FileSystemEntry[] {});
    Arrays.sort(result, FileSystemEntry.COMPARE);
    handler.removeCallbacks(progressUpdater);
    return result;
  }
}
