package com.volosyukivan.diskusage.datasource.fast;

import androidx.annotation.NonNull;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StreamCopy {
  public static void copyStream(@NonNull InputStream is, OutputStream os)
      throws IOException {
    int len;
    byte[] buffer = new byte[32768];
    while ((len = is.read(buffer)) != -1) {
      os.write(buffer, 0, len);
    }
    os.close();
    is.close();
  }

  @NonNull
  public static byte[] copyToArray(InputStream is) throws IOException {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    copyStream(is, os);
    os.flush();
    return os.toByteArray();
  }

  @NonNull
  public static byte[] readFully(File file) throws IOException {
    return copyToArray(new FileInputStream(file));
  }
}
