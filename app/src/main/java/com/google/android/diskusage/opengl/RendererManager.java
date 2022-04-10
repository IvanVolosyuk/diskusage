package com.google.android.diskusage.opengl;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.View;
import com.google.android.diskusage.ui.FileSystemState;
import com.google.android.diskusage.datasource.DataSource;
import com.google.android.diskusage.filesystem.entity.FileSystemSuperRoot;
import com.google.android.diskusage.ui.DiskUsage;
import com.google.android.diskusage.ui.FileSystemViewCPU;

public class RendererManager {
  private static final String HW_RENDERER = "hw_renderer";

  private final DiskUsage diskusage;
  private boolean hwRenderer;
  private boolean rendererChanged = false;

  private SharedPreferences getPrefs() {
    return diskusage.getSharedPreferences("settings", Context.MODE_PRIVATE);
  }

  public RendererManager(DiskUsage diskusage) {
    this.diskusage = diskusage;
  }

  public boolean hardwareRendererByDefault() {
    final int sdkVersion = DataSource.get().getAndroidVersion();
    return sdkVersion >= Build.VERSION_CODES.GINGERBREAD;
  }

  public boolean isHardwareRendererSupported() {
    final int sdkVersion = DataSource.get().getAndroidVersion();
    if (android.os.Build.DEVICE.equals("bravo")) {
      return sdkVersion < Build.VERSION_CODES.ECLAIR
              || sdkVersion > Build.VERSION_CODES.GINGERBREAD;
    }
    return true;
  }

  public boolean warnAboutIncompatibility() {
    final int sdkVersion = DataSource.get().getAndroidVersion();
    return sdkVersion <= Build.VERSION_CODES.GINGERBREAD;
  }

  public void switchRenderer(final FileSystemSuperRoot root) {
    diskusage.fileSystemState.killRenderThread();
    if (hwRenderer) {
      finishRendererSwitch(root);
      return;
    }

    if (warnAboutIncompatibility()) {
      new AlertDialog.Builder(diskusage)
      .setCancelable(true)
      .setIcon(android.R.drawable.ic_dialog_alert)
      .setTitle("WARNING!")
      .setMessage("Hardware renderer may CRASH your PHONE.\n\n" +
      "There is a firmware bug in a number of HTC phones with Android 2.2 (Froyo).")
      .setPositiveButton("Proceeed", (dialog, which) -> finishRendererSwitch(root))
      .setNegativeButton(android.R.string.cancel, null).create().show();
      return;
    }
    finishRendererSwitch(root);
  }

  public void finishRendererSwitch(FileSystemSuperRoot root) {
    hwRenderer = !hwRenderer;
    rendererChanged = true;
    makeView(diskusage.fileSystemState, root);
  }

  public void makeView(
          FileSystemState eventHandler, FileSystemSuperRoot root) {
    View view;
    if (hwRenderer) {
      view = new FileSystemViewGPU(diskusage, eventHandler);
    } else {
      view = new FileSystemViewCPU(diskusage, eventHandler);
    }
    diskusage.menu.wrapAndSetContentView(view, root);
    view.requestFocus();
  }


  public void onResume() {
    if (isHardwareRendererSupported()) {
      hwRenderer = getPrefs().getBoolean(HW_RENDERER,
          hardwareRendererByDefault());
    }
  }

  public void onPause() {
    if (rendererChanged) {
      getPrefs().edit().putBoolean(HW_RENDERER, hwRenderer).apply();
    }
  }
}
