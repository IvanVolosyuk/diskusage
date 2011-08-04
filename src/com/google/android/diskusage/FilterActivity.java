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

import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceActivity;

import com.google.android.diskusage.entity.FileSystemEntry;

public class FilterActivity extends PreferenceActivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    FileSystemEntry.setupStrings(this);
    getPreferenceManager().setSharedPreferencesName("settings");
    addPreferencesFromResource(R.xml.filter);
    getPreferenceScreen().setOrderingAsAdded(true);
    final int sdkVersion = Integer.parseInt(Build.VERSION.SDK);
    if (sdkVersion < Build.VERSION_CODES.FROYO) {
      getPreferenceScreen().removePreference(
          getPreferenceScreen().findPreference("apps"));
      getPreferenceScreen().removePreference(
          getPreferenceScreen().findPreference("memory"));
    }
    
  }
}
