package com.google.android.diskusage.datasource.debug;


import androidx.annotation.NonNull;
import org.jetbrains.annotations.Contract;

public class Precondition {

  @NonNull
  @Contract("null -> fail; !null -> param1")
  public static <T> T checkNotNull(T x) {
    if (x == null) {
      throw new NullPointerException();
    }
    return x;
  }

}
