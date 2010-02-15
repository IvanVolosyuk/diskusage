/**
 * DiskUsage - displays sdcard usage on android.
 * Copyright (C) 2008 Ivan Volosyuk
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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;

import java.io.File;

public class DiskUsage extends Activity {
  private FileSystemView view;
  private static final String SDCARD_ROOT = "/sdcard";
  private static FileSystemEntry root;
  private static AfterLoad afterLoad;
  private static ProgressDialog loading;
  
  static void LoadFiles(final Activity activity,
      final AfterLoad runAfterLoad, boolean force) {
    boolean scanRunning = false;
    
    if (force) {
      root = null;
    }

    if (root != null) {
      runAfterLoad.run(root);
      return;
    }
    
    scanRunning = afterLoad != null;
    afterLoad = runAfterLoad;
    loading = ProgressDialog.show(
        activity, null,
        activity.getString(R.string.scaning_directories),
        true, true);

    if (scanRunning) return;
    final Handler handler = new Handler();
    final File sdcard = new File(SDCARD_ROOT);

    new Thread() {
      @Override
      public void run() {
        try {
          FileSystemEntry rootElement =
            new FileSystemEntry(null, sdcard, 0, 20);
          final FileSystemEntry newRoot = new FileSystemEntry(
              new FileSystemEntry[] { rootElement } );

          handler.post(new Runnable() {
            public void run() {
              loading.dismiss();
              loading = null;
              AfterLoad runAfterLoad = afterLoad;
              afterLoad = null;
              
              if (newRoot.children[0].children == null) {
                handleEmptySDCard(activity);
                return;
              }
              root = newRoot;
              runAfterLoad.run(root);
            }
          });

        } catch (final OutOfMemoryError e) {
          root = null;
          Log.d("DiskUsage", "out of memory!");
          handler.post(new Runnable() {
            public void run() {
              loading.dismiss();
              handleOutOfMemory(activity);
            }
          });
          return;
        }
      }
    }.start();
  }
  
  @Override
  protected void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    LoadFiles(this, new AfterLoad() {
      public void run(FileSystemEntry root) {
        view = new FileSystemView(DiskUsage.this, root);
        setContentView(view);
        view.requestFocus();
      }
    }, false);
  }
  
  @Override
  public void onActivityResult(int a, int result, Intent i) {
    if (result != RESULT_OK) return;
    String path = i.getStringExtra("path");
    view.continueDelete(path);
  }
  

  @Override
  public final boolean onPrepareOptionsMenu(Menu menu) {
    view.onPrepareOptionsMenu(menu);
    return true;
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

  private static void handleEmptySDCard(final Activity activity) {
    new AlertDialog.Builder(activity)
    .setTitle(activity.getString(R.string.empty_or_missing_sdcard))
    .setOnCancelListener(new OnCancelListener() {
      public void onCancel(DialogInterface dialog) {
        activity.finish();
      }
    }).create().show();
  }
  final protected void onSaveInstanceState(Bundle outState) {
    if (view != null)
      view.saveState(outState);
  }
  final protected void onRestoreInstanceState(Bundle inState) {
    if (view != null)
      view.restoreState(inState); 
  }
  
  public interface AfterLoad {
    public void run(FileSystemEntry root);
  }
}
