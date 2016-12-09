package com.google.android.diskusage.datasource;

import java.io.IOException;

import android.content.Context;

public interface DebugDataSourceBridge {
  DataSource initNewDump(Context context) throws IOException;
  DataSource loadDefaultDump() throws IOException;
  void saveDumpAndSendReport(DataSource debugDataSource, Context context) throws IOException;
  boolean dumpExist();
}
