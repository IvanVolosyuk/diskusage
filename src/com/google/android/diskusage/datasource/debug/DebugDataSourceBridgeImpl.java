package com.google.android.diskusage.datasource.debug;

import java.io.IOException;

import android.content.Context;

import com.google.android.diskusage.datasource.DataSource;
import com.google.android.diskusage.datasource.DebugDataSourceBridge;

public class DebugDataSourceBridgeImpl implements DebugDataSourceBridge {
  public DebugDataSourceBridgeImpl() {
  }

  @Override
  public DataSource initNewDump(Context context) throws IOException {
    return DebugDataSource.initNewDump(context);
  }

  @Override
  public DataSource loadDefaultDump() throws IOException {
    return DebugDataSource.loadDefaultDump();
  }

  @Override
  public void saveDumpAndSendReport(DataSource debugDataSource, Context context)
      throws IOException {
    ((DebugDataSource) debugDataSource).saveDumpAndSendReport(context);
  }

  @Override
  public boolean dumpExist() {
    return DebugDataSource.dumpExist();
  }
}
