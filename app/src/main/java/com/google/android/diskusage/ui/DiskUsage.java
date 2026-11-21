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
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.ClipData;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;

import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.MimeTypeMap;

import com.google.android.diskusage.BuildConfig;
import com.google.android.diskusage.datasource.fast.LegacyFileImpl;
import com.google.android.diskusage.datasource.fast.StatFsSourceImpl;
import com.google.android.diskusage.filesystem.Apps2SDLoader;
import com.google.android.diskusage.filesystem.BackgroundDelete;
import com.google.android.diskusage.filesystem.mnt.MountPoint;
import com.google.android.diskusage.core.NativeScanner;
import com.google.android.diskusage.R;
import com.google.android.diskusage.opengl.RendererManager;
import com.google.android.diskusage.core.Scanner;
import com.google.android.diskusage.databinding.ActivityCommonBinding;
import com.google.android.diskusage.datasource.StatFsSource;
import com.google.android.diskusage.filesystem.entity.FileSystemEntry;
import com.google.android.diskusage.filesystem.entity.FileSystemEntrySmall;
import com.google.android.diskusage.filesystem.entity.FileSystemFreeSpace;
import com.google.android.diskusage.filesystem.entity.FileSystemPackage;
import com.google.android.diskusage.filesystem.entity.FileSystemRoot;
import com.google.android.diskusage.filesystem.entity.FileSystemSuperRoot;
import com.google.android.diskusage.filesystem.entity.FileSystemSystemSpace;
import com.google.android.diskusage.ui.common.EdgeToEdge;
import com.google.android.diskusage.ui.common.ScanProgressDialog;
import timber.log.Timber;

import org.jetbrains.annotations.Contract;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

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

  public DiskUsageMenu menu = new DiskUsageMenu(this);
  RendererManager rendererManager = new RendererManager(this);

  @Override
  protected void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    final DiskUsageViewModel viewModel = new ViewModelProvider(this).get(DiskUsageViewModel.class);
    Timber.d("DiskUsage.onCreate()");
    EdgeToEdge.enable(this);
    ActivityCommonBinding binding = ActivityCommonBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
    EdgeToEdge.applyInsets(binding.getRoot());
    binding.toolbar.setVisibility(android.view.View.VISIBLE);
    setSupportActionBar(binding.toolbar);
    menu.onCreate(viewModel);

    // Handle back ourselves: with Android 13+ predictive back the framework no
    // longer delivers KEYCODE_BACK to the view, so the old handler in
    // FileSystemState never ran and the app fell back to "finish DiskUsage",
    // which surfaced the volume chooser again.
    getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
      @Override
      public void handleOnBackPressed() {
        finishOnBack();
      }
    });
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
    Timber.d("DiskUsage.onCreate(), rootPath = %s, receivedState = %s",
            mountPoint.getRoot(), receivedState);
    setTitle(mountPoint.getTitle());
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
    this.menu.setupToolbarMenu(menu);
    return true;
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
      Timber.d("Show package = %s", pkg);
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
    Timber.d("Deletion requested for %s", path);

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

  /**
   * Opens the selected entry in an external application.
   *
   * Android 11+ requires a content URI plus an explicit read grant; the old
   * code used a file:// URI, a bogus "binary/octet-stream" fallback and a
   * provider whose paths didn't cover removable volumes. That combination is
   * the root cause of the "no viewer found" and "video opens in a strange
   * mode" reports.
   */
  public void view(FileSystemEntry entry) {
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

    final String path = entry.absolutePath();
    final File file = new File(path);
    if (!file.exists()) {
      ToastKt.toast(getString(R.string.path_doesnt_exist, path));
      return;
    }

    final Uri uri;
    try {
      uri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".provider", file);
    } catch (IllegalArgumentException e) {
      Timber.w(e, "No provider path configured for %s", path);
      ToastKt.toast(R.string.no_viewer_found);
      return;
    }

    if (file.isDirectory()) {
      viewDirectory(uri, file);
      return;
    }

    if (startViewIntent(uri, file.getName(), guessMimeType(file.getName()))) return;
    // Unknown extension or no handler for the specific type: let the user pick.
    if (startViewIntent(uri, file.getName(), "*/*")) return;
    ToastKt.toast(R.string.no_viewer_found);
  }

  private static String guessMimeType(String fileName) {
    int dot = fileName.lastIndexOf('.');
    if (dot < 0 || dot == fileName.length() - 1) return null;
    String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
  }

  private boolean startViewIntent(Uri uri, String name, String mime) {
    Intent intent = new Intent(Intent.ACTION_VIEW);
    intent.addCategory(Intent.CATEGORY_DEFAULT);
    intent.setDataAndType(uri, mime == null ? "*/*" : mime);
    intent.setClipData(ClipData.newRawUri(name, uri));
    // Our own FileProvider URI: the viewer needs an explicit read grant.
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    return startViewIntent(intent);
  }

  private boolean startViewIntent(@NonNull Intent intent) {
    // No FLAG_GRANT_READ_URI_PERMISSION here: the caller decides. Granting read
    // access for a URI we do not own (e.g. the system documents provider) makes
    // UriGrantsManagerService throw a SecurityException, which used to silently
    // fall back to a useless chooser.
    try {
      startActivity(intent);
      return true;
    } catch (ActivityNotFoundException | SecurityException e) {
      Timber.d(e, "No activity for %s", intent);
      return false;
    }
  }

  /**
   * Opens a directory in a file manager.
   *
   * Android has no standard "show this folder" intent, and most file managers
   * do not register for a {@code content://} URI of a foreign provider. The
   * reliable route is the system documents provider: a document URI of the
   * "externalstorage" provider plus {@code vnd.android.document/directory} opens
   * the preinstalled Files app directly at that folder. The generic intents are
   * still tried first-but-one so third-party managers keep working, and a
   * copy-path dialog is offered when nothing can handle the folder.
   */
  private void viewDirectory(Uri uri, @NonNull File folder) {
    Intent documents = documentsIntent(folder);
    Timber.d("viewDirectory: folder=%s documentsIntent=%s", folder, documents);
    if (documents != null) {
      boolean started = startViewIntent(documents);
      Timber.d("viewDirectory: documents intent started=%s", started);
      if (started) return;
    }

    // Third-party file managers that accept any provider (Material Files & co).
    for (String mime : new String[] {"resource/folder", "inode/directory"}) {
      if (startViewIntent(uri, folder.getName(), mime)) return;
    }

    // Legacy integrations (OI File Manager, Astro, ...).
    Intent intent = new Intent("org.openintents.action.VIEW_DIRECTORY");
    intent.setData(uri);
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    intent.setClipData(ClipData.newRawUri("folder", uri));
    try {
      startActivity(intent);
      return;
    } catch (ActivityNotFoundException | SecurityException ignored) {
    }

    showNoFileManagerDialog(folder.getAbsolutePath());
  }

  /**
   * Builds a {@code content://com.android.externalstorage.documents/...} URI for
   * [folder], which the system Files app (DocumentsUI) understands.
   * Returns null for paths outside the shared storage volumes.
   */
  private Intent documentsIntent(@NonNull File folder) {
    final String path = folder.getAbsolutePath();
    String docId = null;

    File primary = Environment.getExternalStorageDirectory();
    if (primary != null) {
      String primaryPath = primary.getAbsolutePath();
      if (path.equals(primaryPath)) {
        docId = "primary:";
      } else if (path.startsWith(primaryPath + "/")) {
        docId = "primary:" + path.substring(primaryPath.length() + 1);
      }
    }
    if (docId == null && path.startsWith("/storage/")) {
      // Removable / USB volume: /storage/<UUID>/<relative path>
      String[] parts = path.split("/", 4);
      if (parts.length >= 3 && parts[2].length() > 0 && !parts[2].equals("emulated")
          && !parts[2].equals("self")) {
        docId = parts[2] + ":" + (parts.length > 3 ? parts[3] : "");
      }
    }
    if (docId == null) return null;

    try {
      Uri documentUri =
          DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", docId);
      Intent intent = new Intent(Intent.ACTION_VIEW);
      // The provider manages its own permissions; adding a read grant for a
      // foreign URI would throw a SecurityException in UriGrantsManagerService.
      intent.setDataAndType(documentUri, DocumentsContract.Document.MIME_TYPE_DIR);
      return intent;
    } catch (Throwable t) {
      Timber.w(t, "Cannot build documents URI for %s", path);
      return null;
    }
  }

  /** Nothing could open the folder: at least let the user copy the path. */
  private void showNoFileManagerDialog(final String path) {
    new AlertDialog.Builder(this)
        .setTitle(R.string.no_viewer_found)
        .setMessage(getString(R.string.no_file_manager_found_desc, path))
        .setPositiveButton(R.string.button_copy_path, (dialog, which) -> {
          android.content.ClipboardManager clipboard =
              (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
          if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("path", path));
            ToastKt.toast(R.string.path_copied);
          }
        })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
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

    if (MountPoint.getMountPoints(this).size() > 1) {
      // More than one volume: go back to the chooser so the user can switch.
      finish();
    } else {
      // Single volume: back means "leave the app". The task (and with it the
      // scan result) is kept, so reopening is instant.
      moveTaskToBack(true);
    }
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
    Timber.d("DiskUsage.onRestoreInstanceState(), rootPath = %s", inState.getString(KEY_KEY));

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
      return manager.getLargeMemoryClass() * 1024 * 1024;
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
        stats = new StatFsSourceImpl(mountPoint.getRoot());
      } catch (IllegalArgumentException e) {
        Timber.e(e, "Failed to get filesystem stats for " + mountPoint.getRoot());
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
    public String formatUsageInfo(Context context) {
      if (totalBlocks == 0) return context.getString(R.string.usage_info_unknown);
      long usedBytes = busyBlocks * blockSize;
      long totalBytes = totalBlocks * blockSize;
      int percent = totalBytes > 0 ? (int) (usedBytes * 100 / totalBytes) : 0;
      return context.getString(R.string.usage_info,
          FileSystemEntry.calcSizeString(usedBytes),
          FileSystemEntry.calcSizeString(totalBytes),
          percent);
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
        ScanProgressDialog dialog = getPersistantState().loading;
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
      if (rootElement == null || rootElement.children == null) {
        throw new IOException("Native scanner returned no data");
      }
    } catch (IOException | RuntimeException e) {
      Timber.w(e, "Native scan failed, falling back to the Java scanner");
      final Scanner scanner = new Scanner(20, stats.blockSize, stats.busyBlocks, heap);
      progressUpdater = makeProgressUpdater(scanner, stats);
      handler.post(progressUpdater);
      rootElement = scanner.scan(LegacyFileImpl.createRoot(mountPoint.getRoot()));
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
      Timber.e(t, "loadApps2SD: Problem loading apps2sd info");
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

  /**
   * Canonical path of a possibly absent directory, or null.
   *
   * `Context.getExternalCacheDir()`, `getObbDirs()` and friends return null when
   * the volume is not ready (storage full, external storage just reset, MIUI
   * blocking the app dir). The previous code called `getCanonicalPath()` on the
   * result and crashed with a NullPointerException, which aborted the whole
   * application list.
   */
  private static String canonicalPath(File file) {
    if (file == null) return null;
    try {
      return file.getCanonicalPath();
    } catch (IOException e) {
      return null;
    }
  }

  private void moveAppDirectory(FileSystemEntry[] apps, FileSystemRoot media,
                                String sourcePath, String newName,
                                FileSystemPackage.ChildType type, long blockSize) {
    if (sourcePath == null) return;
    try {
      for (FileSystemEntry a : apps) {
        if (!(a instanceof FileSystemPackage)) continue;
        FileSystemPackage app = (FileSystemPackage) a;
        String dir = sourcePath.replace(diskUsagePackage, app.pkg);
        moveIntoPackage(app, media, dir, newName, type, blockSize);
      }
    } catch (Throwable t) {
      Timber.w(t, "Cannot map %s for the app list", newName);
    }
  }

  private static final String diskUsagePackage = "com.google.android.diskusage";

  void moveAppData(@NonNull FileSystemEntry[] apps, FileSystemRoot media, long blockSize) {
    moveAppDirectory(apps, media, canonicalPath(getCacheDir()),
        "Cache", FileSystemPackage.ChildType.CACHE, blockSize);
    moveAppDirectory(apps, media, canonicalPath(getCodeCacheDir()),
        "CodeCache", FileSystemPackage.ChildType.CACHE, blockSize);
    moveAppDirectory(apps, media, canonicalPath(getExternalCacheDir()),
        "ExternalCache", FileSystemPackage.ChildType.CACHE, blockSize);
    moveAppDirectory(apps, media, canonicalPath(getDataDir()),
        "Data", FileSystemPackage.ChildType.DATA, blockSize);
    moveAppDirectory(apps, media, canonicalPath(getFilesDir()),
        "InternalFiles", FileSystemPackage.ChildType.DATA, blockSize);
    moveAppDirectory(apps, media, canonicalPath(getExternalFilesDir(null)),
        "Files", FileSystemPackage.ChildType.DATA, blockSize);

    for (File mediaDir : getExternalMediaDirs()) {
      if (mediaDir == null) continue;
      moveAppDirectory(apps, media, canonicalPath(mediaDir),
          "MediaFiles", FileSystemPackage.ChildType.DATA, blockSize);
    }
    for (File obbDir : getObbDirs()) {
      if (obbDir == null) continue;
      moveAppDirectory(apps, media, canonicalPath(obbDir),
          "Obb", FileSystemPackage.ChildType.CODE, blockSize);
    }

    for (FileSystemEntry a : apps) {
      if (!(a instanceof FileSystemPackage)) continue;
      FileSystemPackage app = (FileSystemPackage) a;
      try {
        app.applyFilter(blockSize);
      } catch (Throwable t) {
        Timber.w(t, "Cannot finalise app entry %s", app.pkg);
      }
    }
    Arrays.sort(apps, FileSystemEntry.COMPARE);
  }

  @Override
  public String getKey() {
    return key;
  }

  public void searchRequest() {
    menu.searchRequest();
  }
}
