package com.google.android.diskusage.datasource;

import java.io.File;

import android.annotation.TargetApi;
import android.os.Build;

public interface Env {
  File getExternalStorageDirectory();

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  boolean isExternalStorageEmulated(File dir);
  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  boolean isExternalStorageRemovable(File dir);

}
