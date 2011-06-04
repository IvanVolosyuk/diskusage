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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

import android.app.ActionBar;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.StatFs;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.MenuItem.OnMenuItemClickListener;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.diskusage.FileSystemState.FileSystemView;
import com.google.android.diskusage.entity.FileSystemEntry;
import com.google.android.diskusage.entity.FileSystemEntrySmall;
import com.google.android.diskusage.entity.FileSystemFile;
import com.google.android.diskusage.entity.FileSystemFreeSpace;
import com.google.android.diskusage.entity.FileSystemPackage;
import com.google.android.diskusage.entity.FileSystemRoot;
import com.google.android.diskusage.entity.FileSystemSuperRoot;
import com.google.android.diskusage.entity.FileSystemSpecial;
import com.google.android.diskusage.entity.FileSystemSystemSpace;
import com.google.android.diskusage.opengl.FileSystemViewGPU;
import com.google.android.diskusage.utils.MimeTypes;

public class DiskUsage extends LoadableActivity {
  // FIXME: wrap to direct requests to rendering thread
  protected FileSystemState fileSystemState;
  public static final int RESULT_DELETE_CONFIRMED = 10;
  public static final int RESULT_DELETE_CANCELED = 11;
  
  public static final String STATE_KEY = "state";
  public static final String TITLE_KEY = "title";
  public static final String ROOT_KEY = "root";
  public static final String KEY_KEY = "key";
  
  public static final String DELETE_PATH_KEY = "path";
  public static final String DELETE_ABSOLUTE_PATH_KEY = "absolute_path";

  private String pathToDelete;
  
  private String rootPath;
  private String rootTitle;
  String key;
  private static final MimeTypes mimeTypes = new MimeTypes();
  private FileSystemEntry selectedEntity;

  protected FileSystemView makeView(FileSystemState eventHandler) {
    SharedPreferences prefs =
      getSharedPreferences("settings", Context.MODE_PRIVATE);
    boolean hardwareRenderer = prefs.getBoolean("hw_renderer", false);

    if (hardwareRenderer) {
      return new FileSystemViewGPU(this, eventHandler);
    } else {
      return new FileSystemViewCPU(this, eventHandler);
    }
  }
  
  @Override
  protected void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    setContentView(new TextView(this));
    platform.onCreate();
    Intent i = getIntent();
    rootPath = i.getStringExtra(ROOT_KEY);
    rootTitle = i.getStringExtra(TITLE_KEY);
    key = i.getStringExtra(KEY_KEY);
    Bundle receivedState = i.getBundleExtra(STATE_KEY);
    Log.d("diskusage", "onCreate, rootPath = " + rootPath + " receivedState = " + receivedState);
    if (receivedState != null) onRestoreInstanceState(receivedState);
  }
  
  static class Platform {
    void onCreate() {}
    public void invalidateOptionsMenu() {}
    public void setShowAsAction(MenuItem item) {}
    public boolean mergedStorage() { return false; }

    static Platform getInstance(DiskUsage context) {
      final int sdkVersion = Integer.parseInt(Build.VERSION.SDK);
      if (sdkVersion >= Build.VERSION_CODES.HONEYCOMB) {
        return context.new Honeycomb();
      } else {
        return new Platform();
      }
    }
  }
  
  private class Honeycomb extends Platform {
    @Override
    void onCreate() {
      ActionBar actionBar = DiskUsage.this.getActionBar();
      actionBar.setDisplayHomeAsUpEnabled(true);
    }
    public void invalidateOptionsMenu() {
      DiskUsage.this.invalidateOptionsMenu();
    }
    public void setShowAsAction(MenuItem item) {
      item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
    }
    public boolean mergedStorage() { return true; }
  }
  
  Platform platform = Platform.getInstance(this);
  
  ArrayList<Runnable> afterLoadAction = new ArrayList<Runnable>();
  
  @Override
  protected void onResume() {
    super.onResume();
    if (pkg_removed != null) {
      // Check if package removed
      String pkg_name = pkg_removed.pkg;
      PackageManager pm = getPackageManager();
      try {
        pm.getPackageInfo(pkg_name, 0);
      } catch (NameNotFoundException e) {
        if (fileSystemState != null)
          fileSystemState.removeInRenderThread(pkg_removed);
      }
      pkg_removed = null;
    }
    LoadFiles(this, new AfterLoad() {
      public void run(FileSystemSuperRoot root, boolean isCached) {
        fileSystemState = new FileSystemState(DiskUsage.this, root);
        FileSystemView view = makeView(fileSystemState);
        fileSystemState.startZoomAnimationInRenderThread(null, !isCached);
        setContentView((View)view);
        ((View)view).requestFocus();
        for (Runnable r : afterLoadAction) {
          r.run();
        }
        afterLoadAction.clear();
        if (pathToDelete != null) {
          String path = pathToDelete;
          pathToDelete = null;
          continueDelete(path);
        }
      }
    }, false);
  }
  
  @Override
  protected void onPause() {
    super.onPause();
    if (fileSystemState != null) {
      fileSystemState.killRenderThread();
      final Bundle savedState = new Bundle();
      fileSystemState.saveState(savedState);
      afterLoadAction.add(new Runnable() {
        @Override
        public void run() {
          fileSystemState.restoreStateInRenderThread(savedState);
        }
      });
    }
  }
  
  @Override
  public void onActivityResult(int a, int result, Intent i) {
    if (result != RESULT_DELETE_CONFIRMED) return;
    pathToDelete = i.getStringExtra("path"); 
  }
  
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    return super.onCreateOptionsMenu(menu);
  }
  
  private static abstract class VersionedPackageViewer {
    abstract void viewPackage(String pkg);

    public static VersionedPackageViewer newInstance(DiskUsage context) {
      final int sdkVersion = Integer.parseInt(Build.VERSION.SDK);
      VersionedPackageViewer viewer = null;
      if (sdkVersion < Build.VERSION_CODES.GINGERBREAD) {
        viewer = context.new EclairPackageViewer();
      } else {
        viewer = context.new GingerbreadPackageViewer();
      }
      return viewer;
    }
  }

  private final class EclairPackageViewer extends VersionedPackageViewer {
    @Override
    void viewPackage(String pkg) {
      try {
        final String APP_PKG_PREFIX = "com.android.settings.";
        final String APP_PKG_NAME = APP_PKG_PREFIX+"ApplicationPkgName";
        Log.d("diskusage", "show package = " + pkg);
        Intent viewIntent = new Intent(Intent.ACTION_VIEW);
        viewIntent.setComponent(new ComponentName(
            "com.android.settings", "com.android.settings.InstalledAppDetails"));
        viewIntent.putExtra(APP_PKG_NAME, pkg);
        viewIntent.putExtra("pkg", pkg);
        startActivity(viewIntent);
      } catch (RuntimeException e) {
        Toast.makeText(DiskUsage.this,
            "Sorry, failed to view the installed app. " +
            "Please contact app developer.", Toast.LENGTH_SHORT).show();
      }
    }
  }

  private final class GingerbreadPackageViewer extends VersionedPackageViewer {
    @Override
    void viewPackage(String pkg) {
      Log.d("diskusage", "show package = " + pkg);
      Intent viewIntent = new Intent(
          Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
          Uri.parse("package:" + pkg));
      startActivity(viewIntent);
    }
  }
  
  private final VersionedPackageViewer packageViewer =
    VersionedPackageViewer.newInstance(this);
  
  protected void viewPackage(FileSystemPackage pkg) {
    packageViewer.viewPackage(pkg.pkg);
    // FIXME: reload package data instead of just removing it
    pkg_removed = pkg;
  }
  
  protected void addRendererSwitchItem(Menu menu) {
    final boolean isGPU = fileSystemState.isGPU();
    // FIXME: i18n
    menu.add(isGPU ? "Software Renderer" : "Hardware Renderer")
    .setOnMenuItemClickListener(new OnMenuItemClickListener() {
      public boolean onMenuItemClick(MenuItem item) {
        fileSystemState.killRenderThread();
        SharedPreferences prefs =
          getSharedPreferences("settings", Context.MODE_PRIVATE);
        boolean hardwareRenderer = prefs.getBoolean("hw_renderer", true);
        prefs.edit().putBoolean("hw_renderer", !hardwareRenderer).commit();

        View newView = isGPU ?
            new FileSystemViewCPU(DiskUsage.this, fileSystemState) :
              new FileSystemViewGPU(DiskUsage.this, fileSystemState);
        DiskUsage.this.setContentView(newView);
        newView.requestFocus();
        return true;
      }
    });
  }
  
  void continueDelete(String path) {
    FileSystemEntry entry = fileSystemState.masterRoot.getEntryByName(path);
    if (entry != null) {
      BackgroundDelete.startDelete(this, entry);
    } else {
      Toast.makeText(this, 
          "Oops. Can't find directory to be deleted.", Toast.LENGTH_SHORT);
    }
  }
  
  private void askForDeletion(final FileSystemEntry entry) {
    final String path = entry.path2();
    final String fullPath = entry.absolutePath();
    Log.d("DiskUsage", "Deletion requested for " + path);
    
    if (entry instanceof FileSystemEntrySmall) {
      Toast.makeText(this,
          "Delete directory instead", Toast.LENGTH_SHORT).show();

      return;
    }
    if (entry.children == null || entry.children.length == 0) {
      if (entry instanceof FileSystemPackage) {
        this.pkg_removed = (FileSystemPackage) entry;
        BackgroundDelete.startDelete(this, entry);
        return;
      }

      // Delete single file or directory
      new AlertDialog.Builder(this)
      .setTitle(new File(fullPath).isDirectory()
          ? format(R.string.ask_to_delete_directory, path)
          : format(R.string.ask_to_delete_file, path))
      .setPositiveButton(str(R.string.button_delete),
          new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
          BackgroundDelete.startDelete(DiskUsage.this, entry);
        }
      })
      .setNegativeButton(str(R.string.button_cancel),
          new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int whichButton) {
        }
      }).create().show();
    } else {
      Intent i = new Intent(this, DeleteActivity.class);
      i.putExtra(DELETE_PATH_KEY, path);
      i.putExtra(DELETE_ABSOLUTE_PATH_KEY, fullPath);
      i.putExtra(DeleteActivity.NUM_FILES_KEY, entry.getNumFiles());

      i.putExtra(DiskUsage.KEY_KEY, this.key);
      i.putExtra(DiskUsage.TITLE_KEY, this.getRootTitle());
      i.putExtra(DiskUsage.ROOT_KEY, this.getRootPath());
      i.putExtra(DeleteActivity.SIZE_KEY, entry.sizeString());
      this.startActivityForResult(i, 0);
    }
  }
  private String format(int id, Object... args) {
    return getString(id, args);
  }
  
  private String str(int id) {
    return getString(id);
  }
  
  public void view(FileSystemEntry entry) {
    Intent intent = new Intent(Intent.ACTION_VIEW);
    intent.addCategory(Intent.CATEGORY_DEFAULT);
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    if (entry instanceof FileSystemEntrySmall) {
      entry = entry.parent;
    }
    if (entry instanceof FileSystemPackage) {
      viewPackage((FileSystemPackage) entry);
      return;
    }
    if (entry.parent != null && entry.parent instanceof FileSystemPackage) {
      viewPackage((FileSystemPackage) entry.parent);
      return;
    }

    File file = new File(entry.absolutePath());
    Uri uri = Uri.fromFile(file);
    
    if (file.isDirectory()) {
      intent = new Intent(Intent.ACTION_VIEW);
      intent.addCategory(Intent.CATEGORY_DEFAULT);
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      intent.setDataAndType(uri, "vnd.android.cursor.item/com.metago.filemanager.dir");
      
      try {
        startActivity(intent);
        return;
      } catch(ActivityNotFoundException e) {
      }

      intent = new Intent("org.openintents.action.VIEW_DIRECTORY");
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      intent.setData(uri);
      
      try {
        startActivity(intent);
        return;
      } catch(ActivityNotFoundException e) {
      }

      intent = new Intent("org.openintents.action.PICK_DIRECTORY");
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      intent.setData(uri);
      intent.putExtra("org.openintents.extra.TITLE",
          str(R.string.title_in_oi_file_manager));
      intent.putExtra("org.openintents.extra.BUTTON_TEXT",
          str(R.string.button_text_in_oi_file_manager));
      try {
        startActivity(intent);
        return;
      } catch(ActivityNotFoundException e) {
      }
      Toast.makeText(this, str(R.string.install_oi_file_manager),
          Toast.LENGTH_SHORT).show();
      return;
    }

    String fileName = entry.name;
    int dot = fileName.lastIndexOf(".");
    if (dot != -1) {
      String extension = fileName.substring(dot + 1).toLowerCase(); 
      String mime = mimeTypes.getMimeByExtension(this, extension);
      try {
        if (mime != null) {
          intent.setDataAndType(uri, mime);
        } else {
          intent.setDataAndType(uri, "binary/octet-stream");
        }
        startActivity(intent);
        return;
      } catch (ActivityNotFoundException e) {
      }
    }
    Toast.makeText(this, str(R.string.no_viewer_found),
        Toast.LENGTH_SHORT).show();
  }
  
  public void finishOnBack() {
    Bundle outState = new Bundle();
    onSaveInstanceState(outState);
    Intent result = new Intent();
    result.putExtra(DiskUsage.STATE_KEY, outState);
    result.putExtra(DiskUsage.KEY_KEY, key);
    setResult(0, result);
    finish();
  }
  
  public void setSelectedEntity(FileSystemEntry position) {
    this.selectedEntity = position;
    platform.invalidateOptionsMenu();
    setTitle(format(R.string.title_for_path, position.toTitleString()));
  }
  
  public void addRescanMenuEntry(Menu menu) {
    menu.add(getString(R.string.button_rescan))
    .setOnMenuItemClickListener(new OnMenuItemClickListener() {
      public boolean onMenuItemClick(MenuItem item) {
        LoadFiles(DiskUsage.this, new AfterLoad() {
          public void run(FileSystemSuperRoot newRoot, boolean isCached) {
            fileSystemState.startZoomAnimationInRenderThread(newRoot, !isCached);
          }
        }, true);
        return true;
      }
    });
  }
  
  public boolean addShowMenuEntry(Menu menu) {
    boolean showFileMenu = false;
    FileSystemEntry entry = null;

    if (!fileSystemState.sdcardIsEmpty()) {
      entry = selectedEntity;
      // FIXME: hack to disable removal of /sdcard
      if (entry == fileSystemState.masterRoot.children[0] || entry instanceof FileSystemSpecial) {
        // Toast.makeText(context, "Select directory or file first", Toast.LENGTH_SHORT).show();
      } else {
        showFileMenu = true;
      }
    }
    
    final FileSystemEntry menuForEntry = entry;
    
    MenuItem item = menu.add(str(R.string.button_show))
    .setEnabled(showFileMenu)
    .setOnMenuItemClickListener(new OnMenuItemClickListener() {
      public boolean onMenuItemClick(MenuItem item) {
        view(menuForEntry);
        return true;
      }
    });
    platform.setShowAsAction(item);
    return showFileMenu;
  }
  
  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    //Log.d("DiskUsage", "onCreateContextMenu");
    menu.clear();
    if (fileSystemState == null) return true;

    boolean showFileMenu = addShowMenuEntry(menu);
    addRendererSwitchItem(menu);
    addRescanMenuEntry(menu);
    
    final FileSystemEntry menuForEntry = selectedEntity;
    menu.add(str(R.string.button_delete))
    .setEnabled(showFileMenu && menuForEntry instanceof FileSystemFile)
    .setOnMenuItemClickListener(new OnMenuItemClickListener() {
      public boolean onMenuItemClick(MenuItem item) {
        askForDeletion(menuForEntry);
        return true;
      }
    });
    return true;
  }
  
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
      switch (item.getItemId()) {
          case android.R.id.home:
            finishOnBack();
            break;
      }
      return super.onOptionsItemSelected(item);
  }
  
  protected void onSaveInstanceState(Bundle outState) {
    if (fileSystemState == null) return;
    
    fileSystemState.killRenderThread();
    fileSystemState.saveState(outState);
  }

  protected void onRestoreInstanceState(final Bundle inState) {
    Log.d("diskusage", "onRestoreInstanceState, rootPath = " + inState.getString(ROOT_KEY));

    if (fileSystemState != null)
      fileSystemState.restoreStateInRenderThread(inState);
    else {
      afterLoadAction.add(new Runnable() {
        @Override
        public void run() {
          fileSystemState.restoreStateInRenderThread(inState);
        }
      });
    }
  }
  
  public interface AfterLoad {
    public void run(FileSystemSuperRoot root, boolean isCached);
  }
  
  Handler handler = new Handler();
  
  private Runnable progressUpdater;
  
  static abstract class MemoryClass {
    abstract int maxHeap();
    
    static class MemoryClassDefault extends MemoryClass {
      @Override
      int maxHeap() {
        return 16 * 1024 * 1024;
      }
    };
    
    static MemoryClass getInstance(DiskUsage diskUsage) {
      final int sdkVersion = Integer.parseInt(Build.VERSION.SDK);
      if (sdkVersion < Build.VERSION_CODES.ECLAIR) {
        return new MemoryClassDefault();
      } else {
        return diskUsage.new MemoryClassDetected();
      }
    }
  };

  class MemoryClassDetected extends MemoryClass {
    @Override
    int maxHeap() {
      ActivityManager manager = (ActivityManager) DiskUsage.this.getSystemService(Context.ACTIVITY_SERVICE);
      return manager.getMemoryClass() * 1024 * 1024;
    }
  }
  
  MemoryClass memoryClass = MemoryClass.getInstance(this);
  
  private int getMemoryQuota() {
    int totalMem = memoryClass.maxHeap();
    int numMountPoints = MountPoint.getMountPoints().size();
    return totalMem / (numMountPoints + 1);
  }
  
  static class FileSystemStats {
    final int blockSize;
    final long freeBlocks;
    final long busyBlocks;
    final long totalBlocks;
    
    public FileSystemStats(MountPoint mountPoint) {
      StatFs stats = null;
      try {
        stats = new StatFs(mountPoint.getRoot());
      } catch (IllegalArgumentException e) {
        Log.e("diskusage",
            "Failed to get filesystem stats for " + mountPoint.getRoot(), e);
      }
      if (stats != null) {
        blockSize = stats.getBlockSize();
        freeBlocks = stats.getAvailableBlocks();
        totalBlocks = stats.getBlockCount();
        busyBlocks = totalBlocks - freeBlocks;
      } else {
        freeBlocks = totalBlocks = busyBlocks = 0;
        blockSize = 512;
      }
    }
    public String formatUsageInfo() {
      if (totalBlocks == 0) return "Used <no information>";
      return String.format("Used %s of %s",
          FileSystemEntry.calcSizeString(busyBlocks * blockSize),
          FileSystemEntry.calcSizeString(totalBlocks * blockSize));
    }
  };
  
  public interface ProgressGenerator {
    FileSystemEntry lastCreatedFile();
    long pos();
  };
  
  Runnable makeProgressUpdater(final ProgressGenerator scanner,
      final FileSystemStats stats) {
    return new Runnable() {
      private FileSystemEntry file;
      @Override
      public void run() {
        MyProgressDialog dialog = getPersistantState().loading;
        if (dialog != null) {
          dialog.setMax(stats.busyBlocks);
          FileSystemEntry lastFile = scanner.lastCreatedFile();
          if (lastFile != file) {
            dialog.setProgress(scanner.pos(), lastFile);
          }
          file = lastFile;
        }
        handler.postDelayed(this, 50);
      }
    };
  }

  @Override
  FileSystemSuperRoot scan() throws IOException, InterruptedException {
    MountPoint mountPoint0 = null;
    if (key.startsWith("rooted")) {
      mountPoint0 = MountPoint.getRooted(getRootPath());
    } else {
      mountPoint0 = MountPoint.getNormal(getRootPath());
    }
    final MountPoint mountPoint = mountPoint0;
    final FileSystemStats stats = new FileSystemStats(mountPoint);

    int heap = getMemoryQuota();
    FileSystemEntry rootElement = null;

    final NativeScanner scanner = new NativeScanner(DiskUsage.this, stats.blockSize, stats.busyBlocks, heap);
    progressUpdater = makeProgressUpdater(scanner, stats);
    handler.post(progressUpdater);
    
    MountPoint realMountPoint = mountPoint;
    boolean fakeDataHoneycomb = (platform.mergedStorage()
        && key.equals("storage:/data"));
    if (fakeDataHoneycomb) realMountPoint = MountPoint.getHoneycombSdcard();
    
    try {
//      if (true) throw new RuntimeException("native fail");
      rootElement = scanner.scan(realMountPoint);
    } catch (RuntimeException e) {
      if (realMountPoint.rootRequired) throw e;
      // Legacy code for devices which fail to setup native code
      handler.removeCallbacks(progressUpdater);
      final Scanner legacyScanner = new Scanner(
          20, stats.blockSize, realMountPoint.getExcludeFilter(), stats.busyBlocks, heap);
      progressUpdater = makeProgressUpdater(legacyScanner, stats);
      handler.post(progressUpdater);
      rootElement = legacyScanner.scan(new File(realMountPoint.root));
    }
    
    handler.removeCallbacks(progressUpdater);
    
    ArrayList<FileSystemEntry> entries = new ArrayList<FileSystemEntry>();
    
    if (rootElement.children != null) {
      for (FileSystemEntry e : rootElement.children) {
        entries.add(e);
      }
    }
    
    if (mountPoint.hasApps2SD) {
      FileSystemEntry[] apps = loadApps2SD(true, AppFilter.getFilterForDiskUsage(), stats.blockSize);
      if (apps != null) {
        FileSystemEntry apps2sd =
          FileSystemEntry.makeNode(null, "Apps2SD").setChildren(
              apps, stats.blockSize);
        entries.add(apps2sd);
      }
    }
    
    if (fakeDataHoneycomb) {
      FileSystemEntry media = FileSystemRoot.makeNode(
          "media", realMountPoint.root).setChildren(entries.toArray(new FileSystemEntry[0]),
              stats.blockSize);
      entries = new ArrayList<FileSystemEntry>();
      entries.add(media);
      
      FileSystemEntry[] appList = loadApps2SD(false,
          AppFilter.getFilterForHoneycomb(), stats.blockSize);
      if (appList != null) {
        FileSystemEntry apps = FileSystemEntry.makeNode(null, "Apps").setChildren(appList, stats.blockSize);
        entries.add(apps);
      }
    }
    
    long visibleBlocks = 0;
    for (FileSystemEntry e : entries) {
      visibleBlocks += e.getSizeInBlocks();
    }
    
    long systemBlocks = stats.totalBlocks - stats.freeBlocks - visibleBlocks;
    Collections.sort(entries, FileSystemEntry.COMPARE);
    if (systemBlocks > 0) {
      entries.add(new FileSystemSystemSpace("System data", systemBlocks * stats.blockSize, stats.blockSize));
      entries.add(new FileSystemFreeSpace("Free space", stats.freeBlocks * stats.blockSize, stats.blockSize));
    } else {
      long freeBlocks = stats.freeBlocks + systemBlocks;
      if (freeBlocks > 0) {
        entries.add(new FileSystemFreeSpace("Free space", freeBlocks * stats.blockSize, stats.blockSize));
      }
    }
    
    rootElement = FileSystemRoot.makeNode(
        getRootTitle(), mountPoint.root)
        .setChildren(entries.toArray(new FileSystemEntry[0]), stats.blockSize);
    FileSystemSuperRoot newRoot = new FileSystemSuperRoot(stats.blockSize);
    newRoot.setChildren(new FileSystemEntry[] { rootElement }, stats.blockSize);
    return newRoot;
  }
  
  protected FileSystemEntry[] loadApps2SD(boolean sdOnly, AppFilter appFilter, int blockSize) {
    final int sdkVersion = Integer.parseInt(Build.VERSION.SDK);
    if (sdkVersion < Build.VERSION_CODES.FROYO && sdOnly) return null;

    try {
      return (new Apps2SDLoader(this).load(sdOnly, appFilter, blockSize));
    } catch (Throwable t) {
      Log.e("diskusage", "problem loading apps2sd info", t);
      return null;
    }
  }
  
  public FileSystemEntry.ExcludeFilter getExcludeFilter() {
    return MountPoint.getMountPoints().get(getRootPath()).getExcludeFilter();
  }
  
  @Override
  public String getRootTitle() {
    return rootTitle;
  }

  @Override
  public String getRootPath() {
    return rootPath;
  }
  
  public String getKey() {
    return key;
  }
}
