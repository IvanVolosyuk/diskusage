package com.google.android.diskusage;

import android.content.Context;
import android.content.SharedPreferences;

public class AppFilter {
  public boolean enableChildren;
  public boolean useApk;
  public boolean useData;
  public boolean useCache;
  public boolean useSD;


  public static AppFilter getFilterForDiskUsage() {
    AppFilter filter = new AppFilter();
    filter.enableChildren = false;
    filter.useApk = true;
    filter.useData = false;
    filter.useCache = false;
    filter.useSD = true;
    return filter;
  }
  
  public static AppFilter loadSavedAppFilter(Context context) {
    SharedPreferences prefs =
      context.getSharedPreferences("settings", Context.MODE_PRIVATE);
    AppFilter filter = new AppFilter();
    filter.enableChildren = true;
    filter.useApk = prefs.getBoolean("show_apk", true);
    filter.useData = prefs.getBoolean("show_data", true);
    filter.useCache = prefs.getBoolean("show_cache", false);
    filter.useSD = !prefs.getBoolean("internal_only", true);
    return filter;
  }

  public AppFilter() {
  }
}
