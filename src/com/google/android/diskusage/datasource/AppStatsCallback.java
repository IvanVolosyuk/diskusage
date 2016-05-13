package com.google.android.diskusage.datasource;

public interface AppStatsCallback {
   void onGetStatsCompleted(AppStats stats, boolean succeeded);
}