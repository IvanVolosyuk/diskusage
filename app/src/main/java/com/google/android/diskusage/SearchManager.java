package com.google.android.diskusage;

import com.google.android.diskusage.entity.FileSystemEntry;
import com.google.android.diskusage.entity.FileSystemSuperRoot;

public class SearchManager {
  private DiskUsageMenu menu;
  private Search finishedSearch;
  private Search activeSearch;
  private String query;
  
  private class Search extends Thread {
    public final String query;
    public FileSystemSuperRoot baseRoot;
    public FileSystemSuperRoot newRoot;
    
    Search(String query, FileSystemSuperRoot baseRoot) {
      this.query = query;
      this.baseRoot = baseRoot;
    }
    
    public void run() {
      try {
        FileSystemSuperRoot root = menu.masterRoot;
        this.newRoot = (FileSystemSuperRoot)
        root.filter(this.query, baseRoot.getDisplayBlockSize());
        if (isInterrupted()) return;
        menu.diskusage.handler.post(new Runnable() {
          @Override
          public void run() {
            searchFinished(Search.this);
          }
        });
      } catch (FileSystemEntry.SearchInterruptedException e) {}
    }
  }

  public SearchManager(DiskUsageMenu menu) {
    this.menu = menu;
  }
  
  public void search(String newQuery) {
    newQuery = newQuery.toLowerCase();
    query = newQuery;
    if (activeSearch != null) {
      if (newQuery.contains(activeSearch.query)) {
        // pending search, return
        return;
      } else {
        activeSearch.interrupt();
        activeSearch = null;
      }
    }

    startSearch();
  }

  private void startSearch() {
    FileSystemSuperRoot baseRoot = menu.masterRoot;
    if (finishedSearch != null) {
      if (query.contains(finishedSearch.query)) {
        baseRoot = finishedSearch.newRoot; 
      } else {
        finishedSearch = null;
      }
    }
    
    if (baseRoot != null) {
      Search search = new Search(query, baseRoot);
      search.start();
    } else {
      menu.finishedSearch(null, null);
    }
  }
  
  private void searchFinished(Search search) {
    if (activeSearch == search) {
      activeSearch = null;
    }
    finishedSearch = search;
    
    if (!query.equals(search.query)) {
      startSearch();
    }
    menu.finishedSearch(search.newRoot, search.query);
  }
  
  public void cancelSearch() {
    if (activeSearch != null) activeSearch.interrupt();
    activeSearch = null;
    finishedSearch = null;
  }
}