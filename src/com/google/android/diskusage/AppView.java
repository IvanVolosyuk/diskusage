package com.google.android.diskusage;

import android.content.ComponentName;
import android.content.Intent;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;

import com.google.android.diskusage.DiskUsage.AfterLoad;

public class AppView extends FileSystemView {
  AppView(DiskUsage context, FileSystemEntry root) {
    super(context, root);
  }
  
  private String str(int id) {
    return context.getString(id);
  }
  
  private void viewPackage(FileSystemPackage pkg) {
    final String APP_PKG_PREFIX = "com.android.settings.";
    final String APP_PKG_NAME = APP_PKG_PREFIX+"ApplicationPkgName";

    Log.d("diskusage", "show package = " + pkg.pkg);
    Intent viewIntent = new Intent(Intent.ACTION_VIEW);
    viewIntent.setComponent(new ComponentName(
        "com.android.settings", "com.android.settings.InstalledAppDetails"));
    viewIntent.putExtra(APP_PKG_NAME, pkg.pkg);
    viewIntent.putExtra("pkg", pkg.pkg);
    // FIXME: reload package data instead of just removing it
    context.pkg_removed = pkg;
    context.startActivity(viewIntent);
  }
  
  private void view(FileSystemEntry entry) {
    while (entry != null) {
      if (entry instanceof FileSystemPackage) {
        viewPackage((FileSystemPackage) entry);
        return;
      }
      entry = entry.parent;
    }
  }
  
  private void showFilterDialog() {
    Intent i = new Intent(context, FilterActivity.class);
    i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    context.startActivityForResult(i, 0);
  }

  
  public final void onPrepareOptionsMenu(Menu menu) {
    //Log.d("DiskUsage", "onCreateContextMenu");
    menu.clear();
    boolean showFileMenu = false;
    FileSystemEntry entry = null;

    if (!sdcardIsEmpty()) {
      entry = cursor.position;
      // FIXME: hack to disable removal of /sdcard
      if (entry == masterRoot.children[0]) {
//        Toast.makeText(context, "Select directory or file first", Toast.LENGTH_SHORT).show();
      } else if (!(cursor.position instanceof FileSystemSystemSpace)) {
        showFileMenu = true;
      }
    }
    
    final FileSystemEntry menuForEntry = entry;

    menu.add(str(R.string.button_show))
    .setEnabled(showFileMenu)
    .setOnMenuItemClickListener(new OnMenuItemClickListener() {
      public boolean onMenuItemClick(MenuItem item) {
        String path = menuForEntry.path();
        Log.d("DiskUsage", "show " + path);
        view(menuForEntry);
        return true;
      }
    });
    
    menu.add(context.getString(R.string.button_rescan))
    .setOnMenuItemClickListener(new OnMenuItemClickListener() {
      public boolean onMenuItemClick(MenuItem item) {
        context.LoadFiles(context, new AfterLoad() {
          public void run(FileSystemEntry newRoot, boolean isCached) {
            rescanFinished(newRoot);
            if (!isCached) startZoomAnimation();
          }
        }, true);
        return true;
      }
    });
    menu.add(context.getString(R.string.change_filter))
    .setOnMenuItemClickListener(new OnMenuItemClickListener() {
      public boolean onMenuItemClick(MenuItem item) {
        showFilterDialog();
        return true;
      }
    });
  }
}
