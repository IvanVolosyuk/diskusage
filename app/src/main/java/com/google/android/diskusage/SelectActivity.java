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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.diskusage.datasource.DataSource;
import com.google.android.diskusage.datasource.DebugDataSourceBridge;
import com.google.android.diskusage.datasource.PortableFile;
import com.google.android.diskusage.datasource.fast.DefaultDataSource;
import com.google.android.diskusage.entity.FileSystemEntry;

public class SelectActivity extends Activity {
  private AlertDialog dialog;
  Map<String,Bundle> bundles = new TreeMap<String,Bundle>();
  ArrayList<Runnable> actionList = new ArrayList<Runnable>();
//  private static boolean debugUnhidden = true;
  private static boolean debugLoadedDump = false;
  private DataSource debugDataSource;
  private static DebugDataSourceBridge debugDataSourceBridge;

  static {
    try {
      Class<?> clazz = Class.forName(
          "com.google.android.diskusage.datasource.debug.DebugDataSourceBridgeImpl");
      debugDataSourceBridge = (DebugDataSourceBridge) clazz.newInstance();
    } catch (Throwable t) {
      Log.d("diskusage", "Debug is disabled");
    }
  }


  private abstract class AbstractUsageAction implements Runnable {
    public void runAction(String key, String title, String rootKey, Class<?> viewer) {
      Intent i = new Intent(SelectActivity.this, viewer);
      i.putExtra(DiskUsage.TITLE_KEY, title);
      i.putExtra(DiskUsage.ROOT_KEY, rootKey);
      i.putExtra(DiskUsage.KEY_KEY, key);
      Bundle bundle = bundles.get(key);
      if (bundle != null) {
        i.putExtra(DiskUsage.STATE_KEY, bundle);
      }
      startActivityForResult(i, 0);
    }
  };

  private class DiskUsageAction extends AbstractUsageAction {
    private final MountPoint mountPoint;

    DiskUsageAction(MountPoint mountPoint) {
      this.mountPoint = mountPoint;
    }

    public void run() {
      runAction(getKeyForStorage(mountPoint), mountPoint.title, mountPoint.root, DiskUsage.class);
    }
  };

  public static String getKeyForStorage(MountPoint mountPoint) {
    return "storage:" + mountPoint.root;
  }

  private class EnableDebugAction implements Runnable {
    public void run() {
      try {
        debugLoadedDump = false;
        debugDataSource = debugDataSourceBridge.initNewDump(SelectActivity.this);
        DataSource.override(debugDataSource);
        dialog.hide();
        MountPoint.reset();
        makeDialog();
      } catch (IOException e) {
        Log.d("diskusage", "Failed to enable debug", e);
        Toast.makeText(
            SelectActivity.this,
            "Failed to enable debug " + e.getMessage(),
            Toast.LENGTH_LONG).show();
      }
    }
  }

  private class DisableDebug implements Runnable {
    @Override
    public void run() {
        DataSource.override(new DefaultDataSource());
        debugDataSource = null;
//        debugUnhidden = false;
        dialog.hide();
        MountPoint.reset();
        makeDialog();
    }

  }

  private class LoadDumpAction implements Runnable {
    public void run() {
      try {
        debugDataSource = debugDataSourceBridge.loadDefaultDump();
        debugLoadedDump = true;
        DataSource.override(debugDataSource);
        dialog.hide();
        MountPoint.reset();
        makeDialog();
      } catch (IOException e) {
        Log.d("diskusage", "Failed to enable debug", e);
        Toast.makeText(
            SelectActivity.this,
            "Failed to enable debug " + e.getMessage(),
            Toast.LENGTH_LONG).show();
      }
    }
  }

  private class SendBugReportAction implements Runnable {
    @Override
    public void run() {
      try {
        debugDataSourceBridge.saveDumpAndSendReport(
            debugDataSource, SelectActivity.this);
      } catch (IOException e) {
        Log.d("diskusage", "Failed to send bugreport", e);
        Toast.makeText(
            SelectActivity.this,
            "Failed to send bugreport: " + e.getMessage(),
            Toast.LENGTH_LONG).show();
      }
    }
  }

  public Handler handler = new Handler();

  public void makeDialog() {
    ArrayList<String> options = new ArrayList<String>();
    actionList.clear();

//    PortableFile[] fileDirs = DataSource.get().getExternalFilesDirs(this);
    for (MountPoint mountPoint : MountPoint.getMountPoints(this).values()) {
      options.add(mountPoint.title);
      actionList.add(new DiskUsageAction(mountPoint));
    }

    if (/*debugUnhidden && */debugDataSourceBridge != null) {
      if (debugDataSource != null && !debugLoadedDump) {
        options.add("* Send bug report");
        actionList.add(new SendBugReportAction());
      }

      if (debugDataSource == null) {
        options.add("* Enable debug dump");
        actionList.add(new EnableDebugAction());

        if (debugDataSourceBridge.dumpExist()) {
          options.add("* Load dump");
          actionList.add(new LoadDumpAction());
        }
      }
      options.add("* Disable debug");
      actionList.add(new DisableDebug());
    }

    final String[] optionsArray = options.toArray(new String[options.size()]);

    dialog = new AlertDialog.Builder(this)
    .setItems(optionsArray,
        new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        actionList.get(which).run();
      }
    })
    .setTitle("View")
    .setOnCancelListener(new OnCancelListener() {
      @Override
      public void onCancel(DialogInterface dialog) {
        finish();
      }
    }).create();
    dialog.show();
  }

  private static final int MY_PERMISSIONS_REQUEST_READ_STORAGE = 1;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    FileSystemEntry.setupStrings(this);
    setContentView(new TextView(this));

    if (this.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {

      // Should we show an explanation?
      if (this.shouldShowRequestPermissionRationale(
              Manifest.permission.READ_EXTERNAL_STORAGE)) {

        // Show an explanation to the user *asynchronously* -- don't block
        // this thread waiting for the user's response! After the user
        // sees the explanation, try again to request the permission.

      } else {

        // No explanation needed, we can request the permission.

        this.requestPermissions(
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                MY_PERMISSIONS_REQUEST_READ_STORAGE);
      }
    }
  }
  @Override
  public void onRequestPermissionsResult(int requestCode,
                                         String permissions[], int[] grantResults) {
    switch (requestCode) {
      case MY_PERMISSIONS_REQUEST_READ_STORAGE: {
        // If request is cancelled, the result arrays are empty.
        if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

          // permission was granted, yay! Do the
          // contacts-related task you need to do.
          Log.d("diskusage", "GRANTED READ storage");

        } else {
          Log.d("diskusage", "DENIED READ storage");

          // permission denied, boo! Disable the
          // functionality that depends on this permission.
        }
        return;
      }

      // other 'case' lines to check for other
      // permissions this app might request
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    makeDialog();
  }

  @Override
  protected void onPause() {
    if (dialog.isShowing()) dialog.dismiss();
    super.onPause();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (data == null) return;
    Bundle state = data.getBundleExtra(DiskUsage.STATE_KEY);
    String key = data.getStringExtra(DiskUsage.KEY_KEY);
    bundles.put(key, state);
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    for (Entry<String, Bundle> entry : bundles.entrySet()) {
      outState.putBundle(entry.getKey(), entry.getValue());
    }
    String[] keys = bundles.keySet().toArray(new String[0]);
    outState.putStringArray(BUNDLE_KEYS, keys);
  }

  @Override
  protected void onRestoreInstanceState(Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    for (String key : savedInstanceState.getStringArray(BUNDLE_KEYS)) {
      bundles.put(key, savedInstanceState.getBundle(key));
    }
  }

  private static final String BUNDLE_KEYS = "keys";
}