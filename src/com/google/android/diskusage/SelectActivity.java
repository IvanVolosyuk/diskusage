package com.google.android.diskusage;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.os.Bundle;
import android.os.Handler;

public class SelectActivity extends Activity {
  private AlertDialog dialog;
  Map<String,Bundle> bundles = new TreeMap<String,Bundle>();
  Map<String, Option> optionInfo = new TreeMap<String, Option>();
  
  private class Option {
    String title;
    MountPoint mountPoint;
    
    Option(String title, MountPoint mountPoint) {
      this.title = title;
      this.mountPoint = mountPoint;
    }
  };
  public String getKeyForApp() {
    return "app";
  }
  
  public String getKeyForStorage(String root) {
    return "storage:" + root;
  }
  
  public Handler handler = new Handler();
  public Runnable checkForMountsUpdates = new Runnable() {
    @Override
    public void run() {
      boolean reload = false;
      try {
        BufferedReader reader = new BufferedReader(new FileReader("/proc/mounts"));
        String line;
        int checksum = 0;
        while ((line = reader.readLine()) != null) {
          checksum += line.length();
        }
        reader.close();
        if (checksum != MountPoint.checksum) {
          reload = true;
        }
      } catch (Throwable t) {}

      if (reload) {
        dialog.hide();
        MountPoint.reset();
        makeDialog();
      }
      handler.postDelayed(this, 2000);
    }
  };
  
  
  public void makeDialog() {
    ArrayList<String> options = new ArrayList<String>();
    
    final String storageCard = getString(R.string.storage_card);
    final String programStorage = getString(R.string.app_storage);
    
    options.add(programStorage);
    optionInfo.put(programStorage, new Option(programStorage, null));
    
    if (MountPoint.hasMultiple()) {
      for (MountPoint mountPoint : MountPoint.getMountPoints().values()) {
        options.add(mountPoint.root);
        optionInfo.put(mountPoint.root, new Option(mountPoint.root, mountPoint));
      }
    } else {
      options.add(storageCard);
      optionInfo.put(storageCard, new Option(storageCard, MountPoint.getDefaultStorage()));
    }
    
    final String[] optionsArray = options.toArray(new String[options.size()]);
    
    dialog = new AlertDialog.Builder(this)
    .setItems(optionsArray,
        new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        Option option = optionInfo.get(optionsArray[which]);
        String key;
        Intent i;
        if (option.mountPoint != null) {
          i = new Intent(SelectActivity.this, DiskUsage.class);
          i.putExtra(DiskUsage.TITLE_KEY, option.title);
          i.putExtra(DiskUsage.ROOT_KEY, option.mountPoint.root);
          key = getKeyForStorage(option.mountPoint.root);
        } else {
          i = new Intent(SelectActivity.this, AppUsage.class);
          i.putExtra(DiskUsage.TITLE_KEY, option.title);
          i.putExtra(DiskUsage.ROOT_KEY, "apps");
          key = getKeyForApp();
        }
        i.putExtra(DiskUsage.KEY_KEY, key);
        Bundle bundle = bundles.get(key);
        if (bundle != null) {
          i.putExtra(DiskUsage.STATE_KEY, bundle);
        }
        startActivityForResult(i, 0);
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
  
//  @Override
//  protected void onCreate(Bundle savedInstanceState) {
//    super.onCreate(savedInstanceState);
//    setContentView(new TextView(this));
//    ActionBar bar = getActionBar();
//    bar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_USE_LOGO);
//  }
  
  @Override
  protected void onResume() {
    super.onResume();
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
  protected void onSaveInstanceState(Bundle outState) {
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
  
  private static final String BUNDLE_KEYS = "keys";
}