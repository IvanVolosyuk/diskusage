package com.google.android.diskusage;

import java.util.Map;
import java.util.TreeMap;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.os.Handler;
import android.util.Log;

import com.google.android.diskusage.DiskUsage.AfterLoad;

public abstract class LoadableActivity extends Activity {
  FileSystemPackage pkg_removed;
  
  
  public abstract String getRootPath();
  public abstract String getRootTitle();
  
  abstract FileSystemEntry scan();
  
  class PersistantActivityState {
    FileSystemEntry root;
    AfterLoad afterLoad;
    ProgressDialog loading;
  };
  
  private static Map<String, PersistantActivityState> persistantActivityState =
    new TreeMap<String, PersistantActivityState>();
  
  protected PersistantActivityState getPersistantState() {
    String key = getRootPath();
    
    PersistantActivityState state = persistantActivityState.get(key);
    if (state != null) return state;
    state = new PersistantActivityState();
    persistantActivityState.put(key, state);
    return state;
  }

  void LoadFiles(final LoadableActivity activity,
      final AfterLoad runAfterLoad, boolean force) {
    boolean scanRunning = false;
    final PersistantActivityState state = getPersistantState();
    Log.d("diskusage", "LoadFiles, afterLoad = " + runAfterLoad);
    
    if (force) {
      state.root = null;
    }

    if (state.root != null) {
      runAfterLoad.run(state.root, true);
      return;
    }

    scanRunning = state.afterLoad != null;
    state.afterLoad = runAfterLoad;
    Log.d("diskusage", "created new progress dialog");
    state.loading = new ProgressDialog(activity);
    final ProgressDialog thisLoading = state.loading;
    state.loading.setOnCancelListener(new OnCancelListener() {
      @Override
      public void onCancel(DialogInterface dialog) {
        state.loading = null;
        activity.finish();
      }
    });
    thisLoading.setCancelable(true);
    thisLoading.setIndeterminate(true);
    thisLoading.setMessage(activity.getString(R.string.scaning_directories));
    thisLoading.show();

    if (scanRunning) return;
    final Handler handler = new Handler();

    new Thread() {
      @Override
      public void run() {
        try {
          Log.d("diskusage", "running scan for " + getRootPath());
          final FileSystemEntry newRoot = scan();

          handler.post(new Runnable() {
            public void run() {
              if (state.loading == null) {
                Log.d("diskusage", "no dialog, doesn't run afterLoad");
                state.afterLoad = null;
                if (newRoot.children[0].children != null) {
                  Log.d("diskusage", "no dialog, updating root still");
                  state.root = newRoot;
                }
                return;
              }
              if (state.loading.isShowing()) state.loading.dismiss();
              state.loading = null;
              AfterLoad afterLoadCopy = state.afterLoad;
              state.afterLoad = null;
              Log.d("diskusage", "dismissed dialog");
              
              if (newRoot.children[0].children == null) {
                Log.d("diskusage", "empty card");
                handleEmptySDCard(activity, runAfterLoad);
                return;
              }
              state.root = newRoot;
              pkg_removed = null;
              Log.d("diskusage", "run afterLoad = " + afterLoadCopy);
              afterLoadCopy.run(state.root, false);
            }
          });

        } catch (final OutOfMemoryError e) {
          state.root = null;
          Log.d("DiskUsage", "out of memory!");
          handler.post(new Runnable() {
            public void run() {
              if (state.loading == null) return;
              state.loading.dismiss();
              handleOutOfMemory(activity);
            }
          });
          return;
        }
      }
    }.start();
  }
  
  @Override
  protected void onPause() {
    PersistantActivityState state = getPersistantState();
    if (state.loading != null) {
      if (state.loading.isShowing()) state.loading.dismiss();
      Log.d("diskusage", "removed progress dialog");
      state.loading = null;
    }
    super.onPause();
  }
  
  private void handleEmptySDCard(final LoadableActivity activity,
      final AfterLoad afterLoad) {
    new AlertDialog.Builder(activity)
    .setTitle(activity.getString(R.string.empty_or_missing_sdcard))
    .setPositiveButton(activity.getString(R.string.button_rescan), new OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        if (afterLoad == null)
          throw new RuntimeException("afterLoad is empty");
        LoadFiles(activity, afterLoad, true);
      }
    })
    .setOnCancelListener(new OnCancelListener() {
      public void onCancel(DialogInterface dialog) {
        activity.finish();
      }
    }).create().show();
  }
  
  private static void handleOutOfMemory(final Activity activity) {
    new AlertDialog.Builder(activity)
    .setTitle(activity.getString(R.string.out_of_memory))
    .setOnCancelListener(new OnCancelListener() {
      public void onCancel(DialogInterface dialog) {
        activity.finish();
      }
    }).create().show();
  }
}
