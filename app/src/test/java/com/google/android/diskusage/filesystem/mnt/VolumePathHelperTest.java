package com.google.android.diskusage.filesystem.mnt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class VolumePathHelperTest {

  private static final String PKG = "com.google.android.diskusage";

  @Test
  public void stripsAppFilesSuffixForPrimaryStorage() {
    assertEquals("/storage/emulated/0",
        VolumePathHelper.stripAppFilesSuffix(
            "/storage/emulated/0/Android/data/" + PKG + "/files", PKG));
  }

  @Test
  public void stripsAppFilesSuffixForRemovableVolume() {
    assertEquals("/storage/1234-ABCD",
        VolumePathHelper.stripAppFilesSuffix(
            "/storage/1234-ABCD/Android/data/" + PKG + "/files", PKG));
  }

  @Test
  public void stripsAndroidDataWithoutMatchingPackage() {
    assertEquals("/storage/1234-ABCD",
        VolumePathHelper.stripAppFilesSuffix(
            "/storage/1234-ABCD/Android/data/other.app/files", PKG));
  }

  @Test
  public void keepsPlainVolumePath() {
    assertEquals("/storage/1234-ABCD",
        VolumePathHelper.stripAppFilesSuffix("/storage/1234-ABCD", PKG));
  }

  @Test
  public void handlesNull() {
    assertNull(VolumePathHelper.stripAppFilesSuffix(null, PKG));
    assertNull(VolumePathHelper.canonical(null));
  }

  @Test
  public void canonicalisesRelativeSegments() {
    assertEquals("/tmp", VolumePathHelper.canonical("/tmp/./"));
  }
}
