package com.google.android.diskusage;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.MenuItem.OnMenuItemClickListener;

import com.google.android.diskusage.DiskUsage.AfterLoad;
import com.google.android.diskusage.FileSystemView.MultiTouchHandler;
import com.google.android.diskusage.FileSystemView.VersionedMultitouchHandler;

public class AppView extends FileSystemView {
  AppView(DiskUsage context, FileSystemEntry root) {
    super(context, root);
  }
  
  private String str(int id) {
    return context.getString(id);
  }
  
  static abstract class VersionedPackageViewer {
    abstract void viewPackage(String pkg);

    public static VersionedPackageViewer newInstance(AppView view) {
      final int sdkVersion = Integer.parseInt(Build.VERSION.SDK);
      VersionedPackageViewer viewer = null;
      if (sdkVersion < Build.VERSION_CODES.GINGERBREAD) {
        viewer = view.new EclairPackageViewer();
      } else {
        viewer = view.new GingerbreadPackageViewer();
      }
      return viewer;
    }
  }

  class EclairPackageViewer extends VersionedPackageViewer {
    @Override
    void viewPackage(String pkg) {
      final String APP_PKG_PREFIX = "com.android.settings.";
      final String APP_PKG_NAME = APP_PKG_PREFIX+"ApplicationPkgName";
      Log.d("diskusage", "show package = " + pkg);
      Intent viewIntent = new Intent(Intent.ACTION_VIEW);
      viewIntent.setComponent(new ComponentName(
          "com.android.settings", "com.android.settings.InstalledAppDetails"));
      viewIntent.putExtra(APP_PKG_NAME, pkg);
      viewIntent.putExtra("pkg", pkg);
      context.startActivity(viewIntent);
    }
  }

  class GingerbreadPackageViewer extends VersionedPackageViewer {
    @Override
    void viewPackage(String pkg) {
      Log.d("diskusage", "show package = " + pkg);
      Intent viewIntent = new Intent(
          Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
          Uri.parse("package:" + pkg));
      context.startActivity(viewIntent);
    }
  }
  
  VersionedPackageViewer packageViewer = VersionedPackageViewer.newInstance(this);
  
  private void viewPackage(FileSystemPackage pkg) {
   packageViewer.viewPackage(pkg.pkg);
   // FIXME: reload package data instead of just removing it
   context.pkg_removed = pkg;
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
        String path = menuForEntry.path2();
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
