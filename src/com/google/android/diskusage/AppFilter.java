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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;

public class AppFilter implements Parcelable {
  public boolean enableChildren;
  public boolean useApk;
  public boolean useData;
  public boolean useCache;
  public boolean useDalvikCache;
  public boolean useSD;


  public static AppFilter getFilterForDiskUsage() {
    AppFilter filter = new AppFilter();
    filter.enableChildren = false;
    filter.useApk = true;
    filter.useData = false;
    filter.useDalvikCache = false;
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
    if (filter.useDalvikCache != useDalvikCache) return false;
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
    filter.useDalvikCache = prefs.getBoolean("show_dalvikCache", true);
    filter.useCache = prefs.getBoolean("show_cache", false);
    filter.useSD = !prefs.getBoolean("internal_only", true);
    return filter;
  }

  public AppFilter() {}
  
  public AppFilter(Parcel in) {
    boolean[] arr = new boolean[6];
    in.readBooleanArray(arr);
    enableChildren = arr[0];
    useApk = arr[1];
    useData = arr[2];
    useCache = arr[3];
    useSD = arr[4];
    useDalvikCache = arr[5];
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeBooleanArray(new boolean[] {
        enableChildren, useApk, useData,useCache, useSD, useDalvikCache
    });
  }
  
  public static final Parcelable.Creator<AppFilter> CREATOR =
    new Parcelable.Creator<AppFilter>() {
    public AppFilter createFromParcel(Parcel in) {
      AppFilter filter = new AppFilter(in);
      return filter;
    }

    public AppFilter[] newArray(int size) {
      return new AppFilter[size];
    }
  };
}
