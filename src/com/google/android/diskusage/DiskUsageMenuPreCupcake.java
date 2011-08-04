package com.google.android.diskusage;

import com.google.android.diskusage.entity.FileSystemSuperRoot;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;

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
      searchBox.setBackgroundDrawable(origSearchBackground);
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
      (LinearLayout) diskusage.findViewById(R.id.search_layout);
    searchLayout.addView((View)view, new LinearLayout.LayoutParams(
      LayoutParams.MATCH_PARENT, 0, 1.f));

    
    searchBar = diskusage.findViewById(R.id.search_bar);
    Button cancelSearch = (Button) diskusage.findViewById(R.id.cancel_button);
    searchBox = (EditText) diskusage.findViewById(R.id.search_box);
    origSearchBackground = searchBox.getBackground();
    
    cancelSearch.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        cancelSearch();
      }
    });
    if (searchPattern != null) {
      searchBox.setText(searchPattern);
      applyPattern(searchPattern);
    } else {
      searchBar.setVisibility(View.GONE);
    }
    searchBox.setOnKeyListener(new View.OnKeyListener() {
      @Override
      public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (KeyEvent.KEYCODE_BACK == keyCode) {
          cancelSearch();
          return true;
        }
        return false;
      }
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
    searchBox.setBackgroundDrawable(origSearchBackground);
    searchPattern = null;
    hideInputMethod();
  }
  
  @Override
  public MenuItem makeSearchMenuEntry(Menu menu) {
    MenuItem item = menu.add("Search");
    item.setOnMenuItemClickListener(new OnMenuItemClickListener() {
      public boolean onMenuItemClick(MenuItem item) {
        searchRequest();
        return true;
      }
    });
    return item;
  }
}
