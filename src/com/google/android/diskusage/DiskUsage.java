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

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;
import android.view.Menu;

public class DiskUsage extends LoadableActivity {
  protected FileSystemView view;
  private Bundle savedState;
  
  protected FileSystemView makeView(DiskUsage diskUsage, FileSystemEntry root) {
    return new FileSystemView(this, root);
  }
  
  @Override
  protected void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    Bundle receivedState = getIntent().getBundleExtra(STATE_KEY);
    if (receivedState != null) onRestoreInstanceState(receivedState);
  }
  
  public static int getExternalBlockSize() {
    final File sdcard = Environment.getExternalStorageDirectory();
    StatFs data = new StatFs(sdcard.getAbsolutePath());
    int blockSize = data.getBlockSize();
    return blockSize;
  }
  
  public int getBlockSize() {
    return getExternalBlockSize();
  }

  @Override
  protected void onResume() {
    super.onResume();
    FileSystemEntry.blockSize = getBlockSize();
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
        view = makeView(DiskUsage.this, root);
        if (!isCached) view.startZoomAnimation();
        setContentView(view);
        view.requestFocus();
        if (savedState != null) {
          onRestoreInstanceState(savedState);
          savedState = null;
        }
      }
    }, false);
  }
  
  @Override
  public void onActivityResult(int a, int result, Intent i) {
    if (view != null) {
      if (result != RESULT_OK) return;
      String path = i.getStringExtra("path");
      view.continueDelete(path);
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
    if (view != null)
      view.restoreState(inState);
    else
      savedState = inState;
  }
  
  public interface AfterLoad {
    public void run(FileSystemEntry root, boolean isCached);
  }

  @Override
  FileSystemEntry scan() {
    final File sdcard = Environment.getExternalStorageDirectory();
    StatFs data = new StatFs(sdcard.getAbsolutePath());
    int blockSize = data.getBlockSize();
    FileSystemEntry.blockSize = blockSize;
    long freeBlocks = data.getAvailableBlocks();
    long totalBlocks = data.getBlockCount();
    

    FileSystemEntry rootElement =
      new FileSystemEntry(null, sdcard, 0, 20, blockSize);
    ArrayList<FileSystemEntry> entries = new ArrayList<FileSystemEntry>();
    
    if (rootElement.children != null) {
      for (FileSystemEntry e : rootElement.children) {
        entries.add(e);
      }
    }
    
    FileSystemEntry[] apps = loadApps2SD(true, AppFilter.getFilterForDiskUsage(), blockSize);
    if (apps != null) {
      FileSystemEntry apps2sd = new FileSystemEntry("Apps2SD", apps, blockSize);
      entries.add(apps2sd);
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
    
    rootElement = new FileSystemEntry("sdcard", entries.toArray(new FileSystemEntry[0]), blockSize);
    FileSystemEntry newRoot = new FileSystemEntry(null,
        new FileSystemEntry[] { rootElement }, blockSize);
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
  
  public static final String STATE_KEY="state";
  public static final int DISKUSAGE_STATE = 5;
  public static final int APPUSAGE_STATE = 6;
}
