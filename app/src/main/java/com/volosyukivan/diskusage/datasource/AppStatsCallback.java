package com.volosyukivan.diskusage.datasource;

public interface AppStatsCallback {
   void onGetStatsCompleted(AppStats stats, boolean succeeded);
}