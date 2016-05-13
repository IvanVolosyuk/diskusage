package com.google.android.diskusage.datasource.fast;

import java.io.File;
import android.annotation.TargetApi;
import android.os.Build;
import android.os.Environment;
import com.google.android.diskusage.datasource.Env;

public class EnvImpl implements Env {

  @Override
  public File getExternalStorageDirectory() {
    return Environment.getExternalStorageDirectory();
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  @Override
  public boolean isExternalStorageEmulated(File base) {
    return Environment.isExternalStorageEmulated(base);
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  @Override
  public boolean isExternalStorageRemovable(File dir) {
    return Environment.isExternalStorageRemovable(dir);
  }
}
