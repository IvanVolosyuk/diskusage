/**
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

package com.google.android.diskusage;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.diskusage.delete.FileInfoAdapter;
import com.google.android.diskusage.entity.FileSystemEntry;

public class DeleteActivity extends Activity {
  public static final String NUM_FILES_KEY = "numFiles";
  public static final String SIZE_KEY = "size";

  @Override
  protected void onResume() {
    super.onResume();
//    Debug.startMethodTracing("diskusage");
    FileSystemEntry.setupStrings(this);
    
    final String path = getIntent().getStringExtra(
        DiskUsage.DELETE_PATH_KEY);
    final String absolutePath = getIntent().getStringExtra(
        DiskUsage.DELETE_ABSOLUTE_PATH_KEY);
    Log.d("diskusage", "DeleteActivity: " + path + " -> " + absolutePath);

    setContentView(R.layout.delete_view);
    ListView lv = (ListView) findViewById(R.id.list);
    TextView summary = (TextView) findViewById(R.id.summary);
    String sizeString = getIntent().getStringExtra(SIZE_KEY);
    int count = getIntent().getIntExtra(NUM_FILES_KEY, 0);
    FileInfoAdapter.setMessage(
        this, summary, count, sizeString);

    final Intent responseIntent = new Intent();
    responseIntent.putExtra(DiskUsage.DELETE_PATH_KEY, path);
    lv.setAdapter(new FileInfoAdapter(
        this,
        absolutePath,
        count,
        summary));
    Button ok = (Button) findViewById(R.id.ok_button);
    ok.setOnClickListener(new OnClickListener() {
      public void onClick(View arg0) {
        setResult(DiskUsage.RESULT_DELETE_CONFIRMED, responseIntent);
        finish();
      }
    });
    Button cancel = (Button) findViewById(R.id.cancel_button);
    cancel.setOnClickListener(new OnClickListener() {
      public void onClick(View arg0) {
        setResult(DiskUsage.RESULT_DELETE_CANCELED);
        finish();
      }
    });
  }
  
  @Override
  protected void onPause() {
    super.onPause();
//    Debug.stopMethodTracing();
  }
}
