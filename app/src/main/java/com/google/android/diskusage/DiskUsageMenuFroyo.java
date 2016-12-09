package com.google.android.diskusage;

import android.content.Context;
import android.view.inputmethod.InputMethodManager;

public class DiskUsageMenuFroyo extends DiskUsageMenuPreCupcake {
  public DiskUsageMenuFroyo(DiskUsage diskusage) {
    super(diskusage);
  }

  @Override
  public void hideInputMethod() {
    InputMethodManager imm = (InputMethodManager)
    diskusage.getSystemService(Context.INPUT_METHOD_SERVICE);
    imm.hideSoftInputFromWindow(searchBox.getWindowToken(), 0);
  }
}
