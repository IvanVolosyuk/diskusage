/*
 * DiskUsage - displays sdcard usage on android.
 * Copyright (C) 2008-2011 Ivan Volosyuk
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

import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.diskusage.R;
import com.google.android.diskusage.databinding.DeleteViewBinding;
import com.google.android.diskusage.filesystem.entity.FileSystemEntry;
import com.google.android.diskusage.ui.common.EdgeToEdge;
import com.google.android.diskusage.ui.common.FileInfo;
import com.google.android.diskusage.ui.common.FileInfoAdapter;
import timber.log.Timber;
import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Confirmation screen that lists the content which is about to be removed.
 * The listing runs off the UI thread so that huge directories don't cause an
 * ANR (issue: "can not delete & scan is slow").
 */
public class DeleteActivity extends AppCompatActivity {
  public static final String NUM_FILES_KEY = "numFiles";
  public static final String SIZE_KEY = "size";

  private static final ExecutorService LISTING_EXECUTOR = Executors.newSingleThreadExecutor();

  Intent responseIntent;
  private DeleteViewBinding binding;

  @Override
  protected void onResume() {
    super.onResume();
    EdgeToEdge.enable(this);
    FileSystemEntry.setupStrings(this);
    final String path = getIntent().getStringExtra(DiskUsage.DELETE_PATH_KEY);
    final String absolutePath = getIntent().getStringExtra(DiskUsage.DELETE_ABSOLUTE_PATH_KEY);
    Timber.d("onResume: %s -> %s", path, absolutePath);

    binding = DeleteViewBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
    EdgeToEdge.applyInsets(binding.getRoot());
    setSupportActionBar(binding.toolbar);
    setTitle(R.string.button_delete);

    String sizeString = getIntent().getStringExtra(SIZE_KEY);
    int count = getIntent().getIntExtra(NUM_FILES_KEY, 0);
    binding.summary.setText(getString(R.string.delete_summary, count, sizeString));

    final File file = new File(absolutePath);
    binding.list.setLayoutManager(new LinearLayoutManager(this));

    LISTING_EXECUTOR.execute(() -> {
      final ArrayList<FileInfo> infos = new ArrayList<>();
      if (file.exists()) {
        collect(file, infos, 0);
      }
      runOnUiThread(() -> {
        if (isFinishing() || isDestroyed()) return;
        binding.list.setAdapter(new FileInfoAdapter(infos));
      });
    });

    responseIntent = new Intent();
    responseIntent.putExtra(DiskUsage.DELETE_PATH_KEY, path);
  }

  /** Depth-limited listing: enough to recognise the content, bounded memory. */
  private static void collect(File file, ArrayList<FileInfo> infos, int depth) {
    File[] children = file.listFiles();
    if (children == null) return;
    for (File sub : children) {
      if (sub.isDirectory()) {
        infos.add(new FileInfo("", sub.getName()));
        if (depth < 3) {
          collect(sub, infos, depth + 1);
        }
      } else if (sub.isFile()) {
        infos.add(new FileInfo(FileSystemEntry.calcSizeString(sub.length()), sub.getName()));
      }
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.ask_for_delete_menu, menu);
    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    int itemId = item.getItemId();
    if (itemId == R.id.ask_cancel) {
      setResult(DiskUsage.RESULT_DELETE_CANCELED);
      finish();
      return true;
    } else if (itemId == R.id.ask_delete) {
      setResult(DiskUsage.RESULT_DELETE_CONFIRMED, responseIntent);
      finish();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }
}
