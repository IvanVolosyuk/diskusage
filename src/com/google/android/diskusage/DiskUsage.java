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
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;

import java.io.File;

public class DiskUsage extends Activity {
	private static FileSystemView view;
	private ProgressDialog loading;
	private Handler handler;
	private static final String SDCARD_ROOT = "/sdcard";
	private static FileSystemEntry root;
	
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        
        if (root != null) {
          final FileSystemEntry superRoot = new FileSystemEntry(new FileSystemEntry[] { root } );
          view = new FileSystemView(DiskUsage.this, superRoot);
          setContentView(view);
          return;
        }

        handler = new Handler();
    	handler.post(new Runnable() {
    	  public void run() {
    	    final File sdcard = new File(SDCARD_ROOT);
    	    loading = ProgressDialog.show(
    	        DiskUsage.this, null,
    	        DiskUsage.this.getString(R.string.scaning_directories),
    	        true, true);

    	    new Thread() {
    	      @Override
            public void run() {
    	        try {
    	           root = new FileSystemEntry(null, sdcard, 0, 20);
    	          final FileSystemEntry superRoot = new FileSystemEntry(new FileSystemEntry[] { root } );
    	          view = new FileSystemView(DiskUsage.this, superRoot);

                  handler.post(new Runnable() {
                    public void run() {
                      loading.dismiss();
                      setContentView(view);
                      view.requestFocus();
                      if (root.children == null) {
                        handleEmptySDCard();
                      }
                    }
                  });

    	        } catch (final OutOfMemoryError e) {
    	          view = null;
    	          Log.d("DiskUsage", "out of memory!");
    	          handler.post(new Runnable() {
    	            public void run() {
    	              loading.dismiss();
    	              handleOutOfMemory();
    	            }
    	          });
    	          return;
    	        }
    	      }
    	    }.start();
    	  }
    	});
    }
    
    @Override
    public final boolean onPrepareOptionsMenu(Menu menu) {
      view.onPrepareOptionsMenu(menu);
      return true;
    }
    
    private void handleOutOfMemory() {
      new AlertDialog.Builder(this)
      .setTitle(getString(R.string.out_of_memory))
      .setOnCancelListener(new OnCancelListener() {
        public void onCancel(DialogInterface dialog) {
          DiskUsage.this.finish();
        }
      }).create().show();
    }
    
    private void handleEmptySDCard() {
      new AlertDialog.Builder(this)
      .setTitle(getString(R.string.empty_or_missing_sdcard))
      .setOnCancelListener(new OnCancelListener() {
        public void onCancel(DialogInterface dialog) {
          DiskUsage.this.finish();
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
}
