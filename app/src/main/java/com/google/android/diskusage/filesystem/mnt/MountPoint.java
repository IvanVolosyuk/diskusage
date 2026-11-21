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

package com.google.android.diskusage.filesystem.mnt;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import com.google.android.diskusage.R;
import com.google.android.diskusage.datasource.PortableFile;
import com.google.android.diskusage.datasource.fast.PortableFileImpl;
import timber.log.Timber;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A volume the user can analyse: internal shared storage, an SD card or a USB
 * drive.
 *
 * <p>Volume discovery was rewritten for modern Android. Before, only paths
 * derived from {@code getExternalFilesDirs()} were used, which silently dropped
 * volumes (most notably the internal storage on some OEM ROMs, removable SD
 * cards and USB devices). Now {@link StorageManager#getStorageVolumes()} is the
 * primary source and the app-private directories are only a fallback.
 */
public class MountPoint {
  private final String title;
  private final String root;
  private final boolean forceHasApps;

  private static boolean init = false;

  MountPoint(String title, String root, boolean forceHasApps) {
    this.title = title;
    this.root = root;
    this.forceHasApps = forceHasApps;
  }

  public String getRoot() {
    return root;
  }

  public String getTitle() {
    return title;
  }

  public boolean isRootRequired() {
    return false;
  }

  /** Whether the app may delete content on this volume (full-disk access). */
  public boolean isDeleteSupported() {
    return true;
  }

  public String getKey() {
    return "storage:" + root;
  }

  public boolean hasApps() {
    return forceHasApps;
  }

  private static List<MountPoint> mountPoints = new ArrayList<>();
  private static Map<String, MountPoint> mountPointForKey = new HashMap<>();

  int getChecksum() {
    return RootMountPoint.checksum;
  }

  public static MountPoint getForKey(Context context, String key) {
    initMountPoints(context);
    MountPoint mountPoint = mountPointForKey.get(key);
    if (mountPoint != null) {
      return mountPoint;
    }
    return RootMountPoint.getForKey(context, key);
  }

  public static List<MountPoint> getMountPoints(Context context) {
    initMountPoints(context);
    RootMountPoint.initMountPoints(context);
    return mountPoints;
  }

  private static void initMountPoints(Context context) {
    if (init) return;
    init = true;

    Set<String> seen = new LinkedHashSet<>();
    List<MountPoint> result = new ArrayList<>();

    // 1. Ask the framework which volumes exist. This is the authoritative list
    //    and includes USB mass storage and adopted/external SD cards.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      StorageManager storageManager =
          (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
      if (storageManager != null) {
        for (StorageVolume volume : storageManager.getStorageVolumes()) {
          String path = directoryOf(volume);
          if (path == null || !seen.add(VolumePathHelper.canonical(path))) continue;
          boolean primary = volume.isPrimary();
          String description = volume.getDescription(context);
          String title = primary
              ? context.getString(R.string.storage_card)
              : (description != null && description.length() > 0 ? description : path);
          result.add(new MountPoint(title, path, primary));
        }
      }
    }

    // 2. Fallback (and volume list completion): every directory the app can
    //    write files to is guaranteed to be a mounted volume.
    for (final PortableFile dir : PortableFileImpl.getExternalAppFilesDirs()) {
      if (dir == null) continue;
      String path = VolumePathHelper.stripAppFilesSuffix(
          dir.getAbsolutePath(), context.getPackageName());
      if (path == null || !seen.add(VolumePathHelper.canonical(path))) continue;
      boolean internal = !dir.isExternalStorageRemovable();
      String title = internal ? context.getString(R.string.storage_card) : path;
      result.add(new MountPoint(title, path, internal));
    }

    // 3. Always offer the primary shared storage, even if the two sources above
    //    failed (e.g. on TV devices without external files dirs).
    if (seen.isEmpty()) {
      File external = Environment.getExternalStorageDirectory();
      if (external != null) {
        result.add(new MountPoint(context.getString(R.string.storage_card),
            external.getAbsolutePath(), true));
      }
    }

    for (MountPoint mountPoint : result) {
      Timber.d("MountPoint %s -> %s (apps=%s)",
          mountPoint.getTitle(), mountPoint.getRoot(), mountPoint.hasApps());
      mountPoints.add(mountPoint);
      mountPointForKey.put(mountPoint.getKey(), mountPoint);
    }
  }

  /**
   * Resolves the file system path of a storage volume across API levels.
   * {@link StorageVolume#getDirectory()} only exists on API 30+, for older
   * releases the UUID based mount path is used instead.
   */
  private static String directoryOf(StorageVolume volume) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      File dir = volume.getDirectory();
      if (dir != null) return dir.getAbsolutePath();
    }
    String uuid = volume.getUuid();
    if (uuid != null) {
      File byUuid = new File("/storage/" + uuid);
      if (byUuid.isDirectory()) return byUuid.getAbsolutePath();
    }
    if (volume.isPrimary() || volume.isEmulated()) {
      File primary = Environment.getExternalStorageDirectory();
      if (primary != null) return primary.getAbsolutePath();
    }
    return null;
  }


  public static void reset() {
    mountPoints = new ArrayList<>();
    mountPointForKey = new HashMap<>();
    init = false;
    RootMountPoint.reset();
  }
}
