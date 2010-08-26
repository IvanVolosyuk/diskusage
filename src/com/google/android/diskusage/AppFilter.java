package com.google.android.diskusage;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;

public class AppFilter implements Parcelable {
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
  
  public boolean equals(Object o) {
    if (!(o instanceof AppFilter)) return false;
    AppFilter filter = (AppFilter) o;
    if (filter.enableChildren != enableChildren) return false;
    if (filter.useApk != useApk) return false;
    if (filter.useData != useData) return false;
    if (filter.useCache != useCache) return false;
    if (filter.useSD != useSD) return false;
    return true;
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

  public AppFilter() {}
  
  public AppFilter(Parcel in) {
    boolean[] arr = new boolean[5];
    in.readBooleanArray(arr);
    enableChildren = arr[0];
    useApk = arr[1];
    useData = arr[2];
    useCache = arr[3];
    useSD = arr[4];
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeBooleanArray(new boolean[] {
        enableChildren, useApk, useData,useCache, useSD
    });
  }
  
  public static final Parcelable.Creator<AppFilter> CREATOR = new Parcelable.Creator<AppFilter>() {
    public AppFilter createFromParcel(Parcel in) {
      AppFilter filter = new AppFilter(in);
      return filter;
    }

    public AppFilter[] newArray(int size) {
      return new AppFilter[size];
    }
  };

}
