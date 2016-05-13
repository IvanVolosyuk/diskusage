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
import java.util.Map;
import java.util.TreeMap;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.google.android.diskusage.datasource.DataSource;
import com.google.android.diskusage.datasource.StatFsSource;
import com.google.android.diskusage.entity.FileSystemEntry;
import com.google.android.diskusage.entity.FileSystemEntry.ExcludeFilter;
import com.google.android.diskusage.entity.FileSystemRoot;

public class MountPoint {
  final FileSystemEntry.ExcludeFilter excludeFilter;
  String title;
  final String root;
  final boolean hasApps2SD;
  final boolean rootRequired;
  final boolean forceHasApps;
  final String fsType;

  MountPoint(String title, String root, ExcludeFilter excludeFilter,
      boolean hasApps2SD, boolean rootRequired, String fsType, boolean forceHasApps) {
    this.title = title;
    this.root = root;
    this.excludeFilter = excludeFilter;
    this.hasApps2SD = hasApps2SD;
    this.rootRequired = rootRequired;
    this.fsType = fsType;
    this.forceHasApps = forceHasApps;
  }

  private static MountPoint defaultStorage;
  private static Map<String, MountPoint> mountPoints = new TreeMap<String, MountPoint>();
  private static Map<String, MountPoint> rootedMountPoints = new TreeMap<String, MountPoint>();
  private static boolean init = false;
  private static MountPoint honeycombSdcard;
  static int checksum = 0;

  public static MountPoint getHoneycombSdcard(Context context) {
    initMountPoints(context);
    return honeycombSdcard;
  }

  public static MountPoint getDefaultStorage(Context context) {
    initMountPoints(context);
    return defaultStorage;
  }

  public static Map<String,MountPoint> getMountPoints(Context context) {
    initMountPoints(context);
    return mountPoints;
  }

  public static Map<String,MountPoint> getRootedMountPoints(Context context) {
    initMountPoints(context);
    return rootedMountPoints;
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
    for (MountPoint m : rootedMountPoints.values()) {
      if (path.contains(FileSystemRoot.withSlash(m.root))) {
        if (match == null || match.root.length() < m.root.length()) {
          match = m;
          Log.d("diskusage", "MATCH:" + m.root);
        }
      }
    }

    // FIXME: quick hack
    if (match == null) {
      Log.d("diskusage", "Use honeycomb hack for /data");
      match = mountPoints.get("/data");
    }
    return match;
  }

  public static MountPoint getRooted(Context context, String rootPath) {
    initMountPoints(context);
    return rootedMountPoints.get(rootPath);
  }

  public static boolean hasMultiple(Context context) {
    initMountPoints(context);
    return mountPoints.size() != 1;
  }

  public FileSystemEntry.ExcludeFilter getExcludeFilter() {
    return excludeFilter;
  }

  public String getRoot() {
    return root;
  }

  public static String storageCardPath() {
    File externalStorageDirectory = DataSource.get().getEnvironment().getExternalStorageDirectory();
    try {
      return externalStorageDirectory.getCanonicalPath();
    } catch (Exception e) {
      return externalStorageDirectory.getAbsolutePath();
    }
  }

  private static boolean isEmulated(String fsType) {
    return fsType.equals("sdcardfs") || fsType.equals("fuse");
  }

  private static void initMountPoints(Context context) {
    if (init) return;
    init = true;
    String storagePath = storageCardPath();
    Log.d("diskusage", "StoragePath: " + storagePath);

    ArrayList<MountPoint> mountPointsList = new ArrayList<MountPoint>();
    HashSet<String> excludePoints = new HashSet<String>();
    if (storagePath != null) {
      defaultStorage = new MountPoint(
              titleStorageCard(context), storagePath, null, false, false, "", false);
      mountPointsList.add(defaultStorage);
      mountPoints.put(storagePath, defaultStorage);
    }

    try {
      // FIXME: debug
      checksum = 0;
      BufferedReader reader = new BufferedReader(DataSource.get().getProc());
      String line;
      while ((line = reader.readLine()) != null) {
        checksum += line.length();
        Log.d("diskusage", "line: " + line);
        String[] parts = line.split(" +");
        if (parts.length < 3) continue;
        String mountPoint = parts[1];
        Log.d("diskusage", "Mount point: " + mountPoint);
        String fsType = parts[2];

        StatFsSource stat = null;
        try {
          stat = DataSource.get().statFs(mountPoint);
        } catch (Exception e) {
        }

        if (!(fsType.equals("vfat") || fsType.equals("tntfs") || fsType.equals("exfat")
            || fsType.equals("texfat") || isEmulated(fsType))
            || mountPoint.startsWith("/mnt/asec")
            || mountPoint.startsWith("/firmware")
            || mountPoint.startsWith("/mnt/secure")
            || mountPoint.startsWith("/data/mac")
            || stat == null
            || (mountPoint.endsWith("/legacy") && isEmulated(fsType))) {
          Log.d("diskusage", String.format("Excluded based on fsType=%s or black list", fsType));
          excludePoints.add(mountPoint);

          // Default storage is not vfat, removing it (real honeycomb)
          if (mountPoint.equals(storagePath)) {
            mountPointsList.remove(defaultStorage);
            mountPoints.remove(mountPoint);
          }
          if (/*rooted &&*/ !mountPoint.startsWith("/mnt/asec/")) {
            mountPointsList.add(new MountPoint(mountPoint, mountPoint, null, false, true, fsType, false));
          }
        } else {
          Log.d("diskusage", "Mount point is good");
          mountPointsList.add(new MountPoint(mountPoint, mountPoint, null, false, false, fsType, false));
        }
      }
      reader.close();

      for (MountPoint mountPoint: mountPointsList) {
        String prefix = mountPoint.root + "/";
        boolean has_apps2sd = false;
        ArrayList<String> excludes = new ArrayList<String>();
        String mountPointName = new File(mountPoint.root).getName();

        for (MountPoint otherMountPoint : mountPointsList) {
          if (otherMountPoint.root.startsWith(prefix)) {
            excludes.add(mountPointName + "/" + otherMountPoint.root.substring(prefix.length()));
          }
        }
        for (String otherMountPoint : excludePoints) {
          if (otherMountPoint.equals(prefix + ".android_secure")) {
            has_apps2sd = true;
          }
          if (otherMountPoint.startsWith(prefix)) {
            excludes.add(mountPointName + "/" + otherMountPoint.substring(prefix.length()));
          }
        }
        MountPoint newMountPoint = new MountPoint(
            mountPoint.root, mountPoint.root, new ExcludeFilter(excludes),
            has_apps2sd, mountPoint.rootRequired, mountPoint.fsType, false);
        if (mountPoint.rootRequired) {
          rootedMountPoints.put(mountPoint.root, newMountPoint);
        } else {
          mountPoints.put(mountPoint.root, newMountPoint);
        }
      }
    } catch (Exception e) {
      Log.e("diskusage", "Failed to get mount points", e);
    }
    final int sdkVersion = DataSource.get().getAndroidVersion();

    try {
      addMediaPaths(context);
    } catch (Throwable t) {
      Log.e("diskusage", "Adding media paths", t);
    }

    MountPoint storageCard = mountPoints.get(storageCardPath());
    if(sdkVersion >= Build.VERSION_CODES.HONEYCOMB
        && (storageCard == null || isEmulated(storageCard.fsType))) {
      mountPoints.remove(storageCardPath());
      // No real /sdcard in honeycomb
      honeycombSdcard = defaultStorage;
      mountPoints.put("/data", new MountPoint(
              titleStorageCard(context), "/data", null, false, false, "", true));
    }

    if (!mountPoints.isEmpty()) {
      defaultStorage = mountPoints.values().iterator().next();
      defaultStorage.title = titleStorageCard(context);
    }

    if (sdkVersion >= Build.VERSION_CODES.LOLLIPOP) {
      initMountPointsLollipop(context);
    }
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private static File getBaseDir(File dir) {
    if (dir == null) {
      return null;
    }

    long totalSpace = dir.getTotalSpace();
    while (true) {
      File base = dir.getParentFile();
      try {
        DataSource.get().getEnvironment().isExternalStorageEmulated(base);
      } catch (Exception e) {
        return dir;
      }
      if (base == null || dir.equals(base) || base.getTotalSpace() != totalSpace) {
        return dir;
      }
      dir = base;
    }
  }

  // Lollipop have new API to get storage states, which can try to use it instead complicated
  // legacy stuff.
  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private static void initMountPointsLollipop(Context context) {
    Map<String, MountPoint> mountPoints = new TreeMap<String, MountPoint>();
    File defaultDir = getBaseDir(DataSource.get().getExternalFilesDir(context));
    File[] dirs = DataSource.get().getExternalFilesDirs(context);
    for (File path : dirs) {
      if (path == null) {
        continue;
      }
      File dir = getBaseDir(path);
      boolean isEmulated = DataSource.get().getEnvironment().isExternalStorageEmulated(dir);
      boolean isRemovable = DataSource.get().getEnvironment().isExternalStorageRemovable(dir);
      boolean hasApps = isEmulated && !isRemovable;
      MountPoint mountPoint = new MountPoint(
          dir.equals(defaultDir) ? titleStorageCard(context) : dir.getAbsolutePath(),
          dir.getAbsolutePath(),
          new ExcludeFilter(new ArrayList<String>()),
          false /* hasApps2SD */,
          false /* rootRequired */,
          "whoCares",
          hasApps /* forceHasApps */);
      mountPoints.put(mountPoint.root, mountPoint);

      if (!isRemovable) {
        defaultStorage = mountPoint;
        honeycombSdcard = mountPoint;
      }
    }
    MountPoint.mountPoints = mountPoints;
  }

  @TargetApi(Build.VERSION_CODES.KITKAT)
  public static File[] getMediaStoragePaths(Context context) {
    try {
      return DataSource.get().getExternalFilesDirs(context);
    } catch (Throwable t) {
      return new File[0];
    }
  }

  public static String canonicalPath(File file) {
    try {
      return file.getCanonicalPath();
    } catch (Exception e) {
      return file.getAbsolutePath();
    }
  }

  private static void addMediaPaths(Context context) {
    File[] mediaStoragePaths = getMediaStoragePaths(context);
    for (File file : mediaStoragePaths) {
      while (file != null) {
        String canonical = canonicalPath(file);

        if (mountPoints.containsKey(canonical)) {
          break;
        }

        MountPoint rootedMountPoint = rootedMountPoints.get(canonical);
        if (rootedMountPoint != null) {
          mountPoints.put(canonical, new MountPoint(
              canonical,
              canonical,
              null,
              false,
              false,
              rootedMountPoint.fsType, false));
          break;
        }
        if (canonical.equals("/")) break;
        file = file.getParentFile();
      }
    }
  }



  private static String titleStorageCard(Context context) {
    return context.getString(R.string.storage_card);
  }

//  private static final String file =
//    "rootfs / rootfs ro,relatime 0 0\n" +
//    "tmpfs /dev tmpfs rw,relatime,mode=755 0 0\n" +
//    "devpts /dev/pts devpts rw,relatime,mode=600 0 0\n" +
//    "proc /proc proc rw,relatime 0 0\n" +
//    "sysfs /sys sysfs rw,relatime 0 0\n" +
//    "none /acct cgroup rw,relatime,cpuacct 0 0\n" +
//    "tmpfs /mnt/asec tmpfs rw,relatime,mode=755,gid=1000 0 0\n" +
//    "none /dev/cpuctl cgroup rw,relatime,cpu 0 0\n" +
//    "/dev/block/mtdblock3 /system yaffs2 ro,relatime 0 0\n" +
//    "/dev/block/mtdblock5 /data yaffs2 rw,nosuid,nodev,relatime 0 0\n" +
//    "/dev/block/mtdblock4 /cache yaffs2 rw,nosuid,nodev,relatime 0 0\n" +
//    "/sys/kernel/debug /sys/kernel/debug debugfs rw,relatime 0 0\n" +
//    "/dev/block/vold/179:1 /mnt/sdcard vfat rw,dirsync,nosuid,nodev,noexec,relatime,uid=1000,gid=1015,fmask=0702,dmask=0702,allow_utime=0020,codepage=cp437,iocharset=iso8859-1,shortname=mixed,utf8,errors=remount-ro 0 0\n" +
//    "/dev/block/vold/179:1 /mnt/secure/asec vfat rw,dirsync,nosuid,nodev,noexec,relatime,uid=1000,gid=1015,fmask=0702,dmask=0702,allow_utime=0020,codepage=cp437,iocharset=iso8859-1,shortname=mixed,utf8,errors=remount-ro 0 0\n" +
//    "tmpfs /mnt/sdcard/.android_secure tmpfs ro,relatime,size=0k,mode=000 0 0\n" +
//    "/dev/block/dm-0 /mnt/asec/com.dasur.language.rus.pack-1 vfat ro,dirsync,nosuid,nodev,noexec,relatime,uid=1000,fmask=0222,dmask=0222,codepage=cp437,iocharset=iso8859-1,shortname=mixed,utf8,errors=remount-ro 0 0\n" +
//    "/dev/block/dm-1 /mnt/asec/net.bytten.xkcdviewer-1 vfat ro,dirsync,nosuid,nodev,noexec,relatime,uid=1000,fmask=0222,dmask=0222,codepage=cp437,iocharset=iso8859-1,shortname=mixed,utf8,errors=remount-ro 0 0\n" +
//    "/dev/block/dm-2 /mnt/asec/zok.android.shapes-2 vfat ro,dirsync,nosuid,nodev,noexec,relatime,uid=1000,fmask=0222,dmask=0222,codepage=cp437,iocharset=iso8859-1,shortname=mixed,utf8,errors=remount-ro 0 0\n" +
//    "/dev/block/dm-3 /mnt/asec/net.hexage.everlands-1 vfat ro,dirsync,nosuid,nodev,noexec,relatime,uid=1000,fmask=0222,dmask=0222,codepage=cp437,iocharset=iso8859-1,shortname=mixed,utf8,errors=remount-ro 0 0\n" +
//    "/dev/block/dm-3 /mnt/sdcard/maps ext2 ro,relatime,size=0k,mode=000 0 0\n";

  public static void reset() {
    defaultStorage = null;
    mountPoints = new TreeMap<String, MountPoint>();
    init = false;
  }
}
