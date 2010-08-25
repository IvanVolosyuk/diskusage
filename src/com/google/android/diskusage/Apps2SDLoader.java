package com.google.android.diskusage;

import java.io.DataInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageStats;
import android.os.Build;
import android.os.RemoteException;
import android.util.Log;

public class Apps2SDLoader {

  private Activity activity;
  private int numLookups;

  public Apps2SDLoader(Activity activity) {
    this.activity = activity;
  }
  
  private synchronized void changeNumLookups(int change) {
    numLookups += change;
  }
  
  private Map<String, Long> getDfSizes() {
    Map<String, Long> sizes = new TreeMap<String, Long>();
    final int sdkVersion = Integer.parseInt(Build.VERSION.SDK);
    if (sdkVersion < Build.VERSION_CODES.FROYO) return sizes;

    try {
      Process proc = Runtime.getRuntime().exec("df");
      DataInputStream is = new DataInputStream(proc.getInputStream());
      while (true) {
        String line = is.readLine();
        if (line == null) break;
        String[] parts = line.split(" ");
        if (parts.length < 5) continue;
        String pkg = parts[0];
        if (!pkg.endsWith(":")) continue;
        pkg = pkg.replaceAll("^.*/", "").replaceAll("-.*$", "");
        String sizeStr = parts[3];
        long size = 0;
        if (sizeStr.endsWith("K")) {
          size = Integer.parseInt(sizeStr.substring(0, sizeStr.length() - 1)) * 1024;
        } else if (sizeStr.endsWith("M")) {
          size = Integer.parseInt(sizeStr.substring(0, sizeStr.length() - 1)) * 1024 * 1024;
        }
        Log.d("diskusage", "Override " + pkg + " - " + size);
        sizes.put(pkg, size);
      }
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return sizes;
  }
  
  public FileSystemEntry[] load(boolean sdOnly, final AppFilter appFilter) throws Throwable {
    final Map<String, Long> dfSizes = getDfSizes();
    final ArrayList<FileSystemEntry> entries = new ArrayList<FileSystemEntry>();

    PackageManager pm = activity.getPackageManager();
    Method getPackageSizeInfo = pm.getClass().getMethod(
        "getPackageSizeInfo", String.class, IPackageStatsObserver.class);
    for (final PackageInfo info : pm.getInstalledPackages(
        PackageManager.GET_META_DATA | PackageManager.GET_UNINSTALLED_PACKAGES)) {
      Log.d("diskusage", "Got package: " + info.packageName);
      if (info.applicationInfo == null) {
        Log.d("diskusage", "No applicationInfo");
        continue;
      }
      int flag = 0x40000; // ApplicationInfo.FLAG_EXTERNAL_STORAGE
      boolean on_sdcard = (info.applicationInfo.flags & flag) != 0;
      if (on_sdcard || !sdOnly) {
        changeNumLookups(1);
        final String pkg = info.packageName;
        final String name = pm.getApplicationLabel(info.applicationInfo).toString();
        getPackageSizeInfo.invoke(pm, pkg, new IPackageStatsObserver.Stub() {

          @Override
          public void onGetStatsCompleted(PackageStats pStats, boolean succeeded)
          throws RemoteException {
            synchronized (Apps2SDLoader.this) {
              changeNumLookups(-1);
              if (succeeded) {
                FileSystemPackage p = new FileSystemPackage(
                    name, pkg, pStats, info.applicationInfo.flags, dfSizes.get(pkg));
                p.applyFilter(appFilter);
                entries.add(p);
                Log.i("diskusage", "codeSize: " + pStats.codeSize);
              }
              Apps2SDLoader.this.notify();
            }
          }
        });

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
    return result;
  }
}
