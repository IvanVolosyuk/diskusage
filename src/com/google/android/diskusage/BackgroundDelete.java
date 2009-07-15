package com.google.android.diskusage;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.util.Log;
import android.widget.Toast;

import java.io.File;

public class BackgroundDelete extends Thread {
  ProgressDialog dialog;
  File file;
  String path;
  FileSystemView view;
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
  
  private BackgroundDelete(FileSystemView view, FileSystemEntry entry) {
    this.view = view;
    this.entry = entry;
    path = entry.path();
    file = new File(path);
    if (!file.exists()) {
      Toast.makeText(view.context, format(R.string.path_doesnt_exist, path),
          Toast.LENGTH_LONG).show();
      view.remove(entry);
      return;
    }
    
    if (file.isFile()) {
      if (file.delete()) {
        Toast.makeText(view.context, str(R.string.file_deleted),
            Toast.LENGTH_SHORT).show();
        view.remove(entry);
      } else {
        Toast.makeText(view.context, str(R.string.error_file_wasnt_deleted),
            Toast.LENGTH_SHORT).show();
      }
      return;
    }
    view.remove(entry);
    
    dialog = new ProgressDialog(view.context);
    dialog.setMessage(format(R.string.deleting_path, path));
    dialog.setIndeterminate(true);
    dialog.setButton(view.context.getString(R.string.button_background),
        new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        background();
        dialog = null;
      }
    });
    dialog.setButton2(view.context.getString(R.string.button_cancel),
        new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        cancel();
        dialog = null;
      }
    });
    dialog.show();
    start();
  }
  
  static void startDelete(FileSystemView view, FileSystemEntry entry) {
    new BackgroundDelete(view, entry);
  }
  
  @Override
  public void run() {
    deletionStatus = deleteRecursively(file);
    // FIXME: use notification object when backgrounded
    view.post(new Runnable() {
      public void run() {
        if (dialog != null) dialog.dismiss();
        if (deletionStatus != DELETION_SUCCESS) {
          restore();
          view.invalidate();
        }
        notifyUser();
      }
    });
  }
  
  public void restore() {
    FileSystemEntry newEntry = new FileSystemEntry(null,
        new File(path), 0, 20);
    // FIXME: may be problems in case of two deletions
    entry.parent.insert(newEntry);
    view.restore(newEntry);
    Log.d("DiskUsage", "restoring undeleted: "
        + newEntry.name + " " +newEntry.size);
  }
  
  public void notifyUser() {
    Log.d("DiskUsage", "Delete: status = " + deletionStatus
        + " directories " + numDeletedDirectories
        + " files " + numDeletedFiles);

    if (deletionStatus == DELETION_SUCCESS) {
      Toast.makeText(view.context,
          format(R.string.deleted_n_directories_and_n_files,
              numDeletedDirectories, numDeletedFiles),
              Toast.LENGTH_LONG).show();
    } else if (deletionStatus == DELETION_CANCELED) {
      Toast.makeText(view.context,
          format(R.string.deleted_n_directories_and_files_and_canceled,
              numDeletedDirectories, numDeletedFiles),
              Toast.LENGTH_LONG).show();
    } else {
      Toast.makeText(view.context,
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
    return view.context.getString(id, args);
  }
  private String str(int id) {
    return view.context.getString(id);
  }
}

