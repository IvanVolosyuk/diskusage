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

package com.google.android.diskusage.filesystem;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import androidx.annotation.NonNull;
import com.google.android.diskusage.R;
import com.google.android.diskusage.core.Scanner;
import com.google.android.diskusage.datasource.DataSource;
import com.google.android.diskusage.datasource.fast.LegacyFileImpl;
import com.google.android.diskusage.filesystem.entity.FileSystemEntry;
import com.google.android.diskusage.filesystem.entity.FileSystemPackage;
import com.google.android.diskusage.filesystem.mnt.MountPoint;
import com.google.android.diskusage.ui.DiskUsage;
import com.google.android.diskusage.utils.Logger;
import java.io.File;
import java.io.IOException;
import splitties.resources.TextResourcesKt;
import splitties.toast.ToastKt;

public class BackgroundDelete extends Thread {
  ProgressDialog dialog;
  File file;
  String path;
  String rootPath;
  DiskUsage diskUsage;
  FileSystemEntry entry;

  private static final int DELETION_SUCCESS = 0;
  private static final int DELETION_FAILED = 1;
  private static final int DELETION_CANCELED = 2;
  private static final int DELETION_IN_PROGRESS = 3;
  private volatile boolean cancelDeletion;
  private boolean backgroundDeletion;
  private int deletionStatus = DELETION_IN_PROGRESS;
  private int numDeletedDirectories = 0;
  private int numDeletedFiles = 0;

  private BackgroundDelete(final DiskUsage diskUsage, @NonNull final FileSystemEntry entry) {
    this.diskUsage = diskUsage;
    this.entry = entry;

    path = entry.path2();
    String deleteRoot = entry.absolutePath();
    file = new File(deleteRoot);
    for (MountPoint mountPoint : MountPoint.getMountPoints(diskUsage)) {
      if ((mountPoint.getRoot() + "/").startsWith(deleteRoot + "/")) {
        ToastKt.longToast("This delete operation will erase entire storage - canceled.");
        return;
      }
    }

    if (!file.exists()) {
      ToastKt.longToast(TextResourcesKt.appStr(R.string.path_doesnt_exist, path));
      diskUsage.fileSystemState.removeInRenderThread(entry);
      return;
    }

    if (file.isFile()) {
      if (file.delete()) {
        ToastKt.toast(R.string.file_deleted);
        diskUsage.fileSystemState.removeInRenderThread(entry);
      } else {
        ToastKt.toast(R.string.error_file_wasnt_deleted);
      }
      return;
    }
    dialog = new ProgressDialog(diskUsage);
    dialog.setMessage(TextResourcesKt.appStr(R.string.deleting_path, path));
    dialog.setIndeterminate(true);
    dialog.setButton(DialogInterface.BUTTON_POSITIVE, diskUsage.getString(R.string.button_background),
            (dialog, which) -> {
              background();
              dialog.dismiss();
            });
    dialog.setButton(DialogInterface.BUTTON_NEGATIVE, diskUsage.getString(android.R.string.cancel),
            (dialog, which) -> {
              cancel();
              dialog.dismiss();
            });
    dialog.setOnDismissListener(x -> dialog = null);
    dialog.setOnCancelListener(x -> dialog = null);
    dialog.show();
    start();
  }

  private void uninstall(@NonNull FileSystemPackage pkg) {
    String pkg_name = pkg.pkg;
    Uri packageURI = Uri.parse("package:" + pkg_name);
    Intent uninstallIntent = new Intent(Intent.ACTION_DELETE, packageURI);
    diskUsage.startActivity(uninstallIntent);
  }

  public static void startDelete(DiskUsage diskUsage, FileSystemEntry entry) {
    new BackgroundDelete(diskUsage, entry);
  }

  @Override
  public void run() {
    deletionStatus = deleteRecursively(file);
    // FIXME: use notification object when backgrounded
    diskUsage.handler.post(() -> {
      if (dialog != null) {
        try {
          dialog.dismiss();
        } catch (Exception e) {
          // ignore exception
        }
      }
      diskUsage.fileSystemState.removeInRenderThread(entry);
      if (deletionStatus != DELETION_SUCCESS) {
        restore();
        diskUsage.fileSystemState.requestRepaint();
        diskUsage.fileSystemState.requestRepaintGPU();
      }
      notifyUser();
    });
  }

  public void restore() {
    Logger.getLOGGER().d("restore started for " + path);
    MountPoint mountPoint = MountPoint.getForKey(diskUsage, diskUsage.getKey());
    long displayBlockSize = diskUsage.fileSystemState.masterRoot.getDisplayBlockSize();
    try {
      FileSystemEntry newEntry = new Scanner(
              // FIXME: hacked allocatedBlocks and heap size
              20, displayBlockSize, 0, 4).scan(
                      // Original: DataSource.get().createLegacyScanFile
              LegacyFileImpl.createRoot(mountPoint.getRoot() + "/" + path));
      // FIXME: may be problems in case of two deletions
      entry.parent.insert(newEntry, displayBlockSize);
      diskUsage.fileSystemState.restore(newEntry);
      Logger.getLOGGER().d("BackgroundDelete.restore(): Restoring undeleted: %s %s",
              newEntry.name, newEntry.sizeString());
    } catch (IOException e) {
      Logger.getLOGGER().d("Failed to restore");
    }
  }

  public void notifyUser() {
    Logger.getLOGGER().d("BackgroundDelete.notifyUser(): Delete: status = %s directories %s files %s",
            deletionStatus, numDeletedDirectories, numDeletedFiles);

    if (deletionStatus == DELETION_SUCCESS) {
      ToastKt.longToast(TextResourcesKt.appStr(R.string.deleted_n_directories_and_n_files,
              numDeletedDirectories, numDeletedFiles));
    } else if (deletionStatus == DELETION_CANCELED) {
      ToastKt.longToast(TextResourcesKt.appStr(R.string.deleted_n_directories_and_files_and_canceled,
              numDeletedDirectories, numDeletedFiles));
    } else {
      ToastKt.longToast(TextResourcesKt.appStr(R.string.deleted_n_directories_and_n_files_and_failed,
              numDeletedDirectories, numDeletedFiles));
    }

  }

  public void background() {
    backgroundDeletion = true;
  }

  public void cancel() {
    cancelDeletion = true;
  }

  public final int deleteRecursively(File directory) {
    if (cancelDeletion) return DELETION_CANCELED;
    boolean isDirectory = directory.isDirectory();
    if (isDirectory) {
      final File[] files = directory.listFiles();
      if (files == null) return DELETION_FAILED;
      for (File value : files) {
        int status = deleteRecursively(value);
        if (status != DELETION_SUCCESS) return status;
      }
    }

    boolean success = directory.delete();
    if (success) {
      if (isDirectory)
        numDeletedDirectories++;
      else
        numDeletedFiles++;
      return DELETION_SUCCESS;
    } else {
      return DELETION_FAILED;
    }
  }
}

