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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import com.google.android.diskusage.R;
import com.google.android.diskusage.databinding.ActivityCommonBinding;
import com.google.android.diskusage.filesystem.entity.FileSystemEntry;
import com.google.android.diskusage.filesystem.mnt.MountPoint;
import com.google.android.diskusage.filesystem.mnt.RootMountPoint;
import com.google.android.diskusage.ui.common.EdgeToEdge;
import com.google.android.diskusage.utils.DeviceHelper;
import com.google.android.diskusage.utils.IOHelper;
import timber.log.Timber;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

/**
 * Entry point: lets the user pick the volume (or, with root, any mount point)
 * that should be analysed.
 */
public class SelectActivity extends AppCompatActivity {
  private static final String BUNDLE_KEYS = "keys";
  private AlertDialog dialog;
  final Map<String, Bundle> bundles = new TreeMap<>();
  final ArrayList<Runnable> actionList = new ArrayList<>();
  private boolean expandRootMountPoints;
  /** Guards against re-entering the graph in a loop when the user presses back. */
  private boolean autoSelected;
  /** True when the graph returned a state bundle (back on a multi-volume device). */
  private boolean childReturnedState;

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

  public final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable checkForMountsUpdates = new Runnable() {
    @Override
    public void run() {
      boolean reload = false;
      try (BufferedReader reader = IOHelper.getProcMountsReader()) {
        String line;
        int checksum = 0;
        while ((line = reader.readLine()) != null) {
          checksum += line.length();
        }
        if (checksum != RootMountPoint.checksum) {
          Timber.d("%s vs %s", checksum, RootMountPoint.checksum);
          reload = true;
        }
      } catch (Throwable ignored) {
      }

      if (reload) {
        dismissDialog();
        MountPoint.reset();
        makeDialog();
      }
      handler.postDelayed(this, 2000);
    }
  };

  private void dismissDialog() {
    if (dialog != null && dialog.isShowing()) {
      dialog.dismiss();
    }
    dialog = null;
  }

  public void makeDialog() {
    dismissDialog();

    ArrayList<String> options = new ArrayList<>();
    actionList.clear();

    for (MountPoint mountPoint : MountPoint.getMountPoints(this)) {
      options.add(mountPoint.getTitle());
      actionList.add(new DiskUsageAction(mountPoint));
    }

    if (DeviceHelper.isDeviceRooted()) {
      SharedPreferences prefs = getSharedPreferences("ignore_list", Context.MODE_PRIVATE);
      Map<String, ?> ignoreList = prefs.getAll();
      if (!ignoreList.keySet().isEmpty()) {
        Set<String> ignores = ignoreList.keySet();
        for (MountPoint mountPoint : RootMountPoint.getRootedMountPoints(this)) {
          if (ignores.contains(mountPoint.getRoot())) continue;
          options.add(mountPoint.getRoot());
          actionList.add(new DiskUsageAction(mountPoint));
        }
        options.add(getString(R.string.show_hide_mount_points));
        actionList.add(new ShowHideAction());
      } else if (expandRootMountPoints) {
        for (MountPoint mountPoint : RootMountPoint.getRootedMountPoints(this)) {
          options.add(mountPoint.getRoot());
          actionList.add(new DiskUsageAction(mountPoint));
        }
        options.add(getString(R.string.show_hide_mount_points));
        actionList.add(new ShowHideAction());
      } else {
        options.add(getString(R.string.root_required));
        actionList.add(() -> {
          expandRootMountPoints = true;
          makeDialog();
        });
      }
    }

    // Autoselect: on devices with a single storage volume the extra tap is
    // pointless. The dialog is only skipped on the first visit; after returning
    // from the graph the user explicitly gets the chooser.
    if (options.size() == 1 && bundles.isEmpty() && !autoSelected && !actionList.isEmpty()) {
      autoSelected = true;
      actionList.get(0).run();
      return;
    }

    final String[] optionsArray = options.toArray(new String[0]);

    dialog = new AlertDialog.Builder(this)
        .setItems(optionsArray, (d, which) -> {
          if (which >= 0 && which < actionList.size()) {
            actionList.get(which).run();
          }
        })
        .setTitle(R.string.ask_view)
        .setOnCancelListener(d -> finish())
        .create();
    dialog.show();
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    EdgeToEdge.enable(this);
    FileSystemEntry.setupStrings(this);
    ActivityCommonBinding binding = ActivityCommonBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
    EdgeToEdge.applyInsets(binding.getRoot());
    ViewCompat.setAccessibilityPaneTitle(binding.getRoot(), getString(R.string.app_name));
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (autoSelected && !childReturnedState) {
      // We entered a child screen and it went away without handing back a
      // state (scan cancelled, permission dialog dismissed, ...): back means
      // "leave the app", not "show the volume chooser again".
      finish();
      return;
    }
    makeDialog();
    handler.post(checkForMountsUpdates);
  }

  @Override
  protected void onPause() {
    // Coming back from the graph (or from a permission screen) must not
    // auto-open it again; show the chooser instead.
    autoSelected = true;
    dismissDialog();
    handler.removeCallbacks(checkForMountsUpdates);
    super.onPause();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (data == null) return;
    Bundle state = data.getBundleExtra(DiskUsage.STATE_KEY);
    String key = data.getStringExtra(DiskUsage.KEY_KEY);
    if (key != null && state != null) {
      bundles.put(key, state);
      childReturnedState = true;
    }
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
  protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    String[] keys = savedInstanceState.getStringArray(BUNDLE_KEYS);
    if (keys == null) return;
    for (String key : keys) {
      Bundle bundle = savedInstanceState.getBundle(key);
      if (bundle != null) {
        bundles.put(key, bundle);
      }
    }
  }
}
