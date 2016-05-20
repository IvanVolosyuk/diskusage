package com.google.android.diskusage.datasource.debug;


public class Precondition {

  public static <T> T checkNotNull(T x) {
    if (x == null) {
      throw new NullPointerException();
    }
    return x;
  }

}
