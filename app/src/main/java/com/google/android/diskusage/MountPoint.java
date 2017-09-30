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
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.google.android.diskusage.datasource.DataSource;
import com.google.android.diskusage.datasource.PortableFile;
import com.google.android.diskusage.datasource.StatFsSource;
import com.google.android.diskusage.entity.FileSystemEntry;
import com.google.android.diskusage.entity.FileSystemEntry.ExcludeFilter;
import com.google.android.diskusage.entity.FileSystemRoot;

public class MountPoint {
  String title;
  final String root;
  final boolean forceHasApps;

  private static boolean init = false;

  MountPoint(String title, String root, boolean forceHasApps) {
    this.title = title;
    this.root = root;
    this.forceHasApps = forceHasApps;
  }

  private static Map<String, MountPoint> mountPoints = new TreeMap<>();

  public static Map<String,MountPoint> getMountPoints(Context context) {
    initMountPoints(context);
    return mountPoints;
  }

  public static MountPoint getNormal(Context context, String rootPath) {
    initMountPoints(context);
    return mountPoints.get(rootPath);
  }

  public static MountPoint forPath(Context context, String path) {
	Log.d("diskusage", "Looking for mount point for path: " + path);
    initMountPoints(context);
    MountPoint match = null;
    path = FileSystemRoot.withSlash(path);
    for (MountPoint m : mountPoints.values()) {
      if (path.contains(FileSystemRoot.withSlash(m.root))) {
        if (match == null || match.root.length() < m.root.length()) {
          Log.d("diskusage", "MATCH:" + m.root);
          match = m;
        }
      }
    }

    return match;
  }

  public String getRoot() {
    return root;
  }

  private static void initMountPoints(Context context) {
    if (init) return;
    init = true;

    for (PortableFile dir : DataSource.get().getExternalFilesDirs(context)) {
      String path = dir.getAbsolutePath().replaceFirst("/Android/data/com.google.android.diskusage/files", "");
      Log.d("diskusage", "mountpoint " + path);
      boolean internal = !dir.isExternalStorageRemovable();
      String title =  internal ? context.getString(R.string.storage_card) : path;
      mountPoints.put(path, new MountPoint(title, path, internal));
    }
  }

  public static void reset() {
    mountPoints = new TreeMap<>();
    init = false;
  }
}
