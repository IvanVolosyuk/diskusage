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
	private FileSystemView view;
	private ProgressDialog loading;
	private Handler handler;
	private static final String SDCARD_ROOT = "/sdcard";
	
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        
		Log.i("DiskUsage", "all placed!");
		
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
    	          final FileSystemEntry root =
    	            new FileSystemEntry(null, sdcard, 0, 20);
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
    	          Log.d("DiskUsage", "DiskUsage got OutOfMemory!");
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
      .setTitle("Out of Memory!")
      .setOnCancelListener(new OnCancelListener() {
        public void onCancel(DialogInterface dialog) {
          DiskUsage.this.finish();
        }
      }).create().show();
    }
    
    private void handleEmptySDCard() {
      new AlertDialog.Builder(this)
      .setTitle("Empty or missing sdcard")
      .setOnCancelListener(new OnCancelListener() {
        public void onCancel(DialogInterface dialog) {
          DiskUsage.this.finish();
        }
      }).create().show();
    }
}
