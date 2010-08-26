package com.google.android.diskusage;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.os.Bundle;

public class SelectActivity extends Activity {
  @Override
  protected void onResume() {
    super.onResume();
    new AlertDialog.Builder(this)
    .setItems(new String[] {"Storage card", "Internal storage"},
        new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        if (which == 0) {
          Intent i = new Intent(SelectActivity.this, DiskUsage.class);
          if (diskUsageState != null)
            i.putExtra(DiskUsage.STATE_KEY, diskUsageState);
          startActivityForResult(i, 0);
        } else {
          Intent i = new Intent(SelectActivity.this, AppUsage.class);
          if (appUsageState != null)
            i.putExtra(DiskUsage.STATE_KEY, appUsageState);
          startActivityForResult(i, 0);
        }
      }
    })
    .setTitle("View")
    .setOnCancelListener(new OnCancelListener() {
      @Override
      public void onCancel(DialogInterface dialog) {
        finish();
      }
    })
    .show();
  }
  
  Bundle diskUsageState;
  Bundle appUsageState;
  
  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (data == null) return;
    Bundle state = data.getBundleExtra(DiskUsage.STATE_KEY);
    if (state == null) return;
    if (resultCode == DiskUsage.DISKUSAGE_STATE) {
      diskUsageState = state;
    } else {
      appUsageState = state;
    }
  }
  
  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putBundle("diskusage", diskUsageState);
    outState.putBundle("appusage", appUsageState);
  }
  
  @Override
  protected void onRestoreInstanceState(Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    diskUsageState = savedInstanceState.getBundle("diskusage");
    appUsageState = savedInstanceState.getBundle("appusage");
  }
}
