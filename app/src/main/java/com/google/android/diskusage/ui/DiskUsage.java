/*
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

package com.google.android.diskusage.ui;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.FileUriExposedException;
import android.os.Handler;
import android.provider.Settings;
import androidx.annotation.NonNull;
import android.view.Menu;
import android.view.MenuItem;
import com.google.android.diskusage.filesystem.Apps2SDLoader;
import com.google.android.diskusage.filesystem.BackgroundDelete;
import com.google.android.diskusage.filesystem.mnt.MountPoint;
import com.google.android.diskusage.core.NativeScanner;
import com.google.android.diskusage.R;
import com.google.android.diskusage.opengl.RendererManager;
import com.google.android.diskusage.core.Scanner;
import com.google.android.diskusage.databinding.ActivityCommonBinding;
import com.google.android.diskusage.datasource.DataSource;
import com.google.android.diskusage.datasource.StatFsSource;
import com.google.android.diskusage.filesystem.entity.FileSystemEntry;
import com.google.android.diskusage.filesystem.entity.FileSystemEntrySmall;
import com.google.android.diskusage.filesystem.entity.FileSystemFreeSpace;
import com.google.android.diskusage.filesystem.entity.FileSystemPackage;
import com.google.android.diskusage.filesystem.entity.FileSystemRoot;
import com.google.android.diskusage.filesystem.entity.FileSystemSuperRoot;
import com.google.android.diskusage.filesystem.entity.FileSystemSystemSpace;
import com.google.android.diskusage.utils.Logger;
import com.google.android.diskusage.utils.MimeTypes;
import org.jetbrains.annotations.Contract;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import splitties.toast.ToastKt;

public class DiskUsage extends LoadableActivity {

  // FIXME: wrap to direct requests to rendering thread
  public FileSystemState fileSystemState;
  public static final int RESULT_DELETE_CONFIRMED = 10;
  public static final int RESULT_DELETE_CANCELED = 11;

  public static final String STATE_KEY = "state";
  public static final String KEY_KEY = "key";

  public static final String DELETE_PATH_KEY = "path";
  public static final String DELETE_ABSOLUTE_PATH_KEY = "absolute_path";
  String key;

  private String pathToDelete;

  private static final MimeTypes mimeTypes = new MimeTypes();
  public DiskUsageMenu menu = DiskUsageMenu.getInstance(this);
  RendererManager rendererManager = new RendererManager(this);

  @Override
  protected void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    Logger.getLOGGER().d("DiskUsage.onCreate()");
    ActivityCommonBinding binding = ActivityCommonBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
    menu.onCreate();
    Intent i = getIntent();


    key = i.getStringExtra(KEY_KEY);
    if (key == null) {
      // Just close instead of crashing later
      finish();
      return;
    }
    Bundle receivedState = i.getBundleExtra(STATE_KEY);

    MountPoint mountPoint = MountPoint.getForKey(this, key);
    if (mountPoint == null) {
      finish();
      return;
    }
    Logger.getLOGGER().d("DiskUsage.onCreate(), rootPath = %s, receivedState = %s",
            mountPoint.getRoot(), receivedState);
    if (receivedState != null) onRestoreInstanceState(receivedState);
  }

  ArrayList<Runnable> afterLoadAction = new ArrayList<>();

  public void applyPatternNewRoot(FileSystemSuperRoot newRoot, String searchQuery) {
    fileSystemState.replaceRootKeepCursor(newRoot, searchQuery);
  }

  @Override
  protected void onResume() {
    super.onResume();
    rendererManager.onResume();
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
    LoadFiles(this, (root, isCached) -> {
      fileSystemState = new FileSystemState(DiskUsage.this, root);
      rendererManager.makeView(fileSystemState, root);
      fileSystemState.startZoomAnimationInRenderThread(null, !isCached, false);

      for (Runnable r : afterLoadAction) {
        r.run();
      }
      afterLoadAction.clear();
      if (pathToDelete != null) {
        String path = pathToDelete;
        pathToDelete = null;
        continueDelete(path);
      }
    }, false);
  }

  @Override
  protected void onPause() {
    rendererManager.onPause();
    super.onPause();
    if (fileSystemState != null) {
      fileSystemState.killRenderThread();
      final Bundle savedState = new Bundle();
      fileSystemState.saveState(savedState);
      afterLoadAction.add(() -> fileSystemState.restoreStateInRenderThread(savedState));
    }
  }

  @Override
  public void onActivityResult(int a, int result, Intent i) {
    super.onActivityResult(a, result, i);
    if (result != RESULT_DELETE_CONFIRMED) return;
    pathToDelete = i.getStringExtra("path");
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    return super.onCreateOptionsMenu(menu);
  }

  private static abstract class VersionedPackageViewer {
    abstract void viewPackage(String pkg);

    @NonNull
    public static VersionedPackageViewer newInstance(DiskUsage context) {
      return context.new GingerbreadPackageViewer();
    }
  }

  private final class GingerbreadPackageViewer extends VersionedPackageViewer {
    @Override
    void viewPackage(String pkg) {
      Logger.getLOGGER().d("Show package = %s", pkg);
      Intent viewIntent = new Intent(
          Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
          Uri.parse("package:" + pkg));
      startActivity(viewIntent);
    }
  }

  private final VersionedPackageViewer packageViewer =
    VersionedPackageViewer.newInstance(this);

  protected void viewPackage(@NonNull FileSystemPackage pkg) {
    packageViewer.viewPackage(pkg.pkg);
    // FIXME: reload package data instead of just removing it
    pkg_removed = pkg;
  }

  void continueDelete(String path) {
    FileSystemEntry entry = fileSystemState.masterRoot.getEntryByName(path, true);
    if (entry != null) {
      BackgroundDelete.startDelete(this, entry);
    } else {
      ToastKt.toast("Oops. Can't find directory to be deleted.");
    }
  }

  public void askForDeletion(@NonNull final FileSystemEntry entry) {
    final String path = entry.path2();
    final String fullPath = entry.absolutePath();
    Logger.getLOGGER().d("Deletion requested for %s", path);

    if (entry instanceof FileSystemEntrySmall) {
      ToastKt.toast("Delete directory instead");
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
          ? getString(R.string.ask_to_delete_directory, path)
          : getString(R.string.ask_to_delete_file, path))
      .setPositiveButton(R.string.button_delete,
              (dialog, which) -> BackgroundDelete.startDelete(DiskUsage.this, entry))
      .setNegativeButton(android.R.string.cancel, null).create().show();
    } else {
      Intent i = new Intent(this, DeleteActivity.class);
      i.putExtra(DELETE_PATH_KEY, path);
      i.putExtra(DELETE_ABSOLUTE_PATH_KEY, fullPath);
      i.putExtra(DeleteActivity.NUM_FILES_KEY, entry.getNumFiles());

      i.putExtra(DiskUsage.KEY_KEY, this.key);
      i.putExtra(DeleteActivity.SIZE_KEY, entry.sizeString());
      this.startActivityForResult(i, 0);
    }
  }

  public boolean isIntentAvailable(Intent intent) {
    final PackageManager packageManager = getPackageManager();
    List<ResolveInfo> res = packageManager.queryIntentActivities(
        intent, PackageManager.MATCH_DEFAULT_ONLY);
    return res.size() > 0;
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
    if (entry.parent instanceof FileSystemPackage) {
      viewPackage((FileSystemPackage) entry.parent);
      return;
    }

    String path = entry.absolutePath();
    File file = new File(path);
    Uri uri = Uri.fromFile(file);

    if (file.isDirectory()) {
      // Go on with default file manager
      // Shoud this be optional?
      intent = new Intent(Intent.ACTION_VIEW);
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      intent.setDataAndType(uri, "inode/directory");

      try {
        startActivity(intent);
        return;
      } catch (ActivityNotFoundException ignored) {
      }

      intent = new Intent("org.openintents.action.VIEW_DIRECTORY");
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      intent.setData(uri);

      try {
        startActivity(intent);
        return;
      } catch (ActivityNotFoundException|FileUriExposedException ignored) {
      }

      intent = new Intent("org.openintents.action.PICK_DIRECTORY");
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      intent.setData(uri);
      intent.putExtra("org.openintents.extra.TITLE",
          getString(R.string.title_in_oi_file_manager));
      intent.putExtra("org.openintents.extra.BUTTON_TEXT",
          getString(R.string.button_text_in_oi_file_manager));

      try {
        startActivity(intent);
        return;
      } catch (ActivityNotFoundException|FileUriExposedException ignored) {
      }

      // old Astro
      intent = new Intent(Intent.ACTION_VIEW);
      intent.addCategory(Intent.CATEGORY_DEFAULT);
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      intent.setDataAndType(uri, "vnd.android.cursor.item/com.metago.filemanager.dir");

      try {
        startActivity(intent);
        return;
      } catch (ActivityNotFoundException|FileUriExposedException ignored) {
      }

      final Intent installSolidExplorer = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=pl.solidexplorer"));

      if (isIntentAvailable(installSolidExplorer)) {
        new AlertDialog.Builder(this)
        .setCancelable(true)
        .setTitle(R.string.dialog_ask_filemanager_title)
        .setMessage(R.string.dialog_ask_filemanager_desc)
            .setPositiveButton(R.string.button_install_filemanager, (arg0, arg1) ->
                    startActivity(installSolidExplorer))
            .setNegativeButton(android.R.string.cancel, null)
            .create().show();
      } else {
        ToastKt.toast(R.string.install_oi_file_manager);
      }
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
      } catch (ActivityNotFoundException|FileUriExposedException ignored) {
      }
    }
    ToastKt.toast(R.string.no_viewer_found);
  }

  public void rescan() {
    LoadFiles(DiskUsage.this, (newRoot, isCached) -> fileSystemState.startZoomAnimationInRenderThread(newRoot, !isCached, false), true);
  }

  public void finishOnBack() {
    if (!menu.readyToFinish()) {
      return;
    }
    Bundle outState = new Bundle();
    onSaveInstanceState(outState);
    Intent result = new Intent();
    result.putExtra(DiskUsage.STATE_KEY, outState);
    result.putExtra(DiskUsage.KEY_KEY, key);
    setResult(0, result);
    finish();
  }

  public void setSelectedEntity(FileSystemEntry position) {
    menu.update(position);
    setTitle(getString(R.string.title_for_path, position.toTitleString()));
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      finishOnBack();
    }
      return super.onOptionsItemSelected(item);
  }

  @Override
  protected void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    if (fileSystemState == null) return;

    fileSystemState.killRenderThread();
    fileSystemState.saveState(outState);
    menu.onSaveInstanceState(outState);
  }

  @Override
  protected void onRestoreInstanceState(@NonNull final Bundle inState) {
    Logger.getLOGGER().d("DiskUsage.onRestoreInstanceState(), rootPath = %s", inState.getString(KEY_KEY));

    if (fileSystemState != null)
      fileSystemState.restoreStateInRenderThread(inState);
    else {
      afterLoadAction.add(() -> fileSystemState.restoreStateInRenderThread(inState));
    }

    menu.onRestoreInstanceState(inState);
  }

  public interface AfterLoad {
    void run(FileSystemSuperRoot root, boolean isCached);
  }

  public Handler handler = new Handler();

  static abstract class MemoryClass {
    abstract int maxHeap();

    @NonNull
    @Contract("_ -> new")
    static MemoryClass getInstance(DiskUsage diskUsage) {
      return diskUsage.new MemoryClassDetected();
    }
  }

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
    int numMountPoints = MountPoint.getMountPoints(this).size();
    return totalMem / (numMountPoints + 1);
  }

  public static class FileSystemStats {
    final long blockSize;
    final long freeBlocks;
    final long busyBlocks;
    final long totalBlocks;

    public FileSystemStats(@NonNull MountPoint mountPoint) {
      StatFsSource stats = null;
      try {
        stats = DataSource.get().statFs(mountPoint.getRoot());
      } catch (IllegalArgumentException e) {
        Logger.getLOGGER().e(e, "Failed to get filesystem stats for " + mountPoint.getRoot());
      }
      if (stats != null) {
        blockSize = stats.getBlockSizeLong();
        freeBlocks = stats.getAvailableBlocksLong();
        totalBlocks = stats.getBlockCountLong();
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
  }

  public interface ProgressGenerator {
    FileSystemEntry lastCreatedFile();
    long pos();
  }

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
    final MountPoint mountPoint = MountPoint.getForKey(this, key);
    final FileSystemStats stats = new FileSystemStats(mountPoint);
    int heap = getMemoryQuota();

    FileSystemEntry rootElement;
    Runnable progressUpdater;
    try {
      final NativeScanner scanner = new NativeScanner(this, stats.blockSize, stats.busyBlocks, heap);
      progressUpdater = makeProgressUpdater(scanner, stats);
      handler.post(progressUpdater);
      rootElement = scanner.scan(mountPoint);
      handler.removeCallbacks(progressUpdater);
    } catch (RuntimeException e) {
      final Scanner scanner = new Scanner(20, stats.blockSize, stats.busyBlocks, heap);
      progressUpdater = makeProgressUpdater(scanner, stats);
      handler.post(progressUpdater);
      rootElement = scanner.scan(DataSource.get().createLegacyScanFile(mountPoint.getRoot()));
      handler.removeCallbacks(progressUpdater);
    }

    ArrayList<FileSystemEntry> entries = new ArrayList<>();

    if (rootElement.children != null) {
      Collections.addAll(entries, rootElement.children);
    }

    if (mountPoint.hasApps()) {
      FileSystemRoot media = (FileSystemRoot) FileSystemRoot.makeNode(
          getString(R.string.graph_media), mountPoint.getRoot(), false).setChildren(entries.toArray(new FileSystemEntry[0]),
              stats.blockSize);
      entries = new ArrayList<>();
      entries.add(media);

      FileSystemEntry[] appList = loadApps2SD(stats.blockSize);
      if (appList != null) {
        moveAppData(appList, media, stats.blockSize);
        FileSystemEntry apps = FileSystemEntry.makeNode(null, getString(R.string.graph_apps)).setChildren(appList, stats.blockSize);
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
      entries.add(new FileSystemSystemSpace(getString(R.string.graph_system_data), systemBlocks * stats.blockSize, stats.blockSize));
      entries.add(new FileSystemFreeSpace(getString(R.string.graph_free_space), stats.freeBlocks * stats.blockSize, stats.blockSize));
    } else {
      long freeBlocks = stats.freeBlocks + systemBlocks;
      if (freeBlocks > 0) {
        entries.add(new FileSystemFreeSpace(getString(R.string.graph_free_space), freeBlocks * stats.blockSize, stats.blockSize));
      }
    }

    rootElement = FileSystemRoot.makeNode(mountPoint.getTitle(), mountPoint.getRoot(), false)
        .setChildren(entries.toArray(new FileSystemEntry[0]), stats.blockSize);
    FileSystemSuperRoot newRoot = new FileSystemSuperRoot(stats.blockSize);
    newRoot.setChildren(new FileSystemEntry[] { rootElement }, stats.blockSize);
    return newRoot;
  }

  protected FileSystemEntry[] loadApps2SD(long blockSize) {
    try {
      return (new Apps2SDLoader(this).load(blockSize));
    } catch (Throwable t) {
      Logger.getLOGGER().e("DiskUsage.loadApps2SD(): Problem loading apps2sd info", t);
      return null;
    }
  }

  void moveIntoPackage(FileSystemPackage pkg,
                       @NonNull FileSystemRoot root,
                       String path, String newName,
                       FileSystemPackage.ChildType type,
                       long blockSize) {
    FileSystemEntry e = root.getByAbsolutePath(path);
    if (e != null) {
      e.remove(blockSize);
      FileSystemRoot newRoot = FileSystemRoot.makeNode(newName, path, true);
      newRoot.setChildren(e.children, blockSize);
      pkg.addPublicChild(newRoot, type, blockSize);
    }
  }

  void moveAppData(@NonNull FileSystemEntry[] apps, FileSystemRoot media, long blockSize) {
    String diskusage = "com.google.android.diskusage";
    for (FileSystemEntry a : apps) {
      FileSystemPackage app = (FileSystemPackage)  a;
      try {
        String cacheDir = getCacheDir().getCanonicalPath().replaceAll(diskusage, app.pkg);
        moveIntoPackage(app, media, cacheDir, "Cache", FileSystemPackage.ChildType.CACHE, blockSize);
      } catch (IOException ignored) {
      }
    }
    for (FileSystemEntry a : apps) {
      FileSystemPackage app = (FileSystemPackage)  a;
      try {
        String dir = getCodeCacheDir().getCanonicalPath().replaceAll(diskusage, app.pkg);
        moveIntoPackage(app, media, dir, "CodeCache", FileSystemPackage.ChildType.CACHE, blockSize);
      } catch (IOException ignored) {
      }
    }
    for (FileSystemEntry a : apps) {
      FileSystemPackage app = (FileSystemPackage)  a;
      try {
        String dir = getExternalCacheDir().getCanonicalPath().replaceAll(diskusage, app.pkg);
        moveIntoPackage(app, media, dir, "ExternalCache", FileSystemPackage.ChildType.CACHE, blockSize);
      } catch (IOException ignored) {
      }
    }
    for (FileSystemEntry a : apps) {
      FileSystemPackage app = (FileSystemPackage)  a;
      try {
        String dir = getDataDir().getCanonicalPath().replaceAll(diskusage, app.pkg);
        moveIntoPackage(app, media, dir, "Data", FileSystemPackage.ChildType.DATA, blockSize);
      } catch (IOException ignored) {
      }
    }
    for (FileSystemEntry a : apps) {
      FileSystemPackage app = (FileSystemPackage)  a;
      try {
        String dir = getFilesDir().getCanonicalPath().replaceAll(diskusage, app.pkg);
        moveIntoPackage(app, media, dir, "InternalFiles", FileSystemPackage.ChildType.DATA, blockSize);
      } catch (IOException ignored) {
      }
    }

    for (FileSystemEntry a : apps) {
      FileSystemPackage app = (FileSystemPackage)  a;
      try {
        String dir = getExternalFilesDir(null).getCanonicalPath().replaceAll(diskusage, app.pkg);
        moveIntoPackage(app, media, dir, "Files", FileSystemPackage.ChildType.DATA, blockSize);
      } catch (IOException ignored) {
      }
    }
    for (FileSystemEntry a : apps) {
      FileSystemPackage app = (FileSystemPackage)  a;
      try {
        for (File mediaDir : getExternalMediaDirs()) {
          String dir = mediaDir.getCanonicalPath().replaceAll(diskusage, app.pkg);
          moveIntoPackage(app, media, dir, "MediaFiles", FileSystemPackage.ChildType.DATA, blockSize);
        }
      } catch (IOException ignored) {
      }
    }
    for (FileSystemEntry a : apps) {
      FileSystemPackage app = (FileSystemPackage)  a;
      try {
        for (File mediaDir : getObbDirs()) {
          String dir = mediaDir.getCanonicalPath().replaceAll(diskusage, app.pkg);
          moveIntoPackage(app, media, dir, "Obb", FileSystemPackage.ChildType.CODE, blockSize);
        }
      } catch (IOException ignored) {
      }
    }

    for (FileSystemEntry a : apps) {
      FileSystemPackage app = (FileSystemPackage)  a;
      app.applyFilter(blockSize);
    }
    Arrays.sort(apps, FileSystemEntry.COMPARE);
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    super.onPrepareOptionsMenu(menu);
    this.menu.onPrepareOptionsMenu(menu);
    return true;
  }

  @Override
  public String getKey() {
    return key;
  }

  public void searchRequest() {
    menu.searchRequest();
  }
}
