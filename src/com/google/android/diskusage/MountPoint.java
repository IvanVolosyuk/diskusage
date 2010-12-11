package com.google.android.diskusage;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.TreeMap;

import android.os.Environment;
import android.util.Log;

import com.google.android.diskusage.FileSystemEntry.ExcludeFilter;

public class MountPoint {
  FileSystemEntry.ExcludeFilter excludeFilter;
  String root;
  
  MountPoint(String root, ExcludeFilter excludeFilter) {
    this.root = root;
    this.excludeFilter = excludeFilter;
  }
  
  public static MountPoint getInternalStorage() {
    initMountPoints();
    return internalStorage;
  }
  
  public static MountPoint getExternalStorage() {
    initMountPoints();
    return externalStorage;
  }
  
  public FileSystemEntry.ExcludeFilter getExcludeFilter() {
    return excludeFilter;
  }
  
  public String getRoot() {
    return root;
  }
  
  private static MountPoint externalStorage;
  private static MountPoint internalStorage;
  private static boolean init = false;
  
  private static void initMountPoints() {
    if (init) return;
    init = true;
    String storagePath = Environment.getExternalStorageDirectory().getAbsolutePath();
    ArrayList<String> mountPoints = new ArrayList<String>();
    HashSet<String> excludePoints = new HashSet<String>();
    mountPoints.add(storagePath);
    externalStorage = new MountPoint(storagePath, null);
    String storagePrefix = storagePath + "/";
    
    try {
      // FIXME: debug
      BufferedReader reader = new BufferedReader(new FileReader("/proc/mounts"));
//      BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(file.getBytes())));
      String line;
      while ((line = reader.readLine()) != null) {
        String[] parts = line.split(" +");
        if (parts.length < 3) continue;
        String mountPoint = parts[1];
        String fsType = parts[2];
        if (mountPoint.startsWith(storagePrefix)) {
          if (fsType.equals("tmpfs")) {
            excludePoints.add(mountPoint);
          } else {
            mountPoints.add(mountPoint);
          }
        }
      }
      
      TreeMap<String, MountPoint> mounts = new TreeMap<String, MountPoint>();
      for (String mountPoint: mountPoints) {
        String prefix = mountPoint + "/";
        ArrayList<String> excludes = new ArrayList<String>();
        String mountPointName = new File(mountPoint).getName();
        
        for (String otherMountPoint : mountPoints) {
          if (otherMountPoint.startsWith(prefix)) {
            excludes.add(mountPointName + "/" + otherMountPoint.substring(prefix.length()));
          }
        }
        for (String otherMountPoint : excludePoints) {
          if (otherMountPoint.startsWith(prefix)) {
            excludes.add(mountPointName + "/" + otherMountPoint.substring(prefix.length()));
          }
        }
        mounts.put(mountPoint, new MountPoint(mountPoint, new ExcludeFilter(excludes)));
      }
      MountPoint defaultStorage = mounts.remove(storagePath);
      if (mounts.size() == 0) {
        externalStorage = defaultStorage;
      } else if (mounts.size() == 1) {
        internalStorage = defaultStorage;
        externalStorage = mounts.values().iterator().next();
      } else {
        internalStorage = defaultStorage;
        externalStorage = mounts.values().iterator().next();
        // FIXME: support for more than one external storage missing
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
}
