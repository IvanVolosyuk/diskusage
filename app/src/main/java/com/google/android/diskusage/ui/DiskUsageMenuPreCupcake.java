package com.google.android.diskusage.ui;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.support.annotation.NonNull;

import com.google.android.diskusage.R;
import com.google.android.diskusage.filesystem.entity.FileSystemSuperRoot;

public class DiskUsageMenuPreCupcake extends DiskUsageMenu {
  private String searchPattern;
  protected View searchBar;
  protected EditText searchBox;
  private Drawable origSearchBackground;

  public DiskUsageMenuPreCupcake(DiskUsage diskusage) {
    super(diskusage);
  }
  
  @Override
  public void onCreate() {}
  
  @Override
  public boolean readyToFinish() {
    if (searchPattern != null) {
      cancelSearch();
      return false;
    }
    return true;
  }
  
  @Override
  public void searchRequest() {
    searchBar.setVisibility(View.VISIBLE);
    searchBox.requestFocus();
  }
  
  @Override
  public boolean finishedSearch(FileSystemSuperRoot newRoot, String searchQuery) {
    boolean matched = super.finishedSearch(newRoot, searchQuery);
    if (matched) {
      searchBox.setBackground(origSearchBackground);
    } else {
      searchBox.setBackgroundColor(Color.parseColor("#FFDDDD"));
    }
    return matched;
  }
  
  @Override
  public void wrapAndSetContentView(View view, FileSystemSuperRoot newRoot) {
    super.wrapAndSetContentView(view, newRoot);
    
    diskusage.setContentView(R.layout.main);
    LinearLayout searchLayout =
            diskusage.findViewById(R.id.search_layout);
    searchLayout.addView(view, new LinearLayout.LayoutParams(
      LayoutParams.MATCH_PARENT, 0, 1.f));

    
    searchBar = diskusage.findViewById(R.id.search_bar);
    Button cancelSearch = diskusage.findViewById(R.id.cancel_button);
    searchBox = diskusage.findViewById(R.id.search_box);
    origSearchBackground = searchBox.getBackground();
    
    cancelSearch.setOnClickListener(v -> cancelSearch());
    if (searchPattern != null) {
      searchBox.setText(searchPattern);
      applyPattern(searchPattern);
    } else {
      searchBar.setVisibility(View.GONE);
    }
    searchBox.setOnKeyListener((v, keyCode, event) -> {
      if (KeyEvent.KEYCODE_BACK == keyCode) {
        cancelSearch();
        return true;
      }
      return false;
    });
    searchBox.addTextChangedListener(new TextWatcher() {
      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        searchPattern = s.toString();
        applyPattern(searchPattern);
      }
      
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
      
      @Override
      public void afterTextChanged(Editable s) {}
    });
  }
  
  public void hideInputMethod() {}
  
  public void cancelSearch() {
    searchBar.setVisibility(View.GONE);
    searchBox.setText("");
    searchBox.setBackground(origSearchBackground);
    searchPattern = null;
    hideInputMethod();
  }
  
  @Override
  public MenuItem makeSearchMenuEntry(@NonNull Menu menu) {
    MenuItem item = menu.add(R.string.button_search);
    item.setOnMenuItemClickListener(item1 -> {
      searchRequest();
      return true;
    });
    return item;
  }
}
