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
import android.os.Bundle;
import android.os.Handler;
import com.google.android.diskusage.R;
import com.google.android.diskusage.filesystem.entity.FileSystemEntry;
import com.google.android.diskusage.filesystem.entity.FileSystemPackage;
import com.google.android.diskusage.filesystem.entity.FileSystemSuperRoot;
import com.google.android.diskusage.ui.DiskUsage.AfterLoad;
import com.google.android.diskusage.ui.common.ScanProgressDialog;
import timber.log.Timber;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import splitties.toast.ToastKt;

public abstract class LoadableActivity extends Activity {
  FileSystemPackage pkg_removed;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    FileSystemEntry.setupStrings(this);
  }

  public abstract String getKey();

  abstract FileSystemSuperRoot scan() throws IOException, InterruptedException;

  public static class PersistantActivityState {
    FileSystemSuperRoot root;
    AfterLoad afterLoad;
    public ScanProgressDialog loading;
  }

  private static final Map<String, PersistantActivityState> persistantActivityState =
          new TreeMap<>();

  public static void resetStoredStates() {
    persistantActivityState.clear();
  }


  // FIXME: use it wisely
 static boolean forceCleanup() {
   boolean success = false;
   for (PersistantActivityState state : persistantActivityState.values()) {
     if (state.afterLoad == null && state.root != null) {
       state.root = null;
       success = true;
     }
   }
   return success;
 }

  public PersistantActivityState getPersistantState() {
    String key = getKey();

    PersistantActivityState state = persistantActivityState.get(key);
    if (state != null) return state;
    state = new PersistantActivityState();
    persistantActivityState.put(key, state);
    return state;
  }

  void LoadFiles(final LoadableActivity activity,
      final AfterLoad runAfterLoad, boolean force) {
    boolean scanRunning;
    final PersistantActivityState state = getPersistantState();
    Timber.d("LoadableActivity.LoadFiles(), afterLoad = %s", runAfterLoad);

    if (force) {
      state.root = null;
    }

    if (state.root != null) {
      runAfterLoad.run(state.root, true);
      return;
    }

    scanRunning = state.afterLoad != null;
    state.afterLoad = runAfterLoad;
    Timber.d("LoadableActivity.LoadFiles(): Created new progress dialog");
    state.loading = new ScanProgressDialog(activity);

    final ScanProgressDialog thisLoading = state.loading;
    state.loading.setOnCancelListener(dialog -> {
      state.loading = null;
      activity.finish();
    });
    thisLoading.setCancelable(true);
//    thisLoading.setIndeterminate(true);
    thisLoading.setMax(1);
    thisLoading.setMessage(activity.getString(R.string.scaning_directories));
    thisLoading.show();

    if (scanRunning) return;
    final Handler handler = new Handler();

    new Thread() {
      @Override
      public void run() {
        String error;
        try {
          Timber.d("LoadableActivity.LoadFiles(): Running scan for %s", getKey());
          final FileSystemSuperRoot newRoot = scan();

          handler.post(() -> {
            if (state.loading == null) {
              Timber.d("LoadableActivity.LoadFiles(): No dialog, doesn't run afterLoad");
              state.afterLoad = null;
              if (newRoot.children[0].children != null) {
                Timber.d("LoadableActivity.LoadFiles(): No dialog, updating root still");
                state.root = newRoot;
              }
              return;
            }
            if (state.loading.isShowing()) state.loading.dismiss();
            state.loading = null;
            AfterLoad afterLoadCopy = state.afterLoad;
            state.afterLoad = null;
            Timber.d("LoadableActivity.LoadFiles(): Dismissed dialog");

            if (newRoot.children[0].children == null) {
              Timber.d("LoadableActivity.LoadFiles(): Empty card");
              handleEmptySDCard(activity, runAfterLoad);
              return;
            }
            state.root = newRoot;
            pkg_removed = null;
            Timber.d("LoadableActivity.LoadFiles(): Run afterLoad = %s", afterLoadCopy);
            afterLoadCopy.run(state.root, false);
          });
          return;
        } catch (final OutOfMemoryError e) {
          state.root = null;
          state.afterLoad = null;
          Timber.d("LoadableActivity.LoadFiles(): Out of memory!");
          handler.post(() -> {
            if (state.loading == null) return;
            state.loading.dismiss();
            handleOutOfMemory(activity);
          });
          return;
        } catch (InterruptedException | IOException | RuntimeException e) {
          error = e.getClass().getName() + ":" + e.getMessage();
          Timber.e("LoadableActivity.LoadFiles(): Native error", e);
        } catch (final StackOverflowError e) {
          error = "Filesystem is damaged.";
        }
        final String finalError = error;
        state.root = null;
        state.afterLoad = null;
        Timber.d("LoadableActivity.LoadFiles(): Exception in scan!");
        handler.post(() -> {
          if (state.loading == null) return;
          state.loading.dismiss();
          new AlertDialog.Builder(activity)
          .setTitle(finalError)
          .setOnCancelListener(dialog -> activity.finish())
          .show();
        });
      }
    }.start();
  }

  @Override
  protected void onPause() {
    PersistantActivityState state = getPersistantState();
    if (state.loading != null) {
      if (state.loading.isShowing()) state.loading.dismiss();
      Timber.d("LoadableActivity.onPause(): Removed progress dialog");
      state.loading = null;
    }
    super.onPause();
  }

  private void handleEmptySDCard(final LoadableActivity activity,
      final AfterLoad afterLoad) {
    new AlertDialog.Builder(activity)
    .setTitle(activity.getString(R.string.empty_or_missing_sdcard))
    .setPositiveButton(activity.getString(R.string.button_rescan), (dialog, which) -> {
      if (afterLoad == null)
        throw new RuntimeException("LoadableActivity.handleEmptySDCard(): afterLoad is empty");
      LoadFiles(activity, afterLoad, true);
    })
    .setOnCancelListener(dialog -> activity.finish()).create().show();
  }

  private static void handleOutOfMemory(final Activity activity) {
    try {
      // Can fail if the main window is already closed.
      new AlertDialog.Builder(activity)
      .setTitle(activity.getString(R.string.out_of_memory))
      .setOnCancelListener(dialog -> activity.finish()).create().show();
    } catch (Throwable t) {
      ToastKt.toast("DiskUsage is out of memory. Sorry.");
    }
  }
}
