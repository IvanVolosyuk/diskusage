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

package com.google.android.diskusage.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.NonNull;
import com.google.android.diskusage.R;
import com.google.android.diskusage.databinding.ActivityCommonBinding;
import com.google.android.diskusage.filesystem.entity.FileSystemEntry;
import com.google.android.diskusage.filesystem.mnt.MountPoint;
import com.google.android.diskusage.filesystem.mnt.RootMountPoint;
import com.google.android.diskusage.utils.DeviceHelper;
import com.google.android.diskusage.utils.IOHelper;
import timber.log.Timber;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

public class SelectActivity extends Activity {
  private static final String BUNDLE_KEYS = "keys";
  private AlertDialog dialog;
  Map<String, Bundle> bundles = new TreeMap<>();
  ArrayList<Runnable> actionList = new ArrayList<>();
  private boolean expandRootMountPoints;

  private abstract class AbstractUsageAction implements Runnable {
    public void runAction(String key, Class<?> viewer) {
      Intent i = new Intent(SelectActivity.this, viewer);
      i.putExtra(DiskUsage.KEY_KEY, key);
      Bundle bundle = bundles.get(key);
      if (bundle != null) {
        i.putExtra(DiskUsage.STATE_KEY, bundle);
      }
      startActivityForResult(i, 0);
    }
  }

  private class DiskUsageAction extends AbstractUsageAction {
    private final MountPoint mountPoint;

    DiskUsageAction(MountPoint mountPoint) {
      this.mountPoint = mountPoint;
    }

    public void run() {
      runAction(mountPoint.getKey(), PermissionRequestActivity.class);
    }
  }

  private class ShowHideAction implements Runnable {
    public void run() {
      Intent i = new Intent(SelectActivity.this, ShowHideMountPointsActivity.class);
      startActivity(i);
    }
  }

  public Handler handler = new Handler();
  public Runnable checkForMountsUpdates = new Runnable() {
    @Override
    public void run() {
      boolean reload = false;
      try {
        BufferedReader reader = IOHelper.getProcMountsReader();
        String line;
        int checksum = 0;
        while ((line = reader.readLine()) != null) {
          checksum += line.length();
        }
        reader.close();
        if (checksum != RootMountPoint.checksum) {
          Timber.d("%s vs %s", checksum, RootMountPoint.checksum);
          reload = true;
        }
      } catch (Throwable ignored) {}

      if (reload) {
        dialog.hide();
        MountPoint.reset();
        makeDialog();
      }
      handler.postDelayed(this, 2000);
    }
  };


  public void makeDialog() {
    ArrayList<String> options = new ArrayList<>();
    actionList.clear();

//    PortableFile[] fileDirs = DataSource.get().getExternalFilesDirs(this);
    for (MountPoint mountPoint : MountPoint.getMountPoints(this)) {
      options.add(mountPoint.getTitle());
      actionList.add(new DiskUsageAction(mountPoint));
    }

    if (DeviceHelper.isDeviceRooted()) {
      SharedPreferences prefs =  getSharedPreferences("ignore_list", Context.MODE_PRIVATE);
      Map<String, ?> ignoreList = prefs.getAll();
      if (!ignoreList.keySet().isEmpty()) {
        Set<String> ignores = ignoreList.keySet();
        for (MountPoint mountPoint : RootMountPoint.getRootedMountPoints(this)) {
          if (ignores.contains(mountPoint.getRoot())) continue;
          options.add(mountPoint.getRoot());
          actionList.add(new DiskUsageAction(mountPoint));
        }
        options.add("[Show/hide]");
        actionList.add(new ShowHideAction());
      } else if (expandRootMountPoints) {
        for (MountPoint mountPoint : RootMountPoint.getRootedMountPoints(this)) {
          options.add(mountPoint.getRoot());
          actionList.add(new DiskUsageAction(mountPoint));
        }
        options.add("[Show/hide]");
        actionList.add(new ShowHideAction());
      } else {
        options.add("[Root required]");
        actionList.add(() -> {
          expandRootMountPoints = true;
          makeDialog();
        });

      }
    }

    final String[] optionsArray = options.toArray(new String[0]);

    dialog = new AlertDialog.Builder(this)
    .setItems(optionsArray,
            (dialog, which) -> actionList.get(which).run())
    .setTitle(R.string.ask_view)
    .setOnCancelListener(dialog -> finish()).create();
    /*try {
      if (debugDataSourceBridge != null) {
        dialog.getListView().setOnItemLongClickListener(
            new OnItemLongClickListener() {
          @Override
          public boolean onItemLongClick(
              AdapterView<?> arg0, View arg1, int arg2, long arg3) {
            debugUnhidden = true;
            dialog.hide();
            makeDialog();
            return true;
          }
        });
      }
    } catch (Throwable t) {
      // api 3
    }*/
    dialog.show();
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    FileSystemEntry.setupStrings(this);
    ActivityCommonBinding binding = ActivityCommonBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
//    ActionBar bar = getActionBar();
//    bar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_USE_LOGO);
  }

  @Override
  protected void onResume() {
    super.onResume();
//    ActionBar actionBar = getActionBar();
//    actionBar.setDisplayHomeAsUpEnabled(true);
    makeDialog();
    handler.post(checkForMountsUpdates);
  }

  @Override
  protected void onPause() {
    if (dialog.isShowing()) dialog.dismiss();
    handler.removeCallbacks(checkForMountsUpdates);
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
  protected void onSaveInstanceState(@NonNull Bundle outState) {
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
}
