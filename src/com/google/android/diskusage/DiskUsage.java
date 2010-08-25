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

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;

public class DiskUsage extends LoadableActivity {
  protected FileSystemView view;
  private static FileSystemEntry root;
  
  
  protected FileSystemView makeView(DiskUsage diskUsage, FileSystemEntry root) {
    return new FileSystemView(this, root);
  }
  
  @Override
  protected void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    LoadFiles(this, new AfterLoad() {
      public void run(FileSystemEntry root) {
        view = makeView(DiskUsage.this, root);
        setContentView(view);
        view.requestFocus();
      }
    }, false);
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (pkg_removed == null) return;
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

  final protected void onSaveInstanceState(Bundle outState) {
    if (view != null)
      view.saveState(outState);
  }
  final protected void onRestoreInstanceState(Bundle inState) {
    if (view != null)
      view.restoreState(inState); 
  }
  
  public interface AfterLoad {
    public void run(FileSystemEntry root);
  }

  @Override
  FileSystemEntry getRoot() {
    return root;
  }

  @Override
  void setRoot(FileSystemEntry root) {
    DiskUsage.root = root;
  }

  @Override
  FileSystemEntry scan() {
    final File sdcard = Environment.getExternalStorageDirectory();

    FileSystemEntry rootElement =
      new FileSystemEntry(null, sdcard, 0, 20);
    FileSystemEntry[] apps = loadApps2SD(true, AppFilter.getFilterForDiskUsage());
    if (apps != null) {
      FileSystemEntry apps2sd = new FileSystemEntry("Apps2SD", apps);
      FileSystemEntry[] files = rootElement.children;
      FileSystemEntry[] newFiles = new FileSystemEntry[files.length + 1];
      System.arraycopy(files, 0, newFiles, 0, files.length);
      newFiles[files.length] = apps2sd;
      java.util.Arrays.sort(newFiles, FileSystemEntry.COMPARE);
      rootElement = new FileSystemEntry("sdcard", newFiles);
    }
    FileSystemEntry newRoot = new FileSystemEntry(null,
        new FileSystemEntry[] { rootElement } );
    return newRoot;
  }
  
  protected FileSystemEntry[] loadApps2SD(boolean sdOnly, AppFilter appFilter) {
    final int sdkVersion = Integer.parseInt(Build.VERSION.SDK);
    if (sdkVersion < Build.VERSION_CODES.FROYO && sdOnly) return null;

    try {
      return (new Apps2SDLoader(this).load(sdOnly, appFilter));
    } catch (Throwable t) {
      Log.e("diskusage", "problem loading apps2sd info", t);
      return null;
    }
  }
}
