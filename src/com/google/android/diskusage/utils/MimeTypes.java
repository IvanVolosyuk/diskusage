package com.google.android.diskusage.utils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.zip.GZIPInputStream;

import android.content.Context;

import com.google.android.diskusage.R;

public final class MimeTypes {
  private HashMap<String, String> extensionToMime;
  
  private void initExtensions(Context context) {
    extensionToMime = new HashMap<String, String>();
    try {
      InputStream is = new GZIPInputStream(
          context.getResources().openRawResource(R.raw.mimes));
      ByteArrayOutputStream os = new ByteArrayOutputStream();
      byte[] buf = new byte[16384];
      while (true) {
        int r = is.read(buf);
        if (r <= 0) break;
        os.write(buf, 0, r);
      }
      String[] lines = os.toString().split("\n");
      String mime = null;
      for (int i = 0; i < lines.length; i++) {
        String val = lines[i];
        if (val.length() == 0) mime = null;
        else if (mime == null) mime = val;
        else extensionToMime.put(val, mime);
      }
    } catch (Exception e) {
      throw new RuntimeException("failed to open mime db", e);
    }
  }
  
  public String getMimeByExtension(Context context, String extension) {
    if (extensionToMime == null) {
      initExtensions(context);
    }
    return extensionToMime.get(extension);
  }
}
