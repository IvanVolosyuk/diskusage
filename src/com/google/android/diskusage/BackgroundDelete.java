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

import com.google.android.diskusage.entity.FileSystemEntry;
import com.google.android.diskusage.entity.FileSystemPackage;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

public class BackgroundDelete extends Thread {
  ProgressDialog dialog;
  File file;
  String path;
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
  
  private BackgroundDelete(final DiskUsage diskUsage, final FileSystemEntry entry) {
    this.diskUsage = diskUsage;
    this.entry = entry;
    
    path = entry.path2();
    String deleteRoot = entry.absolutePath();
    file = new File(deleteRoot);
    for (MountPoint mountPoint : MountPoint.getMountPoints(diskUsage).values()) {
      if ((mountPoint.root + "/").startsWith(deleteRoot + "/")) {
        Toast.makeText(diskUsage, "This delete operation will erase entire storage - canceled.",
            Toast.LENGTH_LONG).show();
        return;
      }
    }
    
    if (!file.exists()) {
      Toast.makeText(diskUsage, format(R.string.path_doesnt_exist, path),
          Toast.LENGTH_LONG).show();
      diskUsage.fileSystemState.removeInRenderThread(entry);
      return;
    }
    
    if (file.isFile()) {
      if (file.delete()) {
        Toast.makeText(diskUsage, str(R.string.file_deleted),
            Toast.LENGTH_SHORT).show();
        diskUsage.fileSystemState.removeInRenderThread(entry);
      } else {
        Toast.makeText(diskUsage, str(R.string.error_file_wasnt_deleted),
            Toast.LENGTH_SHORT).show();
      }
      return;
    }
    dialog = new ProgressDialog(diskUsage);
    dialog.setMessage(format(R.string.deleting_path, path));
    dialog.setIndeterminate(true);
    dialog.setButton(diskUsage.getString(R.string.button_background),
        new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        background();
        dialog = null;
      }
    });
    dialog.setButton2(diskUsage.getString(R.string.button_cancel),
        new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        cancel();
        dialog = null;
      }
    });
    dialog.setOnDismissListener(new OnDismissListener() {
      @Override
      public void onDismiss(DialogInterface _) {
        dialog = null;        
      }
    });
    dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
      @Override
      public void onCancel(DialogInterface _) {
        dialog = null;
      }
    });
    dialog.show();
    start();
  }
  
  private void uninstall(FileSystemPackage pkg) {
    String pkg_name = pkg.pkg;
    Uri packageURI = Uri.parse("package:" + pkg_name);
    Intent uninstallIntent = new Intent(Intent.ACTION_DELETE, packageURI);
    diskUsage.startActivity(uninstallIntent);
  }

  static void startDelete(DiskUsage diskUsage, FileSystemEntry entry) {
    new BackgroundDelete(diskUsage, entry);
  }
  
  @Override
  public void run() {
    deletionStatus = deleteRecursively(file);
    // FIXME: use notification object when backgrounded
    diskUsage.handler.post(new Runnable() {
      public void run() {
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
      }
    });
  }
  
  public void restore() {
    Log.d("DiskUsage", "restore started for " + path);
    int displayBlockSize = diskUsage.fileSystemState.masterRoot.getDisplayBlockSize();
    FileSystemEntry newEntry = new Scanner(
        // FIXME: hacked allocatedBlocks and heap size
        20, displayBlockSize, null, 0, 4).scan(
            new File(diskUsage.getRootPath() + "/" + path));
    // FIXME: may be problems in case of two deletions
    entry.parent.insert(newEntry, displayBlockSize);
    diskUsage.fileSystemState.restore(newEntry);
    
    Log.d("DiskUsage", "restoring undeleted: "
        + newEntry.name + " " + newEntry.sizeString());
  }
  
  public void notifyUser() {
    Log.d("DiskUsage", "Delete: status = " + deletionStatus
        + " directories " + numDeletedDirectories
        + " files " + numDeletedFiles);

    if (deletionStatus == DELETION_SUCCESS) {
      Toast.makeText(diskUsage,
          format(R.string.deleted_n_directories_and_n_files,
              numDeletedDirectories, numDeletedFiles),
              Toast.LENGTH_LONG).show();
    } else if (deletionStatus == DELETION_CANCELED) {
      Toast.makeText(diskUsage,
          format(R.string.deleted_n_directories_and_files_and_canceled,
              numDeletedDirectories, numDeletedFiles),
              Toast.LENGTH_LONG).show();
    } else {
      Toast.makeText(diskUsage,
          format(R.string.deleted_n_directories_and_n_files_and_failed,
              numDeletedDirectories, numDeletedFiles),
              Toast.LENGTH_LONG).show();
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
      for (int i = 0; i < files.length; i++) {
        int status = deleteRecursively(files[i]); 
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

  private String format(int id, Object... args) {
    return diskUsage.getString(id, args);
  }
  private String str(int id) {
    return diskUsage.getString(id);
  }
}

