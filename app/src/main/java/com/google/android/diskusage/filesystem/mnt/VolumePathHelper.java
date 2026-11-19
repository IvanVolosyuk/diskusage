/*
 * DiskUsage - displays sdcard usage on android.
 * Copyright (C) 2008-2011 Ivan Volosyuk
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 */

package com.google.android.diskusage.filesystem.mnt;

import java.io.File;

/**
 * Pure-Java path helpers used by {@link MountPoint}. They are free of Android
 * dependencies so that the volume-path handling is covered by JVM unit tests.
 */
public final class VolumePathHelper {

  private VolumePathHelper() {
  }

  /**
   * Turns {@code /storage/1234-ABCD/Android/data/<pkg>/files} into
   * {@code /storage/1234-ABCD}. Returns {@code null} for {@code null} input.
   */
  public static String stripAppFilesSuffix(String path, String packageName) {
    if (path == null) return null;
    if (packageName != null) {
      String suffix = "/Android/data/" + packageName + "/files";
      if (path.endsWith(suffix)) {
        return path.substring(0, path.length() - suffix.length());
      }
    }
    int idx = path.indexOf("/Android/data/");
    if (idx > 0) return path.substring(0, idx);
    return path;
  }

  /**
   * Stable comparison key for volume paths. Two paths pointing at the same
   * directory (e.g. {@code /sdcard} and {@code /storage/emulated/0}) must not be
   * listed twice.
   */
  public static String canonical(String path) {
    if (path == null) return null;
    try {
      return new File(path).getCanonicalPath();
    } catch (Exception e) {
      return path;
    }
  }
}
