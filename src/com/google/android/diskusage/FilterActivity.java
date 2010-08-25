package com.google.android.diskusage;

import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceActivity;

public class FilterActivity extends PreferenceActivity {
  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getPreferenceManager().setSharedPreferencesName("settings");
    addPreferencesFromResource(R.xml.filter);
    getPreferenceScreen().setOrderingAsAdded(true);
    final int sdkVersion = Integer.parseInt(Build.VERSION.SDK);
    if (sdkVersion < Build.VERSION_CODES.FROYO) {
      getPreferenceScreen().removePreference(
          getPreferenceScreen().findPreference("internal_only"));
    }
  }
}
