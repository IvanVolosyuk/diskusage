package com.google.android.diskusage;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.diskusage.DiskUsage.AfterLoad;

public class DeleteActivity extends Activity {
  @Override
  protected void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    setContentView(new TextView(this));
    DiskUsage.LoadFiles(this, new AfterLoad() {
      public void run(FileSystemEntry root) {
        setContentView(R.layout.delete_view);
        ListView lv = (ListView) findViewById(R.id.list);
        
        List<FileSystemEntry> files = new ArrayList<FileSystemEntry>();
        String path = getIntent().getStringExtra("path");
        final Intent responseIntent = new Intent();
        responseIntent.putExtra("path", path);
        final FileSystemEntry entry = root.getEntryByName(path);
        FileSystemEntry.deleteParent = entry;
        entry.getAllChildren(files);
        lv.setAdapter(new ArrayAdapter<FileSystemEntry>(
            DeleteActivity.this, R.layout.list_item, R.id.text, files));
        Button ok = (Button) findViewById(R.id.ok_button);
        ok.setOnClickListener(new OnClickListener() {
          public void onClick(View arg0) {
            setResult(RESULT_OK, responseIntent);
            finish();
          }
        });
        Button cancel = (Button) findViewById(R.id.cancel_button);
        cancel.setOnClickListener(new OnClickListener() {
          public void onClick(View arg0) {
            setResult(RESULT_CANCELED);
            finish();
          }
        });
      }
    });
  }
}
