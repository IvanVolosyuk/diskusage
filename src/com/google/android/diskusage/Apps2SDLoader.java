package com.google.android.diskusage;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageStats;
import android.os.Environment;
import android.os.RemoteException;
import android.os.StatFs;
import android.util.Log;

public class Apps2SDLoader {

  private Activity activity;
  private FileSystemEntry rootElement;
  private ArrayList<FileSystemEntry> entries = new ArrayList<FileSystemEntry>();

  public Apps2SDLoader(Activity activity, FileSystemEntry rootElement) {
    this.activity = activity;
    this.rootElement = rootElement;
  }
  
  int numLookups;
  int size;

  private synchronized void changeNumLookups(int change) {
    numLookups += change;
  }
  
  public void load() throws Throwable {
//    File path = Environment.getExternalStorageDirectory();
//    StatFs stat = new StatFs(path.getPath());
//    long blockSize = stat.getBlockSize();
//    long totalBlocks = stat.getBlockCount();
//    long availableBlocks = stat.getAvailableBlocks();
//    long allocatedSize = (totalBlocks - availableBlocks) * blockSize;

    PackageManager pm = activity.getPackageManager();
    Method getPackageSizeInfo = pm.getClass().getMethod(
        "getPackageSizeInfo", String.class, IPackageStatsObserver.class);
    final FileSystemEntry apps = new FileSystemEntry(rootElement, "Apps2SD", 0, null);
    for (final PackageInfo info : pm.getInstalledPackages(PackageManager.GET_META_DATA)) {
      if (info.applicationInfo == null) continue;
      int flag = 0x40000; // ApplicationInfo.FLAG_EXTERNAL_STORAGE
      if ((info.applicationInfo.flags & flag) != 0) {
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
                entries.add(new FileSystemPackage(apps, name, pkg, (int) pStats.codeSize, null));
                Log.i("diskusage", "codeSize: " + pStats.codeSize);
                size += pStats.codeSize;
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
    if (size != 0) {
      apps.size = size;
      apps.children = entries.toArray(new FileSystemEntry[] {});
      Arrays.sort(apps.children, apps);

      FileSystemEntry[] children = new FileSystemEntry[rootElement.children.length + 1];
      System.arraycopy(rootElement.children, 0, children, 0, rootElement.children.length);
      children[rootElement.children.length] = apps;
      java.util.Arrays.sort(children, rootElement);
      rootElement.children = children;
      rootElement.size += apps.size;
    }
  }
}
