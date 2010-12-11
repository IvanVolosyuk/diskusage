package com.google.android.diskusage;

import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.os.Bundle;

public class SelectActivity extends Activity {
  private AlertDialog dialog;
  @Override
  protected void onResume() {
    super.onResume();
    ArrayList<String> options = new ArrayList<String>();
    final String externalCard = "External card";
    final String internalCard = "Internal card";
    final String programStorage = "App storage";
    
    if (MountPoint.getExternalStorage() != null) {
      options.add(externalCard);
    }
    if (MountPoint.getInternalStorage() != null) {
      options.add(internalCard);
    }
    options.add(programStorage);
    final String[] optionsArray = options.toArray(new String[options.size()]);
    
    dialog = new AlertDialog.Builder(this)
    .setItems(optionsArray,
        new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        String option = optionsArray[which];
        if (option.equals(externalCard)) {
          Intent i = new Intent(SelectActivity.this, DiskUsage.class);
          if (diskUsageState != null)
            i.putExtra(DiskUsage.STATE_KEY, diskUsageState);
          startActivityForResult(i, 0);
        } else if (option.equals(internalCard)) {
          Intent i = new Intent(SelectActivity.this, DiskUsageInternal.class);
          if (diskUsageInternalState != null)
            i.putExtra(DiskUsage.STATE_KEY, diskUsageInternalState);
          startActivityForResult(i, 0);
        } else if (option.equals(programStorage)) {
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
    }).create();
    dialog.show();
  }
  
  @Override
  protected void onPause() {
    if (dialog.isShowing()) dialog.dismiss();
    super.onPause();
  }
  
  Bundle diskUsageState;
  Bundle diskUsageInternalState;
  Bundle appUsageState;
  
  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (data == null) return;
    Bundle state = data.getBundleExtra(DiskUsage.STATE_KEY);
    if (state == null) return;
    if (resultCode == DiskUsage.DISKUSAGE_STATE) {
      diskUsageState = state;
    } else if (resultCode == DiskUsage.APPUSAGE_STATE) {
      appUsageState = state;
    } else if (resultCode == DiskUsage.DISKUSAGE_INTERNAL_STATE) {
      diskUsageInternalState = state;
    }
  }
  
  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putBundle("diskusage", diskUsageState);
    outState.putBundle("appusage", appUsageState);
    outState.putBundle("diskusageInternal", diskUsageInternalState);
  }
  
  @Override
  protected void onRestoreInstanceState(Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    diskUsageState = savedInstanceState.getBundle("diskusage");
    appUsageState = savedInstanceState.getBundle("appusage");
    diskUsageInternalState = savedInstanceState.getBundle("diskusageInternal");
  }
}
