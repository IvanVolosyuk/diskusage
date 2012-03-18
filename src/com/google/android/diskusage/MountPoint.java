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
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import com.google.android.diskusage.entity.FileSystemEntry;
import com.google.android.diskusage.entity.FileSystemEntry.ExcludeFilter;

public class MountPoint {
  FileSystemEntry.ExcludeFilter excludeFilter;
  String root;
  boolean hasApps2SD;
  boolean rootRequired;
  
  MountPoint(String root, ExcludeFilter excludeFilter, boolean hasApps2SD, boolean rootRequired) {
    this.root = root;
    this.excludeFilter = excludeFilter;
    this.hasApps2SD = hasApps2SD;
    this.rootRequired = rootRequired;
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
    return Environment.getExternalStorageDirectory().getAbsolutePath();
  }
  
  private static void initMountPoints(Context context) {
    if (init) return;
    init = true;
    String storagePath = storageCardPath();
    ArrayList<MountPoint> mountPointsList = new ArrayList<MountPoint>();
    HashSet<String> excludePoints = new HashSet<String>();
    if (storagePath != null) {
      defaultStorage = new MountPoint(storagePath, null, false, false); 
      mountPointsList.add(defaultStorage);
      mountPoints.put(storagePath, defaultStorage);
    }
    
    boolean rooted = new File("/system/bin/su").isFile()
                 || new File("/system/xbin/su").isFile();
    
    try {
      // FIXME: debug
      checksum = 0;
      BufferedReader reader = new BufferedReader(new FileReader("/proc/mounts"));
//      BufferedReader reader = new BufferedReader(new InputStreamReader(context.getResources().openRawResource(R.raw.mounts_honeycomb), "UTF-8"));
      String line;
      while ((line = reader.readLine()) != null) {
        checksum += line.length();
        String[] parts = line.split(" +");
        if (parts.length < 3) continue;
        String mountPoint = parts[1];
        String fsType = parts[2];
        if (!fsType.equals("vfat") || mountPoint.startsWith("/mnt/asec")
            || mountPoint.startsWith("/mnt/secure") || mountPoint.startsWith("/data/mac")) {
          excludePoints.add(mountPoint);
          
          // Default storage is not vfat, removing it (real honeycomb)
          if (mountPoint.equals(storagePath)) {
            mountPointsList.remove(defaultStorage);
            mountPoints.remove(mountPoint);
          }
          if (rooted && !mountPoint.startsWith("/mnt/asec/")) {
            mountPointsList.add(new MountPoint(mountPoint, null, false, true));
          }
        } else {
          mountPointsList.add(new MountPoint(mountPoint, null, false, false));
        }
      }
      
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
            mountPoint.root, new ExcludeFilter(excludes),
            has_apps2sd, mountPoint.rootRequired);
        if (mountPoint.rootRequired) {
          rootedMountPoints.put(mountPoint.root, newMountPoint);
        } else {
          mountPoints.put(mountPoint.root, newMountPoint);
        }
      }
    } catch (Exception e) {
      Log.e("diskusage", "Failed to get mount points", e);
    }
    final int sdkVersion = Integer.parseInt(Build.VERSION.SDK);
    
    if(sdkVersion >= Build.VERSION_CODES.HONEYCOMB
        && mountPoints.get(storageCardPath()) == null) {
      // No real /sdcard in honeycomb
      honeycombSdcard = defaultStorage;
      mountPoints.put("/data", new MountPoint("/data", null, false, false));
    }

    if (!mountPoints.isEmpty()) {
      defaultStorage = mountPoints.values().iterator().next();
    }
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
