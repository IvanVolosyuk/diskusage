package com.google.android.diskusage;

import android.app.Activity;
import android.content.Intent;
import android.os.Debug;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.diskusage.delete.FileInfoAdapter;

public class DeleteActivity extends Activity {
  public static final String NUM_FILES_KEY = "numFiles";
  public static final String SIZE_KEY = "size";

  @Override
  protected void onResume() {
    super.onResume();
//    Debug.startMethodTracing("diskusage");
    FileSystemEntry.setupStrings(this);

    setContentView(R.layout.delete_view);
    ListView lv = (ListView) findViewById(R.id.list);
    TextView summary = (TextView) findViewById(R.id.summary);
    long size = getIntent().getLongExtra(SIZE_KEY, 0);
    int count = getIntent().getIntExtra(NUM_FILES_KEY, 0);
    summary.setText(
        FileInfoAdapter.formatMessage(this, count, FileSystemEntry.calcSizeString(size)));

//    String[] path = getIntent().getStringArrayExtra("path");
    String path = getIntent().getStringExtra("path");
    final Intent responseIntent = new Intent();
    responseIntent.putExtra("path", path);
    lv.setAdapter(new FileInfoAdapter(
        this,
        getIntent().getStringExtra(DiskUsage.ROOT_KEY),
        new String[] { getIntent().getStringExtra("path") },
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
