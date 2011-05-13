/**
 * DiskUsage - displays sdcard usage on android.
 * Copyright (C) 2008 Ivan Volosyuk
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.StatFs;
import android.util.Log;
import android.view.Menu;

public class DiskUsage extends LoadableActivity {
  protected FileSystemView view;
  private Bundle savedState;
  public static final int RESULT_DELETE_CONFIRMED = 10;
  public static final int RESULT_DELETE_CANCELED = 11;
  
  public static final String STATE_KEY = "state";
  public static final String TITLE_KEY = "title";
  public static final String ROOT_KEY = "root";
  public static final String KEY_KEY = "key";

  private String pathToDelete;
  
  private String rootPath;
  private String rootTitle;
  String key;
  
  protected FileSystemView makeView(DiskUsage diskUsage, FileSystemRoot root) {
    return new FileSystemView(this, root);
  }
  
  @Override
  protected void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    Intent i = getIntent();
    rootPath = i.getStringExtra(ROOT_KEY);
    rootTitle = i.getStringExtra(TITLE_KEY);
    key = i.getStringExtra(KEY_KEY);
    Bundle receivedState = i.getBundleExtra(STATE_KEY);
    Log.d("diskusage", "onCreate, rootPath = " + rootPath + " receivedState = " + receivedState);
    if (receivedState != null) onRestoreInstanceState(receivedState);
  }
  
  @Override
  protected void onResume() {
    super.onResume();
    if (pkg_removed != null) {
      // Check if package removed
      String pkg_name = pkg_removed.pkg;
      PackageManager pm = getPackageManager();
      try {
        pm.getPackageInfo(pkg_name, 0);
      } catch (NameNotFoundException e) {
        if (view != null)
          view.remove(pkg_removed);
      }
      pkg_removed = null;
    }
    LoadFiles(this, new AfterLoad() {
      public void run(FileSystemRoot root, boolean isCached) {
        view = makeView(DiskUsage.this, root);
        if (!isCached) view.startZoomAnimation();
        setContentView(view);
        view.requestFocus();
        if (savedState != null) {
          onRestoreInstanceState(savedState);
          savedState = null;
        }
        if (pathToDelete != null) {
          String path = pathToDelete;
          pathToDelete = null;
          view.continueDelete(path);
        }
      }
    }, false);
  }
  
  @Override
  protected void onPause() {
    super.onPause();
    if (view != null) {
      savedState = new Bundle();
      view.saveState(savedState);
    }
  }
  
  @Override
  public void onActivityResult(int a, int result, Intent i) {
    if (result != RESULT_DELETE_CONFIRMED) return;
    pathToDelete = i.getStringExtra("path"); 
  }
  
//  @Override
//  public boolean onCreateOptionsMenu(Menu menu) {
//      MenuInflater inflater = getMenuInflater();
//      inflater.inflate(R.menu.menu, menu);
//      return true;
//  }

  @Override
  public final boolean onPrepareOptionsMenu(Menu menu) {
    if (view != null) {
      view.onPrepareOptionsMenu(menu);
    }
    return true;
  }
  
//  @Override
//  public boolean onOptionsItemSelected(MenuItem item) {
//    if (view != null) {
//      view.onOptionItemSelected(item);
//    }
//    return true;
//  }

  protected void onSaveInstanceState(Bundle outState) {
    if (view != null)
      view.saveState(outState);
  }
  protected void onRestoreInstanceState(Bundle inState) {
    Log.d("diskusage", "onRestoreInstanceState, rootPath = " + inState.getString(ROOT_KEY));

    if (view != null)
      view.restoreState(inState);
    else
      savedState = inState;
  }
  
  public interface AfterLoad {
    public void run(FileSystemRoot root, boolean isCached);
  }
  
  Handler handler = new Handler();
  
  private Runnable progressUpdater;
  
  static abstract class MemoryClass {
    abstract int maxHeap();
    
    static class MemoryClassDefault extends MemoryClass {
      @Override
      int maxHeap() {
        return 16 * 1024 * 1024;
      }
    };
    
    static MemoryClass getInstance(DiskUsage diskUsage) {
      final int sdkVersion = Integer.parseInt(Build.VERSION.SDK);
      if (sdkVersion < Build.VERSION_CODES.ECLAIR) {
        return new MemoryClassDefault();
      } else {
        return diskUsage.new MemoryClassDetected();
      }
    }
  };

  class MemoryClassDetected extends MemoryClass {
    @Override
    int maxHeap() {
      ActivityManager manager = (ActivityManager) DiskUsage.this.getSystemService(Context.ACTIVITY_SERVICE);
      return manager.getMemoryClass() * 1024 * 1024;
    }
  }
  
  MemoryClass memoryClass = MemoryClass.getInstance(this);
  
  private int getMemoryQuota() {
    int totalMem = memoryClass.maxHeap();
    int numMountPoints = MountPoint.getMountPoints().size();
    return totalMem / (numMountPoints + 1);
  }
  
  static class FileSystemStats {
    final int blockSize;
    final long freeBlocks;
    final long busyBlocks;
    final long totalBlocks;
    
    public FileSystemStats(MountPoint mountPoint) {
      StatFs stats = null;
      try {
        stats = new StatFs(mountPoint.getRoot());
      } catch (IllegalArgumentException e) {
        Log.e("diskusage",
            "Failed to get filesystem stats for " + mountPoint.getRoot(), e);
      }
      if (stats != null) {
        blockSize = stats.getBlockSize();
        freeBlocks = stats.getAvailableBlocks();
        totalBlocks = stats.getBlockCount();
        busyBlocks = totalBlocks - freeBlocks;
      } else {
        freeBlocks = totalBlocks = busyBlocks = 0;
        blockSize = 512;
      }
    }
    public String formatUsageInfo() {
      if (totalBlocks == 0) return "Used <no information>";
      return String.format("Used %s of %s",
          FileSystemEntry.calcSizeString(busyBlocks * blockSize),
          FileSystemEntry.calcSizeString(totalBlocks * blockSize));
    }
  };
  
  public interface ProgressGenerator {
    FileSystemEntry lastCreatedFile();
    long pos();
  };
  
  Runnable makeProgressUpdater(final ProgressGenerator scanner,
      final FileSystemStats stats) {
    return new Runnable() {
      private FileSystemEntry file;
      @Override
      public void run() {
        MyProgressDialog dialog = getPersistantState().loading;
        if (dialog != null) {
          dialog.setMax(stats.busyBlocks);
          FileSystemEntry lastFile = scanner.lastCreatedFile();
          if (lastFile != file) {
            dialog.setProgress(scanner.pos(), lastFile);
          }
          file = lastFile;
        }
        handler.postDelayed(this, 50);
      }
    };
  }

  @Override
  FileSystemRoot scan() throws IOException, InterruptedException {
    final MountPoint mountPoint = MountPoint.get(getRootPath());
    final FileSystemStats stats = new FileSystemStats(mountPoint);

    int heap = getMemoryQuota();
    FileSystemEntry rootElement = null;

    final NativeScanner scanner = new NativeScanner(DiskUsage.this, stats.blockSize, stats.busyBlocks, heap);
    progressUpdater = makeProgressUpdater(scanner, stats);
    handler.post(progressUpdater);
    
    try {
//      if (true) throw new RuntimeException("native fail");
      rootElement = scanner.scan(mountPoint);
    } catch (RuntimeException e) {
      if (mountPoint.rootRequired) throw e;
      // Legacy code for devices which fail to setup native code
      handler.removeCallbacks(progressUpdater);
      final Scanner legacyScanner = new Scanner(
          20, stats.blockSize, mountPoint.getExcludeFilter(), stats.busyBlocks, heap);
      progressUpdater = makeProgressUpdater(legacyScanner, stats);
      handler.post(progressUpdater);
      rootElement = legacyScanner.scan(new File(mountPoint.root));
    }
    
    handler.removeCallbacks(progressUpdater);
    
    ArrayList<FileSystemEntry> entries = new ArrayList<FileSystemEntry>();
    
    if (rootElement.children != null) {
      for (FileSystemEntry e : rootElement.children) {
        entries.add(e);
      }
    }
    
    if (mountPoint.hasApps2SD) {
      FileSystemEntry[] apps = loadApps2SD(true, AppFilter.getFilterForDiskUsage(), stats.blockSize);
      if (apps != null) {
        FileSystemEntry apps2sd = FileSystemEntry.makeNode(null, "Apps2SD").setChildren(apps, stats.blockSize);
        entries.add(apps2sd);
      }
    }
    
    long visibleBlocks = 0;
    for (FileSystemEntry e : entries) {
      visibleBlocks += e.getSizeInBlocks();
    }
    
    long systemBlocks = stats.totalBlocks - stats.freeBlocks - visibleBlocks;
    Collections.sort(entries, FileSystemEntry.COMPARE);
    if (systemBlocks > 0) {
      entries.add(new FileSystemSystemSpace("System data", systemBlocks * stats.blockSize, stats.blockSize));
      entries.add(new FileSystemFreeSpace("Free space", stats.freeBlocks * stats.blockSize, stats.blockSize));
    } else {
      long freeBlocks = stats.freeBlocks + systemBlocks;
      if (freeBlocks > 0) {
        entries.add(new FileSystemFreeSpace("Free space", freeBlocks * stats.blockSize, stats.blockSize));
      }
    }
    
    rootElement = FileSystemEntry.makeNode(
        null, getRootTitle()).setChildren(entries.toArray(new FileSystemEntry[0]), stats.blockSize);
    FileSystemRoot newRoot = new FileSystemRoot(stats.blockSize);
    newRoot.setChildren(new FileSystemEntry[] { rootElement }, stats.blockSize);
    return newRoot;
  }
  
  protected FileSystemEntry[] loadApps2SD(boolean sdOnly, AppFilter appFilter, int blockSize) {
    final int sdkVersion = Integer.parseInt(Build.VERSION.SDK);
    if (sdkVersion < Build.VERSION_CODES.FROYO && sdOnly) return null;

    try {
      return (new Apps2SDLoader(this).load(sdOnly, appFilter, blockSize));
    } catch (Throwable t) {
      Log.e("diskusage", "problem loading apps2sd info", t);
      return null;
    }
  }
  
  public FileSystemEntry.ExcludeFilter getExcludeFilter() {
    return MountPoint.getMountPoints().get(getRootPath()).getExcludeFilter();
  }
  
  @Override
  public String getRootTitle() {
    return rootTitle;
  }

  @Override
  public String getRootPath() {
    return rootPath;
  }
}
