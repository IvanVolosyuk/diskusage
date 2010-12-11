package com.google.android.diskusage;

public class DiskUsageInternal extends DiskUsage {
  public MountPoint getMountPoint() {
    return MountPoint.getInternalStorage();
  }
}
