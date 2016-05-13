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

import java.util.ArrayList;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.google.android.diskusage.AppFilter.App2SD;
import com.google.android.diskusage.datasource.DataSource;
import com.google.android.diskusage.datasource.StatFsSource;
import com.google.android.diskusage.entity.FileSystemEntry;
import com.google.android.diskusage.entity.FileSystemFreeSpace;
import com.google.android.diskusage.entity.FileSystemPackage;
import com.google.android.diskusage.entity.FileSystemRoot;
import com.google.android.diskusage.entity.FileSystemSpecial;
import com.google.android.diskusage.entity.FileSystemSuperRoot;
import com.google.android.diskusage.entity.FileSystemSystemSpace;

public class AppUsage extends DiskUsage {
  private AppFilter pendingFilter;

  FileSystemSuperRoot wrapApps(FileSystemSpecial appsElement,
      AppFilter filter, int displayBlockSize) {
    long freeSize = 0;
    long allocatedSpace = 0;
    long systemSize = 0;
    Log.d("diskusage", "memory = " + filter.memory);
    if (filter.memory == App2SD.INTERNAL) {
      StatFsSource data = DataSource.get().statFs("/data");
      int dataBlockSize = data.getBlockSize();
      freeSize = data.getAvailableBlocks() * dataBlockSize;
      allocatedSpace = data.getBlockCount() * dataBlockSize - freeSize;
    }

    if (allocatedSpace > 0) {
      systemSize = allocatedSpace - appsElement.getSizeInBlocks() * displayBlockSize;
    }

//    if (filter.useSD) {
//      FileSystemRoot newRoot = new FileSystemRoot(displayBlockSize);
//      newRoot.setChildren(new FileSystemEntry[] { appsElement }, displayBlockSize);
//      return newRoot;
//    }

    ArrayList<FileSystemEntry> entries = new ArrayList<FileSystemEntry>();
    entries.add(appsElement);
    if (systemSize > 0) {
      entries.add(new FileSystemSystemSpace("System data", systemSize, displayBlockSize));
    }
    if (freeSize > 0) {
      entries.add(new FileSystemFreeSpace("Free space", freeSize, displayBlockSize));
    }

    FileSystemEntry[] internalArray = entries.toArray(new FileSystemEntry[] {});
    String name = "Data";
    if (filter.memory == App2SD.BOTH) {
      name = "Data & Storage";
    } else if (filter.memory == App2SD.APPS2SD) {
      name = "Storage";
    }
    FileSystemEntry internalElement =
      FileSystemRoot.makeNode(name, "/Apps").setChildren(
        internalArray, displayBlockSize);

    FileSystemSuperRoot newRoot = new FileSystemSuperRoot(displayBlockSize);
    newRoot.setChildren(new FileSystemEntry[] { internalElement }, displayBlockSize);
    return newRoot;
  }

  @Override
  FileSystemSuperRoot scan() {
    AppFilter filter  = pendingFilter;
    int displayBlockSize = 512;
    FileSystemEntry[] appsArray = loadApps2SD(false, filter, displayBlockSize);
    FileSystemSpecial appsElement = new FileSystemSpecial("Applications", appsArray, displayBlockSize);
    appsElement.filter = filter;
    return wrapApps(appsElement, filter, displayBlockSize);
  }

  @Override
  protected void onCreate(Bundle icicle) {
    pendingFilter = AppFilter.loadSavedAppFilter(this);
    super.onCreate(icicle);
    Log.d("diskusage", "onCreate");
  }

  private FileSystemSpecial getAppsElement(FileSystemState view) {
    FileSystemEntry root = view.masterRoot;
    FileSystemEntry apps = root.children[0].children[0];
    if (apps instanceof FileSystemPackage) {
      apps = apps.parent;
    }
    return (FileSystemSpecial) apps;
  }

  private void updateFilter(AppFilter newFilter) {
    // FIXME: hack
    if (fileSystemState == null) {
      pendingFilter = newFilter;
      return;
    }

    int displayBlockSize = fileSystemState.masterRoot.getDisplayBlockSize();
    FileSystemSpecial appsElement = getAppsElement(fileSystemState);
    if (newFilter.equals(appsElement.filter)) {
      return;
    }
    for (FileSystemEntry entry : appsElement.children) {
      FileSystemPackage pkg = (FileSystemPackage) entry;
      pkg.applyFilter(newFilter, displayBlockSize);
    }
    java.util.Arrays.sort(appsElement.children, FileSystemEntry.COMPARE);

    appsElement = new FileSystemSpecial(appsElement.name, appsElement.children,
        displayBlockSize);
    appsElement.filter = newFilter;

    FileSystemSuperRoot newRoot = wrapApps(appsElement, newFilter, displayBlockSize);
    getPersistantState().root = newRoot;
    afterLoadAction.clear();
    fileSystemState.startZoomAnimationInRenderThread(newRoot, true, false);
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    Log.d("diskusage", "onSaveInstanceState");

    if (fileSystemState == null) return;
    fileSystemState.killRenderThread();
    FileSystemSpecial appsElement = getAppsElement(fileSystemState);
    outState.putParcelable("filter", appsElement.filter);
  }

  @Override
  protected void onRestoreInstanceState(Bundle inState) {
    super.onRestoreInstanceState(inState);
    Log.d("diskusage", "onRestoreInstanceState");
    AppFilter newFilter = (AppFilter) inState.getParcelable("filter");
    if (newFilter != null) updateFilter(newFilter);
  }

  @Override
  public void onActivityResult(int a, int result, Intent i) {
    super.onActivityResult(a, result, i);
    AppFilter newFilter = AppFilter.loadSavedAppFilter(this);
    updateFilter(newFilter);
  }

  @Override
  protected void onResume() {
    // TODO Auto-generated method stub
    super.onResume();
    Log.d("diskusage", "onResume");

  }

  @Override
  protected void onPause() {
    // TODO Auto-generated method stub
    super.onPause();
    Log.d("diskusage", "onPause");
  }
}
