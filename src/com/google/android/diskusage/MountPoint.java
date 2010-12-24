package com.google.android.diskusage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;

import android.os.Environment;
import android.util.Log;

import com.google.android.diskusage.FileSystemEntry.ExcludeFilter;

public class MountPoint {
  FileSystemEntry.ExcludeFilter excludeFilter;
  String root;
  boolean hasApps2SD;
  
  MountPoint(String root, ExcludeFilter excludeFilter) {
    this.root = root;
    this.excludeFilter = excludeFilter;
  }
  MountPoint(String root, ExcludeFilter excludeFilter, boolean hasApps2SD) {
    this.root = root;
    this.excludeFilter = excludeFilter;
    this.hasApps2SD = hasApps2SD;
  }
  
  private static MountPoint defaultStorage;
  private static Map<String, MountPoint> mountPoints = new TreeMap<String, MountPoint>();
  private static boolean init = false;
  static int checksum = 0; 
  
  public static MountPoint getDefaultStorage() {
    initMountPoints();
    return defaultStorage;
  }
  
  public static Map<String,MountPoint> getMountPoints() {
    initMountPoints();
    return mountPoints;
  }
  
  public static boolean hasMultiple() {
    initMountPoints();
    return mountPoints.size() != 1;
  }

  public FileSystemEntry.ExcludeFilter getExcludeFilter() {
    return excludeFilter;
  }
  
  public String getRoot() {
    return root;
  }
  
  private static void initMountPoints() {
    if (init) return;
    init = true;
    String storagePath = Environment.getExternalStorageDirectory().getAbsolutePath();
    ArrayList<String> mountPointsList = new ArrayList<String>();
    HashSet<String> excludePoints = new HashSet<String>();
    mountPointsList.add(storagePath);
    defaultStorage = new MountPoint(storagePath, null);
    mountPoints.put(storagePath, defaultStorage);
    String storagePrefix = storagePath + "/";
    
    try {
      // FIXME: debug
      checksum = 0;
      BufferedReader reader = new BufferedReader(new FileReader("/proc/mounts"));
      String line;
      while ((line = reader.readLine()) != null) {
        checksum += line.length();
        String[] parts = line.split(" +");
        if (parts.length < 3) continue;
        String mountPoint = parts[1];
        String fsType = parts[2];
        if (mountPoint.startsWith(storagePrefix) || mountPoint.startsWith("/mnt/")) {
          if (!fsType.equals("vfat") || mountPoint.startsWith("/mnt/asec")
              || mountPoint.startsWith("/mnt/secure")) {
            excludePoints.add(mountPoint);
          } else {
            mountPointsList.add(mountPoint);
          }
        }
      }
      
      for (String mountPoint: mountPointsList) {
        String prefix = mountPoint + "/";
        boolean has_apps2sd = false;
        ArrayList<String> excludes = new ArrayList<String>();
        String mountPointName = new File(mountPoint).getName();
        
        for (String otherMountPoint : mountPointsList) {
          if (otherMountPoint.startsWith(prefix)) {
            excludes.add(mountPointName + "/" + otherMountPoint.substring(prefix.length()));
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
        mountPoints.put(mountPoint, new MountPoint(mountPoint, new ExcludeFilter(excludes), has_apps2sd));
      }
      if (!mountPoints.isEmpty()) {
        defaultStorage = mountPoints.values().iterator().next();
      }
    } catch (Exception e) {
      Log.e("diskusage", "Failed to get mount points", e);
    }
  }
  
  private static final String file = 
    "rootfs / rootfs ro,relatime 0 0\n" +
    "tmpfs /dev tmpfs rw,relatime,mode=755 0 0\n" +
    "devpts /dev/pts devpts rw,relatime,mode=600 0 0\n" +
    "proc /proc proc rw,relatime 0 0\n" +
    "sysfs /sys sysfs rw,relatime 0 0\n" +
    "none /acct cgroup rw,relatime,cpuacct 0 0\n" +
    "tmpfs /mnt/asec tmpfs rw,relatime,mode=755,gid=1000 0 0\n" +
    "none /dev/cpuctl cgroup rw,relatime,cpu 0 0\n" +
    "/dev/block/mtdblock3 /system yaffs2 ro,relatime 0 0\n" +
    "/dev/block/mtdblock5 /data yaffs2 rw,nosuid,nodev,relatime 0 0\n" +
    "/dev/block/mtdblock4 /cache yaffs2 rw,nosuid,nodev,relatime 0 0\n" +
    "/sys/kernel/debug /sys/kernel/debug debugfs rw,relatime 0 0\n" +
    "/dev/block/vold/179:1 /mnt/sdcard vfat rw,dirsync,nosuid,nodev,noexec,relatime,uid=1000,gid=1015,fmask=0702,dmask=0702,allow_utime=0020,codepage=cp437,iocharset=iso8859-1,shortname=mixed,utf8,errors=remount-ro 0 0\n" +
    "/dev/block/vold/179:1 /mnt/secure/asec vfat rw,dirsync,nosuid,nodev,noexec,relatime,uid=1000,gid=1015,fmask=0702,dmask=0702,allow_utime=0020,codepage=cp437,iocharset=iso8859-1,shortname=mixed,utf8,errors=remount-ro 0 0\n" +
    "tmpfs /mnt/sdcard/.android_secure tmpfs ro,relatime,size=0k,mode=000 0 0\n" +
    "/dev/block/dm-0 /mnt/asec/com.dasur.language.rus.pack-1 vfat ro,dirsync,nosuid,nodev,noexec,relatime,uid=1000,fmask=0222,dmask=0222,codepage=cp437,iocharset=iso8859-1,shortname=mixed,utf8,errors=remount-ro 0 0\n" +
    "/dev/block/dm-1 /mnt/asec/net.bytten.xkcdviewer-1 vfat ro,dirsync,nosuid,nodev,noexec,relatime,uid=1000,fmask=0222,dmask=0222,codepage=cp437,iocharset=iso8859-1,shortname=mixed,utf8,errors=remount-ro 0 0\n" +
    "/dev/block/dm-2 /mnt/asec/zok.android.shapes-2 vfat ro,dirsync,nosuid,nodev,noexec,relatime,uid=1000,fmask=0222,dmask=0222,codepage=cp437,iocharset=iso8859-1,shortname=mixed,utf8,errors=remount-ro 0 0\n" +
    "/dev/block/dm-3 /mnt/asec/net.hexage.everlands-1 vfat ro,dirsync,nosuid,nodev,noexec,relatime,uid=1000,fmask=0222,dmask=0222,codepage=cp437,iocharset=iso8859-1,shortname=mixed,utf8,errors=remount-ro 0 0\n" +
    "/dev/block/dm-3 /mnt/sdcard/maps ext2 ro,relatime,size=0k,mode=000 0 0\n";

  public static void reset() {
    defaultStorage = null;
    mountPoints = new TreeMap<String, MountPoint>();
    init = false;
  }
}
