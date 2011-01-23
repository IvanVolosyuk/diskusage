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
  
  protected FileSystemView makeView(DiskUsage diskUsage, FileSystemEntry root) {
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
  
  public int getBlockSize() {
    StatFs data = new StatFs(getRootPath());
    int blockSize = data.getBlockSize();
    return blockSize;
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
      public void run(FileSystemEntry root, boolean isCached) {
        FileSystemEntry.blockSize = getBlockSize();
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
    if (view != null) {
      if (result != RESULT_DELETE_CONFIRMED) return;
      pathToDelete = i.getStringExtra("path"); 
    }
  }
  

  @Override
  public final boolean onPrepareOptionsMenu(Menu menu) {
    if (view != null) {
      view.onPrepareOptionsMenu(menu);
    }
    return true;
  }

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
    public void run(FileSystemEntry root, boolean isCached);
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

  @Override
  FileSystemEntry scan() {
    final MountPoint mountPoint = MountPoint.getMountPoints().get(getRootPath());
    StatFs data = new StatFs(mountPoint.getRoot());
    final int blockSize = data.getBlockSize();
    long freeBlocks = data.getAvailableBlocks();
    long totalBlocks = data.getBlockCount();
    final long busyBlocks = totalBlocks - freeBlocks;
    

    int heap = getMemoryQuota();
    final Scanner scanner = new Scanner(
        20, blockSize, mountPoint.getExcludeFilter(), busyBlocks, heap);
    
    progressUpdater = new Runnable() {
      @Override
      public void run() {
        MyProgressDialog dialog = getPersistantState().loading;
        if (dialog != null) {
          dialog.setMax(busyBlocks);
          dialog.setProgress(scanner.pos, scanner.lastCreatedFile);
        }
        handler.postDelayed(this, 50);
      }
    };
    handler.post(progressUpdater);
    
    FileSystemEntry rootElement = scanner.scan(
            new File(mountPoint.getRoot()));
    
    handler.removeCallbacks(progressUpdater);
    
    ArrayList<FileSystemEntry> entries = new ArrayList<FileSystemEntry>();
    
    if (rootElement.children != null) {
      for (FileSystemEntry e : rootElement.children) {
        entries.add(e);
      }
    }
    
    if (mountPoint.hasApps2SD) {
      FileSystemEntry[] apps = loadApps2SD(true, AppFilter.getFilterForDiskUsage(), blockSize);
      if (apps != null) {
        FileSystemEntry apps2sd = FileSystemEntry.makeNode(null, "Apps2SD").setChildren(apps);
        entries.add(apps2sd);
      }
    }
    
    long visibleBlocks = 0;
    for (FileSystemEntry e : entries) {
      visibleBlocks += e.getSizeInBlocks();
    }
    
    long systemBlocks = totalBlocks - freeBlocks - visibleBlocks;
    Collections.sort(entries, FileSystemEntry.COMPARE);
    if (systemBlocks > 0) {
      entries.add(new FileSystemSystemSpace("System data", systemBlocks * blockSize, blockSize));
      entries.add(new FileSystemFreeSpace("Free space", freeBlocks * blockSize, blockSize));
    } else {
      freeBlocks += systemBlocks;
      if (freeBlocks > 0) {
        entries.add(new FileSystemFreeSpace("Free space", freeBlocks * blockSize, blockSize));
      }
    }
    
    rootElement = FileSystemEntry.makeNode(
        null, getRootTitle()).setChildren(entries.toArray(new FileSystemEntry[0]));
    FileSystemEntry newRoot = FileSystemEntry.makeNode(
        null, null).setChildren(new FileSystemEntry[] { rootElement });
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
