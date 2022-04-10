package com.google.android.diskusage.opengl;

import com.google.android.diskusage.entity.FileSystemEntry;
import com.google.android.diskusage.opengl.RenderingThread.TextPixels;

public class DrawingCache {
  private final FileSystemEntry entry;
  private String sizeString;
  public RenderingThread.TextPixels textPixels;
  public RenderingThread.TextPixels sizePixels;

  public DrawingCache(FileSystemEntry entry) {
    this.entry = entry;
  }
  
  public String getSizeString() {
    if (sizeString != null) {
      return sizeString;
    }
    String sizeString = entry.sizeString();
    this.sizeString = sizeString;
    return sizeString;
  }

  public void resetSizeString() {
    sizeString = null;
    sizePixels = null;
  }
  
  public void drawText(RenderingThread rt, float x0, float y0, int elementWidth) {
    if (textPixels == null) {
      textPixels = new TextPixels(entry.name);
    }
    textPixels.draw(rt, x0, y0, elementWidth);
  }
  
  public void drawSize(RenderingThread rt, float x0, float y0, int elementWidth) {
    if (sizePixels == null) {
      sizePixels = new TextPixels(getSizeString());
    }
    sizePixels.draw(rt, x0, y0, elementWidth);
  }
}
