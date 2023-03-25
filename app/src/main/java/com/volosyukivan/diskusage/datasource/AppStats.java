package com.volosyukivan.diskusage.datasource;

public interface AppStats {
  long getCacheSize();
  long getDataSize();
  long getCodeSize();
  long getExternalCacheSize();
  long getExternalCodeSize();
  long getExternalDataSize();
  long getExternalMediaSize();
  long getExternalObbSize();
}