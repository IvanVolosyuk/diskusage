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

package com.volosyukivan.diskusage.filesystem.mnt;

import android.content.Context;
import com.volosyukivan.diskusage.utils.IOHelper;
import timber.log.Timber;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RootMountPoint extends MountPoint {
  // private final String fsType;

  RootMountPoint(String root, String fsType) {
    super(root, root, false);
    // this.fsType = fsType;
  }

  @Override
  public boolean isRootRequired() {
    return true;
  }

  @Override
  public boolean hasApps() {
    return false;
  }

  @Override
  public boolean isDeleteSupported() {
    return false;
  }

  @Override
  public String getKey() {
      return "rooted:" + getRoot();
  }

  private static List<MountPoint> rootedMountPoints = new ArrayList<>();
  private static Map<String, MountPoint> rootedMountPointForKey = new HashMap<>();
  private static boolean init = false;
  public static int checksum = 0;


  public static List<MountPoint> getRootedMountPoints(Context context) {
    initMountPoints(context);
    return rootedMountPoints;
  }

  public static MountPoint getForKey(Context context, String key) {
    initMountPoints(context);
    return rootedMountPointForKey.get(key);
  }

  public static void initMountPoints(Context context) {
    if (init) return;
    init = true;

    try {
      checksum = 0;
      BufferedReader reader = IOHelper.getProcMountsReader();
      String line;
      while ((line = reader.readLine()) != null) {
        checksum += line.length();
        Timber.d("RootMountPoint.initMountPoints(): Line: %s", line);
        String[] parts = line.split(" +");
        if (parts.length < 3) continue;
        String mountPoint = parts[1];
        Timber.d("RootMountPoint.initMountPoints(): Mount point: " + mountPoint);
        String fsType = parts[2];

        if (!mountPoint.startsWith("/mnt/asec/")) {
          MountPoint m = new RootMountPoint(mountPoint, fsType);
          rootedMountPoints.add(m);
          rootedMountPointForKey.put(m.getKey(), m);
        }
      }
      reader.close();
    } catch (Exception e) {
      Timber.e(e, "RootMountPoint.initMountPoints(): Failed to get mount points");
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
    rootedMountPoints = new ArrayList<>();
    rootedMountPointForKey = new HashMap<>();
    init = false;
  }
}
