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
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

import com.google.android.diskusage.entity.FileSystemEntry;
import com.google.android.diskusage.entity.FileSystemEntry.ExcludeFilter;
import com.google.android.diskusage.entity.FileSystemRoot;

public class MountPoint {
  final FileSystemEntry.ExcludeFilter excludeFilter;
  String title;
  final String root;
  final boolean hasApps2SD;
  final boolean rootRequired;
  final String fsType;

  MountPoint(String title, String root, ExcludeFilter excludeFilter,
      boolean hasApps2SD, boolean rootRequired, String fsType) {
    this.title = title;
    this.root = root;
    this.excludeFilter = excludeFilter;
    this.hasApps2SD = hasApps2SD;
    this.rootRequired = rootRequired;
    this.fsType = fsType;
  }

  private static MountPoint defaultStorage;
  private static Map<String, MountPoint> mountPoints = new TreeMap<String, MountPoint>();
  private static Map<String, MountPoint> rootedMountPoints = new TreeMap<String, MountPoint>();
  private static boolean init = false;
  private static MountPoint honeycombSdcard;
  static int checksum = 0;

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof MountPoint)) {
      return false;
    }
    MountPoint other = (MountPoint) o;
    return (other.rootRequired == rootRequired) && (other.root.equals(root));
  }

  @Override
  public int hashCode() {
    return root.hashCode() * (rootRequired ? 13 : 31);
  }

  @Override
  public String toString() {
    return root + (rootRequired ? " (root)" : "");
  }

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

  public static String canonicalPath(File file) {
    try {
      return file.getCanonicalPath();
    } catch (Exception e) {
      return file.getAbsolutePath();
    }
  }

  public static String storageCardPath() {
    try {
      return canonicalPath(Environment.getExternalStorageDirectory());
    } catch (Exception e) {
      return Environment.getExternalStorageDirectory().getAbsolutePath();
    }
  }

  @TargetApi(Build.VERSION_CODES.KITKAT)
  public static File[] getMediaStoragePaths(Context context) {
    try {
      return context.getExternalFilesDirs(Environment.DIRECTORY_DCIM);
    } catch (Throwable t) {
      return new File[0];
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

    Set<MountPoint> mountPointsList = new HashSet<MountPoint>();
    HashSet<String> excludePoints = new HashSet<String>();
    if (storagePath != null) {
      defaultStorage = new MountPoint(
              titleStorageCard(context), storagePath, null, false, false, "fuse");
      mountPointsList.add(defaultStorage);
      mountPoints.put(storagePath, defaultStorage);
    }

    BufferedReader reader = null;
    try {
      // FIXME: debug
      checksum = 0;
      reader = new BufferedReader(new FileReader("/proc/mounts"));
//      BufferedReader reader = new BufferedReader(new InputStreamReader(context.getResources().openRawResource(R.raw.mounts_honeycomb), "UTF-8"));
      String line;
      while ((line = reader.readLine()) != null) {
        checksum += line.length();
        Log.d("diskusage", "line: " + line);
        String[] parts = line.split(" +");
        if (parts.length < 3) continue;
        String mountPoint = parts[1];
        Log.d("diskusage", "Mount point: " + mountPoint);
        String fsType = parts[2];

        StatFs stat = null;
        try {
          stat = new StatFs(mountPoint);
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
//          if (mountPoint.equals(storagePath)) {
//            mountPointsList.remove(defaultStorage);
//            mountPoints.remove(mountPoint);
//          }
          if (/*rooted &&*/ !mountPoint.startsWith("/mnt/asec/")) {
            mountPointsList.add(new MountPoint(
                mountPoint, mountPoint, null, false, true, fsType));
          }
        } else {
          Log.d("diskusage", "Mount point is good");
          mountPointsList.add(new MountPoint(
              mountPoint, mountPoint, null, false, false, fsType));
        }
      }
    } catch (Exception e) {
      Log.e("diskusage", "Failed to get mount points", e);
    } finally {
      if (reader != null) {
        try {
          reader.close();
        } catch (IOException e) {
        }
      }
    }

    addMountPointsCorresponding(mountPointsList, getMediaStoragePaths(context));

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
          has_apps2sd, mountPoint.rootRequired, mountPoint.fsType);
      if (mountPoint.rootRequired) {
        rootedMountPoints.put(mountPoint.root, newMountPoint);
      } else {
        mountPoints.put(mountPoint.root, newMountPoint);
      }
    }

    final int sdkVersion = Integer.parseInt(Build.VERSION.SDK);

    MountPoint storageCard = mountPoints.get(storageCardPath());
    if(sdkVersion >= Build.VERSION_CODES.HONEYCOMB
        && (storageCard == null || isEmulated(storageCard.fsType))) {
      mountPoints.remove(storageCardPath());
      // No real /sdcard in honeycomb
      honeycombSdcard = defaultStorage;
      mountPoints.put("/data", new MountPoint(
              titleStorageCard(context), "/data", null, false, false, ""));
    }

    if (!mountPoints.isEmpty()) {
      defaultStorage = mountPoints.values().iterator().next();
      defaultStorage.title = titleStorageCard(context);
    } else if (mountPoints.size() == 1) {
      mountPoints.entrySet().iterator().next().getValue().title =
          titleStorageCard(context);
    }
  }

  private static void addMountPointsCorresponding(
      Set<MountPoint> mountPointList, File[] mediaStoragePaths) {
    for (File file : mediaStoragePaths) {
      while (true) {
        String canonical = canonicalPath(file);
          MountPoint rootedMountPoint = new MountPoint(
              canonical,
              canonical,
              null,
              false,
              true,
              "fuse");
          MountPoint mountPoint = new MountPoint(
              canonical,
              canonical,
              null,
              false,
              false,
              "fuse");
        if (mountPointList.contains(mountPoint)) {
          break;
        } else if (mountPointList.contains(rootedMountPoint)) {
          mountPointList.add(mountPoint);
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
