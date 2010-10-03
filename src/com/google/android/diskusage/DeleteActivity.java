package com.google.android.diskusage;

import java.util.ArrayList;
import java.util.List;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

public class DeleteActivity extends DiskUsage {
  @Override
  protected void onResume() {
    super.onResume();
    setContentView(new TextView(this));
    LoadFiles(this, new AfterLoad() {
      public void run(FileSystemEntry root, boolean isCached) {
        setContentView(R.layout.delete_view);
        ListView lv = (ListView) findViewById(R.id.list);
        
        List<String> files = new ArrayList<String>();
        String path = getIntent().getStringExtra("path");
        final Intent responseIntent = new Intent();
        responseIntent.putExtra("path", path);
        final FileSystemEntry deleteRoot = root.getEntryByName(path);
        deleteRoot.getAllChildren(files, deleteRoot);
        lv.setAdapter(new ArrayAdapter<String>(
            DeleteActivity.this, R.layout.list_item, R.id.text, files));
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
    }, false);
  }
}
