package com.google.android.diskusage;

import java.io.File;

import com.google.android.diskusage.DiskUsage.AfterLoad;

import android.accounts.OnAccountsUpdateListener;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;

public abstract class LoadableActivity extends Activity {
  private ProgressDialog loading;
  private AfterLoad afterLoad;
  FileSystemPackage pkg_removed;
  
  abstract void setRoot(FileSystemEntry root);
  abstract FileSystemEntry getRoot();
  abstract FileSystemEntry scan();

  void LoadFiles(final LoadableActivity activity,
      final AfterLoad runAfterLoad, boolean force) {
    boolean scanRunning = false;
    
    if (force) {
      setRoot(null);
    }

    if (getRoot() != null) {
      runAfterLoad.run(getRoot(), true);
      return;
    }
    
    scanRunning = afterLoad != null;
    afterLoad = runAfterLoad;
    activity.loading = new ProgressDialog(activity);
    activity.loading.setOnCancelListener(new OnCancelListener() {
      @Override
      public void onCancel(DialogInterface dialog) {
        activity.loading = null;
        activity.finish();
      }
    });
    activity.loading.setCancelable(true);
    activity.loading.setIndeterminate(true);
    activity.loading.setMessage(activity.getString(R.string.scaning_directories));
    activity.loading.show();

    if (scanRunning) return;
    final Handler handler = new Handler();

    new Thread() {
      @Override
      public void run() {
        try {
          final FileSystemEntry newRoot = scan();

          handler.post(new Runnable() {
            public void run() {
              if (activity.loading == null) return;
              activity.loading.dismiss();
              activity.loading = null;
              AfterLoad runAfterLoad = afterLoad;
              afterLoad = null;
              
              if (newRoot.children[0].children == null) {
                handleEmptySDCard(activity, runAfterLoad);
                return;
              }
              setRoot(newRoot);
              pkg_removed = null;
              runAfterLoad.run(getRoot(), false);
            }
          });

        } catch (final OutOfMemoryError e) {
          setRoot(null);
          Log.d("DiskUsage", "out of memory!");
          handler.post(new Runnable() {
            public void run() {
              if (activity.loading == null) return;
              activity.loading.dismiss();
              handleOutOfMemory(activity);
            }
          });
          return;
        }
      }
    }.start();
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
