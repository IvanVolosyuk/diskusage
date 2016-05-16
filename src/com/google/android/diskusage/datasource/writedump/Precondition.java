package com.google.android.diskusage.datasource.writedump;


public class Precondition {

  public static <T> T checkNotNull(T x) {
    if (x == null) {
      throw new NullPointerException();
    }
    return x;
  }

}
