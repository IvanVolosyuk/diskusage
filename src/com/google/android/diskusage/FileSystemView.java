/**
 * DiskUsage - displays sdcard usage on android.
 * Copyright (C) 2008 Ivan Volosyuk
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package com.google.android.diskusage;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.zip.GZIPInputStream;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.Toast;

import com.google.android.diskusage.DiskUsage.AfterLoad;

class FileSystemView extends View {
  FileSystemRoot masterRoot;
  //FileSystemEntry viewRoot;
  protected Cursor cursor;
  boolean titleNeedUpdate = false;
  DiskUsage context;
  
  private int numSpecialEntries = 0;
  private FileSystemFreeSpace freeSpace;
  private FileSystemSystemSpace systemSpace;
  private long freeSpaceZoom = 0;

  protected float targetViewDepth;
  protected long  targetViewTop;
  protected long  targetViewBottom;
  protected int   targetElementWidth;

  private float prevViewDepth;
  private long  prevViewTop;
  private long  prevViewBottom;
  private int   prevElementWidth;

  private float viewDepth;
  private long  viewTop;
  private long  viewBottom;
  
  private long displayTop;
  private long displayBottom;

  private int screenWidth;
  private int screenHeight;

  float yscale;

  private long animationStartTime;
  private Interpolator interpolator = new DecelerateInterpolator();
  private static long animationDuration = 900; 
  private static long deletionAnimationDuration = 900; 
  protected float maxLevels = 3.2f;

  
  // Enable simple view caching (useful for motions), but labels are painted
  // inconsistently.
  private final boolean useCache = false;

  static HashMap<String, String> extensionToMime;

  private boolean fullZoom;
  private boolean warnOnFileSelect;
  
  private float touchDepth;
  private long touchPoint;

  private FileSystemEntry touchEntry;
  private float touchX, touchY;
  private boolean touchMovement;
  private float speedX;
  private float speedY;
  
  private long touchZoom;
  private int multiNumTouches;
  private boolean multitouchReset;
  float touchWidth;
  float touchPointX; 
  float minDistance;
  float minDistanceX;
  int minElementWidth;
  int maxElementWidth;
  
  private int stats_num_deletions = 0;
  private boolean screenTouching;
  
  static class VersionedMultitouchHandler {
    boolean handleTouch(MotionEvent ev) {
      return false;
    }
    public static VersionedMultitouchHandler newInstance(FileSystemView view) {
      final int sdkVersion = Integer.parseInt(Build.VERSION.SDK);
      VersionedMultitouchHandler detector = null;
      if (sdkVersion < Build.VERSION_CODES.ECLAIR) {
        detector = new VersionedMultitouchHandler();
      } else {
        detector = view.new MultiTouchHandler();
      }
      return detector;
    }
  }

  class MultiTouchHandler extends VersionedMultitouchHandler {
    ArrayList<MotionFilter> filterX = new ArrayList<MotionFilter>();
    ArrayList<MotionFilter> filterY = new ArrayList<MotionFilter>();

    private MotionFilter getFilterX(int i) {
      if (filterX.size() <= i)
        filterX.add(new MotionFilter());
      return filterX.get(i);      
    }
    
    private MotionFilter getFilterY(int i) {
      if (filterY.size() <= i)
        filterY.add(new MotionFilter());
      return filterY.get(i);      
    }
    
    boolean handleTouch(MotionEvent ev) {
      int action = ev.getAction();
      Integer num = (Integer) ev.getPointerCount();
      if (num == 1) {
        return false;
      }

//      Log.d("diskusage", "multi: " + action + " num = " + num);
      if ((action & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_POINTER_DOWN) {
//        Log.d("diskusage", "multi down");
        multitouchReset = true;
        for (int i = 0; i < num; i++) {
          getFilterX(i).noFilter(ev.getX(i));
          getFilterY(i).noFilter(ev.getY(i));
        }
      }

      if (action == MotionEvent.ACTION_MOVE) {
//        Log.d("diskusage", "multi move");
        float xmin, xmax, ymin, ymax;
        ymin = ymax = getFilterX(0).doFilter(ev.getY(0));
        xmin = xmax = getFilterY(0).doFilter(ev.getX(0));
        for (int i = 1; i < num; i++) {
          float x = getFilterX(i).doFilter(ev.getX(i));
          float y = getFilterY(i).doFilter(ev.getY(i));
          if (x < xmin) xmin = x;
          if (x > xmax) xmax = x;
          if (y < ymin) ymin = y;
          if (y > ymax) ymax = y;
        }
        if (multitouchReset) {
//          Log.d("diskusage", "multi move: reset");
          multitouchReset = false;
          multiNumTouches = num;
          touchMovement = true;
          float dy = ymax - ymin;
          if (dy < minDistance) dy = minDistance;
          float avg_y = 0.5f * (ymax + ymin);
          touchZoom = (displayBottom - displayTop) * (long)dy / screenHeight;
          touchPoint = displayTop + (displayBottom - displayTop) * (long)avg_y / screenHeight;

          float avg_x = 0.5f * (xmax + xmin);
          float dx = xmax - xmin;
          minDistanceX = FileSystemEntry.elementWidth / 2;
          if (dx < minDistanceX) dx = minDistanceX;
          touchWidth = dx / FileSystemEntry.elementWidth;
          touchPointX = viewDepth + avg_x / FileSystemEntry.elementWidth; 
          //            Log.d("diskusage", "multitouch reset " + avg_x + " : " + dx);
          return true;
        }
        float dy = ymax - ymin;
        if (dy < minDistance) dy = minDistance;
        long displayBottom_Top = touchZoom * screenHeight / (long) dy;
        float avg_y = 0.5f * (ymax + ymin);
        displayTop = touchPoint - displayBottom_Top * (long) avg_y / screenHeight;
        displayBottom = displayTop + displayBottom_Top;

        float avg_x = 0.5f * (xmax + xmin);
        float dx = xmax - xmin;
        if (dx < minDistanceX) dx = minDistanceX;
        FileSystemEntry.elementWidth = (int) (dx / touchWidth);

        if (FileSystemEntry.elementWidth < minElementWidth)
          FileSystemEntry.elementWidth = minElementWidth;
//        else if (FileSystemEntry.elementWidth > maxElementWidth)
//          FileSystemEntry.elementWidth = maxElementWidth;
        targetElementWidth = FileSystemEntry.elementWidth;

        targetViewDepth = viewDepth = touchPointX - avg_x / FileSystemEntry.elementWidth;
        maxLevels = screenWidth / (float) FileSystemEntry.elementWidth;
        //          Log.d("diskusage", "multitouch " + avg_x + " : " + dx + "(" + old_dx + ")");

        long dt = (displayBottom - displayTop) / 41;
        if (dt < 2) {
          displayBottom += 41 * 2; 
        }
        viewTop = displayTop + dt;
        viewBottom = displayBottom - dt;

        targetViewTop = viewTop;
        targetViewBottom = viewBottom;
        animationStartTime = 0;
        FileSystemView.this.invalidate();
        return true;
      }
      return true;
    }
  };
  
  VersionedMultitouchHandler multitouchHandler =
    VersionedMultitouchHandler.newInstance(this);
  
  public void onMotion(float newTouchX, float newTouchY, long moveTime) {
    float touchOffsetX = newTouchX - touchX;
    float touchOffsetY = newTouchY - touchY;
    speedX += touchOffsetX;
    speedY += touchOffsetY;
    long dt = moveTime - prevMoveTime; 
    if (dt > 10) {
      speedX *= 10.f / dt;
      speedY *= 10.f / dt;
      prevMoveTime = moveTime - 10;
    }
    
    if (Math.abs(touchOffsetX) < 10 && Math.abs(touchOffsetY)< 10 && !touchMovement)
      return;
    touchMovement = true;
    
    viewDepth -= touchOffsetX / FileSystemEntry.elementWidth;
    if (viewDepth * FileSystemEntry.elementWidth < -screenWidth * 0.6)
      viewDepth = -screenWidth * 0.6f / FileSystemEntry.elementWidth;
    targetViewDepth = viewDepth;

    long offset = (long)(touchOffsetY / yscale);
    long allowedOverflow = (long)(screenHeight * 0.6f / yscale);
    viewTop -= offset;
    viewBottom -= offset;

    if (viewTop < -allowedOverflow) {
      long oldTop = viewTop;
      viewTop = -allowedOverflow;
      viewBottom += viewTop - oldTop;
    }

    if (viewBottom > masterRoot.encodedSize + allowedOverflow) {
      long oldBottom = viewBottom;
      viewBottom = masterRoot.encodedSize + allowedOverflow;
      viewTop += viewBottom - oldBottom;
    }
    
    targetViewTop = viewTop;
    targetViewBottom = viewBottom;
    animationStartTime = 0;
    touchX = newTouchX;
    touchY = newTouchY;
    invalidate();
    return;
  }
  
  static class MotionFilter {
    public static float dx = 5;
    float cur;
    float cur2;
    float dx2;; 
    int idle = 0;
    
    float noFilter(float value) {
      cur = value;
      cur2 = value;
      dx2 = 0;
      return value;
    }
    
    float doFilter(float val) {
      if (val > cur + dx) {
        cur += val - (cur + dx);
        dx2--;
        if (dx2 < 0) dx2 = 0;
        idle = 0;
      } else if (val < cur - dx) {
        cur += val - (cur - dx);
        dx2--;
        if (dx2 < 0) dx2 = 0;
      } else {
        dx2++;
        if (dx2 > dx) dx2 = dx;
      }
      if (val > cur2 + dx2) {
        cur2 += val - (cur2 + dx2);
      } else if (val < cur2 - dx2) {
        cur2 += val - (cur2 - dx2);
      }
      return cur2;
    }
  }
  
  private MotionFilter filterX = new MotionFilter();
  private MotionFilter filterY = new MotionFilter();

  @Override
  public final boolean onTouchEvent(MotionEvent ev) {
    if (sdcardIsEmpty())
      return true;
    
    if (deletingEntry != null) {
      // setup state of multitouch to reinitialize next time
      multiNumTouches = 0;
      return true;
    }
    
    if (multitouchHandler.handleTouch(ev))
      return true;

    float newTouchX = ev.getX();
    float newTouchY = ev.getY();

    int action = ev.getAction();
    
    if (multiNumTouches > 1) {
      if (action == MotionEvent.ACTION_UP) {
        multiNumTouches = 0;
        screenTouching = false;
        invalidate();
      }
      return true;
    }

    if (action == MotionEvent.ACTION_DOWN) {
      screenTouching = true;
      multiNumTouches = 1;
      multitouchReset = true;
      newTouchX = filterX.noFilter(newTouchX);
      newTouchY = filterY.noFilter(newTouchY);
      touchX = newTouchX;
      touchY = newTouchY;
      touchDepth = (FileSystemEntry.elementWidth * viewDepth + touchX) /
      FileSystemEntry.elementWidth;
      touchPoint = displayTop + (displayBottom - displayTop) * (long)touchY / screenHeight;
      touchEntry = masterRoot.findEntry((int)touchDepth + 1, touchPoint);
      if (touchEntry == masterRoot) {
        touchEntry = null;
        Log.d("diskusage", "warning: masterRoot selected in onTouchEvent");
      }
      speedX = 0;
      speedY = 0;
      prevMoveTime = ev.getEventTime();
    } else if (action == MotionEvent.ACTION_MOVE) {
      long moveTime = ev.getEventTime();
      newTouchX = filterX.doFilter(newTouchX);
      newTouchY = filterY.doFilter(newTouchY);
      onMotion(newTouchX, newTouchY, moveTime);
      return true;
    } else if (action == MotionEvent.ACTION_UP) {
      screenTouching = false;
      newTouchX = filterX.doFilter(newTouchX);
      newTouchY = filterY.doFilter(newTouchY);
      // This prevents first touch after pinch-zoom, removed
      //      if (multiNumTouches != 1) return true;
      
      if (!touchMovement) {
        if (touchEntry == null) {
          Log.d("diskusage", "touchEntry == null");
          return true;
        }
        if (masterRoot.depth(touchEntry) > (int)touchDepth + 1) return true;
        touchSelect(touchEntry);
        return true;
      }
      touchMovement = false;
      
      { // copy paste, fling
        float touchOffsetX = speedX * 15;
        float touchOffsetY = speedY * 15;
        targetViewDepth -= touchOffsetX / FileSystemEntry.elementWidth;
        if (targetViewDepth * FileSystemEntry.elementWidth < -screenWidth * 0.6)
          targetViewDepth = -screenWidth * 0.6f / FileSystemEntry.elementWidth;


        long offset = (long)(touchOffsetY / yscale);
        long allowedOverflow = (long)(screenHeight * 0.6f / yscale);
        targetViewTop -= offset;
        targetViewBottom -= offset;

        if (targetViewTop < -allowedOverflow) {
          long oldTop = targetViewTop;
          targetViewTop = -allowedOverflow;
          targetViewBottom += targetViewTop - oldTop;
        }

        if (targetViewBottom > masterRoot.encodedSize + allowedOverflow) {
          long oldBottom = targetViewBottom;
          targetViewBottom = masterRoot.encodedSize + allowedOverflow;
          targetViewTop += targetViewBottom - oldBottom;
        }
      }
      
      if (animationStartTime != 0) return true;
      prepareMotion();
      animationDuration = 300;
      invalidate();
    }
    return true;
  }
  
  public FileSystemView(
      DiskUsage context, FileSystemRoot root) {
    super(context);
    this.context = context;

    //this.viewRoot = root;
    this.masterRoot = root;
    updateSpecialEntries();
    zoomState = ZoomState.ZOOM_ALLOCATED;
//    this.setBackgroundColor(0x80000000);
//    this.setBackgroundDrawable(null);
    this.setFocusable(true);
    this.setFocusableInTouchMode(true);
    targetViewBottom = root.encodedSize;
    cursor = new Cursor(masterRoot);
    setBackgroundColor(Color.GRAY);
  }
  
  private void updateSpecialEntries() {
    numSpecialEntries = 0;
    freeSpace = null;
    systemSpace = null;
    freeSpaceZoom = 0;
    if (masterRoot.children == null) return;
    FileSystemEntry root = masterRoot.children[0];
    if (root.children == null) return;
    for (FileSystemEntry e : root.children) {
      if (e instanceof FileSystemSystemSpace) {
        systemSpace = (FileSystemSystemSpace) e;
        numSpecialEntries++;
      }
      if (e instanceof FileSystemFreeSpace) {
        numSpecialEntries++;
        freeSpace = (FileSystemFreeSpace) e;
      }
    }
  }
  
  private Bitmap cacheBitmap;
  private Canvas cacheCanvas;
  private long cacheTop;
  private long cacheBottom;
  private float cacheMinDepth;
  private float cacheMaxDepth;
  
  private void makeCacheBitmap() {
    Log.d("DiskUsage", "making new cache");
    // make movement prediction
    float preX = 0, postX = 0, preY = 0, postY = 0;
/*    if (touchOffsetX > 0) {
            preX = (int)Math.min(touchOffsetX * 5, touchX);
    }

    if (touchOffsetX < 0) {
            postX = (int)Math.min(-touchOffsetX * 5, screenWidth - touchX);
    }
    if (touchOffsetY > 0) {
            preY = (int)Math.min(touchOffsetY * 5, touchY);
    }

    if (touchOffsetY < 0) {
            postX = (int)Math.min(-touchOffsetY * 5, screenWidth - touchY);
    }*/
    preX = postX = screenWidth / 3;
    preY = postY = screenHeight / 2;
    

    if (cacheBitmap == null || cacheBitmap.isRecycled()) {
      cacheBitmap = Bitmap.createBitmap(
                    (int)(screenWidth + preX + postX),
                    (int)(screenHeight + preY + postY), Bitmap.Config.RGB_565);
      cacheCanvas = new Canvas(cacheBitmap);
      cacheCanvas.drawColor(Color.GRAY);
    } else {
      cacheCanvas.drawColor(Color.GRAY);
    }

    cacheTop = viewTop - (long)(preY / yscale);
    cacheBottom = viewBottom + (long) (postY / yscale);
    cacheMinDepth = viewDepth - preX / FileSystemEntry.elementWidth;
    cacheMaxDepth = viewDepth + postX / FileSystemEntry.elementWidth;
    paintSlow(cacheCanvas, cacheTop, cacheBottom, cacheMinDepth, new Rect(), screenHeight * 2);
  }
  
  private final void paintCached(final Canvas canvas) {
    Log.d("DiskUsage", "painting cached");
    yscale = screenHeight / (float)(this.viewBottom - this.viewTop);
    int yoffset = (int)((viewTop - cacheTop) * yscale);
    int xoffset = (int)((viewDepth - cacheMinDepth) * FileSystemEntry.elementWidth);
    canvas.drawBitmap(cacheBitmap, new Rect(
        xoffset, yoffset,
        xoffset + screenWidth,
        yoffset + screenHeight),
        new Rect(0, 0, screenWidth, screenHeight), null);
    
  }
  
  private final boolean checkCacheValid() {
    if (cacheBitmap == null || cacheBitmap.isRecycled()) return false;
    if (viewTop < cacheTop) return false;
    if (viewBottom > cacheBottom) return false;
    if (viewDepth < cacheMinDepth || viewDepth > cacheMaxDepth) return false;
    return true;
  }
  
  
  private final void paintSlow(final Canvas canvas,
      long viewTop, long viewBottom, float viewDepth, Rect bounds, int screenHeight) {
    //Log.d("DiskUsage", "painting slow");
//    Paint p = new Paint();
//    p.setColor(Color.GRAY);
//    p.setAlpha(100);
//
//    canvas.drawColor(p.getColor());
    if (bounds.bottom != 0 || bounds.top != 0 || bounds.left != 0 || bounds.right != 0) {
      // Log.d("DiskUsage", "bounds: " + bounds);
      masterRoot.paint(canvas, bounds, cursor, viewTop, viewDepth, yscale, screenHeight, numSpecialEntries);
//      Paint p = new Paint();
//      p.setColor(Color.RED);
//      p.setStyle(Style.STROKE);
//      canvas.drawRect(bounds, p);
    } else {
      Rect bounds2 = new Rect(0, 0, screenWidth, screenHeight);
      masterRoot.paint(canvas, bounds2, cursor, viewTop, viewDepth, yscale, screenHeight, numSpecialEntries);
    }
  }
  
  @Override
  protected final void onDraw(final Canvas canvas) {
    FileSystemEntry.setupStrings(context);
    fadeAwayEntry(this);
    if (titleNeedUpdate) {
      context.setTitle(format(R.string.title_for_path, cursor.position.toTitleString()));
      titleNeedUpdate = false;
    }

    try {
      boolean animation = false;
      long curr = System.currentTimeMillis();
      if (curr > animationStartTime + animationDuration) {
        // no animation
        viewTop = targetViewTop;
        viewBottom = targetViewBottom;
        viewDepth = targetViewDepth;
        FileSystemEntry.elementWidth = targetElementWidth;
        maxLevels = screenWidth / (float) targetElementWidth;
      } else {
        float f = interpolator.getInterpolation((curr - animationStartTime) / (float) animationDuration);
        viewTop = (long)(f * targetViewTop + (1-f) * prevViewTop);
        viewBottom = (long)(f * targetViewBottom + (1-f) * prevViewBottom); 
        viewDepth = f * targetViewDepth + (1-f) * prevViewDepth;
        FileSystemEntry.elementWidth = (int)(f * targetElementWidth + (1-f) * prevElementWidth);

        animation = true;
      }

      Rect bounds = canvas.getClipBounds();
      long dt = (viewBottom - viewTop) / 40;
      displayTop = viewTop - dt;
      displayBottom = viewBottom + dt;
      
      long align = (displayBottom - displayTop) / screenHeight;
      displayTop = displayTop / align * align;
      displayBottom = displayTop + align * screenHeight;
      
      yscale = screenHeight / (float)(displayBottom - displayTop);
      if (useCache && (touchMovement || animationDuration == 300)) {
        if (!checkCacheValid()) makeCacheBitmap();
        paintCached(canvas);
      } else {
        cacheBitmap = null;
        paintSlow(canvas, displayTop, displayBottom, viewDepth, bounds, screenHeight);
      }

      if (animation) {
        invalidate();
      } else if (!screenTouching) {
        if (targetViewTop < 0 || targetViewBottom > masterRoot.encodedSize
            || viewDepth < 0 || FileSystemEntry.elementWidth > maxElementWidth) {
          prepareMotion();
          animationDuration = 300;
          invalidate();
          if (targetViewTop < 0) {
            long oldTop = targetViewTop;
            targetViewTop = 0;
            targetViewBottom += targetViewTop - oldTop;
          } else if (targetViewBottom > masterRoot.encodedSize) {
            long oldBottom = targetViewBottom;
            targetViewBottom = masterRoot.encodedSize;
            targetViewTop += targetViewBottom - oldBottom;
          }
          if (targetViewTop < 0) {
            targetViewTop = 0;
          }
          
          if (targetViewBottom > masterRoot.encodedSize) {
            targetViewBottom = masterRoot.encodedSize;
          }

          if (viewDepth < 0) {
            targetViewDepth = 0;
          }
          if (targetElementWidth > maxElementWidth) {
            targetElementWidth = maxElementWidth;
          }
        }
      }

    } catch (Throwable t) {
      Log.d("DiskUsage", "Got exception", t);
    }
  }
  
  private String format(int id, Object... args) {
    return context.getString(id, args);
  }
  
  private String str(int id) {
    return context.getString(id);
  }

  final void prepareMotion() {
    Log.d("diskusage", "prepare motion");
    animationDuration = 900;
    prevViewDepth = viewDepth;
    prevViewTop = viewTop;
    prevViewBottom = viewBottom;
    prevElementWidth = FileSystemEntry.elementWidth;
    animationStartTime = System.currentTimeMillis();
  }
  
  final void invalidate(Cursor cursor) {
    float cursorx0 = (cursor.depth - viewDepth) * FileSystemEntry.elementWidth;
    float cursory0 = (cursor.top - displayTop) * yscale;
    float cursorx1 = cursorx0 + FileSystemEntry.elementWidth;
    float cursory1 = cursory0 + cursor.position.encodedSize * yscale;
    invalidate((int)cursorx0, (int)cursory0, (int)cursorx1 + 2, (int)cursory1 + 2);
  }
  
  long prevMoveTime;

  /*
   * TODO:
   * Add Message to the screen in DeleteActivity
   * Check that DeleteActivity has right title
   * multitouch on eclair
   * Fling works bad on eclair, use 10ms approximation for last movement
   */
  private void touchSelect(FileSystemEntry entry) {
    FileSystemEntry prevCursor = cursor.position;
    int prevDepth = cursor.depth;
    cursor.set(this, entry);
    int currDepth = cursor.depth;
    prepareMotion();
    
    if ((entry == masterRoot.children[0]) || (entry instanceof FileSystemFreeSpace)) {
      Log.d("diskusage", "special case for " + entry.name);
      toggleZoomState();
      return;
    }
    
    zoomState = ZoomState.ZOOM_OTHER;
    
    zoomFitLabelMoveUp();
    zoomFitToScreen();
    boolean has_children = entry.children != null && entry.children.length != 0; 
    if (!has_children) {
      Log.d("diskusage", "zoom file");
      fullZoom = false;
      if (targetViewTop == prevViewTop
          && targetViewBottom == prevViewBottom) {
        if ((!warnOnFileSelect) && (!(entry instanceof FileSystemSystemSpace))) {
          Toast.makeText(context,
              "Press menu to preview or delete", Toast.LENGTH_SHORT).show();
          warnOnFileSelect = true;
        }
      }
      float minRequiredDepth = cursor.depth + 1 + (has_children ? 1 : 0) - maxLevels;
      if (targetViewDepth < minRequiredDepth) {
        targetViewDepth = minRequiredDepth;
      }
      return;
    } else if (prevCursor == entry) {
      Log.d("diskusage", "zoom toggle same element");
      fullZoom = !fullZoom;
    } else if (currDepth < prevDepth) {
      Log.d("diskusage", "zoom false");
      fullZoom = false;
    } else {
      if (entry.encodedSize * yscale > FileSystemEntry.fontSize * 2) {
        fullZoom = true;
      } else {
        fullZoom = false;
        
      }
    }

    float maxRequiredDepth = cursor.depth - (cursor.depth > 0 ? 1 : 0);
    float minRequiredDepth = cursor.depth + 1 + (has_children ? 1 : 0) - maxLevels;
    if (minRequiredDepth > maxRequiredDepth) {
      Log.d("diskusage", "zoom levels overlap, fullZoom = " + fullZoom);
      if (fullZoom) {
        maxRequiredDepth = minRequiredDepth;
      } else {
        minRequiredDepth = maxRequiredDepth;
      }
    }
    
    if (targetViewDepth < minRequiredDepth) {
      targetViewDepth = minRequiredDepth;
    } else if (targetViewDepth > maxRequiredDepth) {
      targetViewDepth = maxRequiredDepth;
    }
    if (fullZoom) {
      targetViewTop = cursor.top;
      targetViewBottom = cursor.top + cursor.position.encodedSize;
    } else {
    }
    if (targetViewBottom == prevViewBottom && targetViewTop == prevViewTop) {
      fullZoom = false;
      targetViewTop = cursor.top + 1;
      targetViewBottom = cursor.top + cursor.position.encodedSize - 1;
      zoomFitLabelMoveUp();
      zoomFitToScreen();
    }
    long freeSpaceClip = getFreeSpaceZoom();
    if (targetViewBottom > freeSpaceClip) {
      targetViewBottom = freeSpaceClip;
      if (targetViewTop == 0) zoomState = ZoomState.ZOOM_ALLOCATED;
    }
  }
  
  public final void zoomFitLabel() {
    if (cursor.position.encodedSize == 0) {
      //Log.d("DiskUsage", "position is of zero size");
      return;
    }
    
    float yscale = screenHeight / (float)(targetViewBottom - targetViewTop);

    if (cursor.position.encodedSize * yscale > FileSystemEntry.fontSize * 2 + 2) {
      //Log.d("DiskUsage", "position large enough to contain label");
    } else {
      //Log.d("DiskUsage", "zoom in");
      float new_yscale = FileSystemEntry.fontSize * 2.5f / cursor.position.encodedSize;
      prepareMotion();

      targetViewTop = targetViewBottom - (long) (screenHeight / new_yscale);

      if (targetViewTop > cursor.top) {
        //Log.d("DiskUsage", "moving down to fit view after zoom in");
        // 10% from top
        long offset = cursor.top - (long)(targetViewTop * 0.8 + targetViewBottom * 0.2);
        targetViewTop += offset;
        targetViewBottom += offset;

        if (targetViewTop < 0) {
          //Log.d("DiskUsage", "at the top");
          targetViewBottom -= targetViewTop;
          targetViewTop = 0;
        }
      }
    }
  }

  public final void zoomFitLabelMoveUp() {
    if (cursor.position.encodedSize == 0) {
      //Log.d("DiskUsage", "position is of zero size");
      return;
    }
    
    zoomFitLabel();

    if (targetViewBottom < cursor.top + cursor.position.encodedSize) {
      //Log.d("DiskUsage", "move up as needed");
      prepareMotion();

      long offset = cursor.top + cursor.position.encodedSize 
      - (long)(targetViewTop * 0.2 + targetViewBottom * 0.8);
      targetViewTop += offset;
      targetViewBottom += offset;
      if (targetViewBottom > masterRoot.encodedSize) {
        long diff = targetViewBottom - masterRoot.encodedSize;
        targetViewBottom = masterRoot.encodedSize;
        targetViewTop -= diff;
      }
    }
    invalidate();
  }

  public final void zoomFitToScreen() {
    if (targetViewTop < cursor.top &&
        targetViewBottom > cursor.top + cursor.position.encodedSize) {

      // Log.d("DiskUsage", "fits in, no need for zoom out");
      return;
    }

    //Log.d("DiskUsage", "zoom out");

    prepareMotion();

    FileSystemEntry viewRoot = cursor.position.parent;
    targetViewTop = masterRoot.getOffset(viewRoot);
    long size = viewRoot.encodedSize;
    targetViewBottom = targetViewTop + size;
    zoomFitLabelMoveUp();
    invalidate();
  }

  final boolean back() {
    FileSystemEntry newpos = cursor.position.parent;
    if (newpos == masterRoot) {
      return false;
    }
    cursor.set(this, newpos);
    
    if (masterRoot.children != null && newpos == masterRoot.children[0]) {
      prepareMotion();
      zoomState = ZoomState.ZOOM_FULL;
      setZoomState();
      titleNeedUpdate = true;
      return true;
    }

    int requiredDepth = cursor.depth - (cursor.position.parent == masterRoot ? 0 : 1); 
    if (targetViewDepth > requiredDepth) {
      prepareMotion();
      targetViewDepth = requiredDepth;
    }
    zoomFitToScreen();
    titleNeedUpdate = true;
    return true;
  }
  
//  public void onOptionItemSelected(MenuItem item) {
//    // FIXME: use id instead
//    String title = item.getTitle().toString();
//    FileSystemEntry entry = cursor.position;
//
//    if (title.equals("Show")) {
//      String path = entry.path2();
//      Log.d("DiskUsage", "show " + path);
//      FileSystemView.this.view(entry);
//      return;
//    } else if (title.equals("Rescan")) {
//      context.LoadFiles(context, new AfterLoad() {
//        public void run(FileSystemEntry newRoot, boolean isCached) {
//          rescanFinished(newRoot);
//          if (!isCached) startZoomAnimation();
//        }
//      }, true);
//      return;
//    } else if (title.equals("Delete")) {
//      String path = entry.path2();
//      Log.d("DiskUsage", "ask for deletion of " + path);
//      FileSystemView.this.askForDeletion(entry);
//      return;
//    }
//  }

  
  public void onPrepareOptionsMenu(Menu menu) {
    //Log.d("DiskUsage", "onCreateContextMenu");
    menu.clear();
    boolean showFileMenu = false;
    FileSystemEntry entry = null;

    if (!sdcardIsEmpty()) {
      entry = cursor.position;
      // FIXME: hack to disable removal of /sdcard
      if (entry == masterRoot.children[0] || entry instanceof FileSystemSpecial) {
        // Toast.makeText(context, "Select directory or file first", Toast.LENGTH_SHORT).show();
      } else {
        showFileMenu = true;
      }
    }
    
    final FileSystemEntry menuForEntry = entry;

    menu.add(str(R.string.button_show))
    .setEnabled(showFileMenu)
    .setOnMenuItemClickListener(new OnMenuItemClickListener() {
      public boolean onMenuItemClick(MenuItem item) {
        String path = menuForEntry.path2();
        Log.d("DiskUsage", "show " + path);
        FileSystemView.this.view(menuForEntry);
        return true;
      }
    });
    
    menu.add(context.getString(R.string.button_rescan))
    .setOnMenuItemClickListener(new OnMenuItemClickListener() {
      public boolean onMenuItemClick(MenuItem item) {
        context.LoadFiles(context, new AfterLoad() {
          public void run(FileSystemRoot newRoot, boolean isCached) {
            rescanFinished(newRoot);
            if (!isCached) startZoomAnimation();
          }
        }, true);
        return true;
      }
    });
    menu.add(str(R.string.button_delete))
    .setEnabled(showFileMenu)
    .setOnMenuItemClickListener(new OnMenuItemClickListener() {
      public boolean onMenuItemClick(MenuItem item) {
        String path = menuForEntry.path2();
        Log.d("DiskUsage", "ask for deletion of " + path);
        FileSystemView.this.askForDeletion(menuForEntry);
        return true;
      }
    });
  }
  
  public void rescanFinished(FileSystemRoot newRoot) {
    masterRoot = newRoot;
    updateSpecialEntries();
    cursor = new Cursor(masterRoot);
    titleNeedUpdate = true;
    invalidate();
  }
  
  private void view(FileSystemEntry entry) {
    Intent intent = new Intent(Intent.ACTION_VIEW);
    intent.addCategory(Intent.CATEGORY_DEFAULT);
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    if (entry instanceof FileSystemEntrySmall) {
      entry = entry.parent;
    }
    File file = new File(context.getRootPath() + "/" + entry.path2());
    Uri uri = Uri.fromFile(file);
    
    if (file.isDirectory()) {
      intent = new Intent(Intent.ACTION_VIEW);
      intent.addCategory(Intent.CATEGORY_DEFAULT);
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      intent.setDataAndType(uri, "vnd.android.cursor.item/com.metago.filemanager.dir");
      
      try {
        context.startActivity(intent);
        return;
      } catch(ActivityNotFoundException e) {
      }

      intent = new Intent("org.openintents.action.VIEW_DIRECTORY");
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      intent.setData(uri);
      
      try {
        context.startActivity(intent);
        return;
      } catch(ActivityNotFoundException e) {
      }

      intent = new Intent("org.openintents.action.PICK_DIRECTORY");
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      intent.setData(uri);
      intent.putExtra("org.openintents.extra.TITLE",
          str(R.string.title_in_oi_file_manager));
      intent.putExtra("org.openintents.extra.BUTTON_TEXT",
          str(R.string.button_text_in_oi_file_manager));
      try {
        context.startActivity(intent);
        return;
      } catch(ActivityNotFoundException e) {
      }
      Toast.makeText(context, str(R.string.install_oi_file_manager),
          Toast.LENGTH_SHORT).show();
      return;
    }

    String fileName = entry.name;
    int dot = fileName.lastIndexOf(".");
    if (dot != -1) {
      String extension = fileName.substring(dot + 1).toLowerCase(); 
      String mime = getMimeByExtension(extension);
      try {
        if (mime != null) {
          intent.setDataAndType(uri, mime);
        } else {
          intent.setDataAndType(uri, "binary/octet-stream");
        }
        context.startActivity(intent);
        return;
      } catch (ActivityNotFoundException e) {
      }
    }
    Toast.makeText(context, str(R.string.no_viewer_found),
        Toast.LENGTH_SHORT).show();
  }
  
  private String getMimeByExtension(String extension) {
    if (extensionToMime == null) {
      initExtensions();
    }
    return extensionToMime.get(extension);
  }

  private void initExtensions() {
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
  
//  private String[] resolveSmallEntry(FileSystemEntrySmall smallEntry) {
//    FileSystemEntry parent = smallEntry.parent;
//    String parentPath = parent.path2();
//    String[] allNames = new File(context.getRootPath() + "/" + parentPath).list();
//    Set<String> parentFiles = new TreeSet<String>();
//    for (FileSystemEntry child: parent.children) {
//      parentFiles.add(child.name);
//    }
//    List<String> result = new ArrayList<String>();
//    for (String name : allNames) {
//      if (parentFiles.contains(name)) continue;
//      result.add(parentPath + "/" + name);
//    }
//    return result.toArray(new String[result.size()]);
//  }
  
  private void askForDeletion(final FileSystemEntry entry) {
    final String path = entry.path2();
    Log.d("DiskUsage", "Deletion requested for " + path);
    
    if (entry instanceof FileSystemEntrySmall) {
//      // FIXME: find out list of files
//      Intent i = new Intent(context, DeleteActivity.class);
//      i.putExtra("path", resolveSmallEntry((FileSystemEntrySmall) entry));
//      i.putExtra(DiskUsage.KEY_KEY, context.key);
//      i.putExtra(DiskUsage.TITLE_KEY, context.getRootTitle());
//      i.putExtra(DiskUsage.ROOT_KEY, context.getRootPath());
//      i.putExtra(DeleteActivity.NUM_FILES_KEY, entry.getNumFiles());
//      i.putExtra(DeleteActivity.SIZE_KEY, entry.getSizeInBytes());
//      context.startActivityForResult(i, 0);
      Toast.makeText(context,
          "Delete directory instead", Toast.LENGTH_SHORT).show();

      return;
    }
    if (entry.children == null || entry.children.length == 0) {
      if (entry instanceof FileSystemPackage) {
        context.pkg_removed = (FileSystemPackage) entry;
        BackgroundDelete.startDelete(FileSystemView.this, entry);
        return;
      }

      // Delete single file or directory
      new AlertDialog.Builder(this.context)
      .setTitle(new File(context.getRootPath() + "/" + path).isDirectory()
          ? format(R.string.ask_to_delete_directory, path)
          : format(R.string.ask_to_delete_file, path))
      .setPositiveButton(str(R.string.button_delete),
          new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
          BackgroundDelete.startDelete(FileSystemView.this, entry);
        }
      })
      .setNegativeButton(str(R.string.button_cancel),
          new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int whichButton) {
        }
      }).create().show();
    } else {
      Intent i = new Intent(context, DeleteActivity.class);
      i.putExtra("path", path);
      i.putExtra(DeleteActivity.NUM_FILES_KEY, entry.getNumFiles());

      i.putExtra(DiskUsage.KEY_KEY, context.key);
      i.putExtra(DiskUsage.TITLE_KEY, context.getRootTitle());
      i.putExtra(DiskUsage.ROOT_KEY, context.getRootPath());
      i.putExtra(DeleteActivity.SIZE_KEY, entry.sizeString());
      context.startActivityForResult(i, 0);
    }
  }
  
  void continueDelete(String path) {
    FileSystemEntry entry = masterRoot.getEntryByName(path);
    if (entry != null) {
      BackgroundDelete.startDelete(this, entry);
    } else {
      Toast.makeText(getContext(), 
          "Oops. Can't find directory to be deleted.", Toast.LENGTH_SHORT);
    }
  }
  
  public final void moveAwayCursor(FileSystemEntry entry) {
    if (cursor.position != entry) return;
//    FIXME: should not be needed
//    this.invalidate();
//    cursor.set(this, entry);
    try {
      cursor.up(this);
    } catch (RuntimeException e) {
      // getPrev -> getIndexOf() can sometimes when this called from moveAwayCursor()
    }
    if (cursor.position != entry) {
      return;
    }
    cursor.left(this);
    
//    if (cursor.position == entry) {
//      cursor.position = masterRoot.children[0];
//    }
  }
  
  public final void remove(FileSystemEntry entry) {
    stats_num_deletions++;
//    this.invalidate();
//    cursor.set(this, entry);
//    cursor.up(this);
//    if (cursor.position == entry) {
//      cursor.left(this);
//    }
//    if (cursor.position == entry) {
//      cursor.position = masterRoot.children[0];
//    }
    fadeAwayEntryStart(entry, this);
    invalidate();
//    cursor.refresh(this);
  }
  
  private FileSystemEntry deletingEntry = null;
  private long deletingAnimationStartTime = 0;
  private long deletingInitialSize;
  
  public final void deleteDeletingEntry() {
    if (deletingEntry.parent == masterRoot) {
      throw new RuntimeException("sdcard deletion is not available in UI");
    }
    int displayBlockSize = masterRoot.getDisplayBlockSize(); 
    moveAwayCursor(deletingEntry);
    deletingEntry.remove(displayBlockSize);
    long deletingEntryBlocks = deletingEntry.getSizeInBlocks();
    if (freeSpace != null) {
      freeSpace.setSizeInBlocks(freeSpace.getSizeInBlocks() + deletingEntryBlocks, displayBlockSize);
      masterRoot.setSizeInBlocks(masterRoot.getSizeInBlocks() + deletingEntryBlocks, displayBlockSize);
      masterRoot.children[0].setSizeInBlocks(masterRoot.children[0].getSizeInBlocks()
          + deletingEntryBlocks, displayBlockSize);
      freeSpace.sizeString = null;
    }

    FileSystemEntry.deletedEntry = null;
    FileSystemEntry parent = deletingEntry.parent;
    
    long freeSpaceEncoded = 0, systemSpaceEncoded = 0;
    if (freeSpace != null) {
      freeSpaceEncoded = freeSpace.encodedSize;
      freeSpace.encodedSize = -2;
    }
    
    if (systemSpace != null) {
      systemSpaceEncoded = systemSpace.encodedSize;
      systemSpace.encodedSize = -1;
    }
    // Sort elements otherwise painting code works incorrect
    while (parent != null) {
      Arrays.sort(parent.children, FileSystemEntry.COMPARE);
      parent = parent.parent;
    }
    
    for (FileSystemEntry e : masterRoot.children[0].children) {
      Log.d("diskusage", "entry = " + e.name + " " + e.encodedSize);
    }
    
    if (freeSpace != null) {
      freeSpace.encodedSize = freeSpaceEncoded;
    }
    if (systemSpace != null) {
      systemSpace.encodedSize = systemSpaceEncoded;
    }
    deletingEntry = null;
  }
  
  public final void fadeAwayEntryStart(FileSystemEntry entry, FileSystemView view) {
    if (deletingEntry != null) {
      deleteDeletingEntry();
    }
    deletingAnimationStartTime = 0;
    deletingEntry = entry;
    FileSystemEntry.deletedEntry = entry;
    deletingInitialSize = entry.getSizeInBlocks();
  }
  
  public final void fadeAwayEntry(FileSystemView view) {
    FileSystemEntry entry = deletingEntry;
    if (entry == null) return;
//    Log.d("diskusage", "deletion in progress");
    
    long time = System.currentTimeMillis();
    
    if (deletingAnimationStartTime == 0) {
      deletingAnimationStartTime = time;
    }
    long dt = time - deletingAnimationStartTime;
//    Log.d("diskusage", "dt = + " + dt);
    if (dt > deletionAnimationDuration) {
      deleteDeletingEntry();
      return;
    }
    this.invalidate();
    float f = interpolator.getInterpolation(dt / (float) animationDuration);
//    Log.d("diskusage", "f = + " + f);
    long prevSize = entry.getSizeInBlocks();
    long newBlocks = (long)((1 - f) * deletingInitialSize);
//    Log.d("diskusage", "newSize = + " + newSize);
    long dSize = newBlocks - prevSize;
    
    if (dSize >= 0) return;
    
    FileSystemEntry parent = entry.parent;
    int displayBlockSize = masterRoot.getDisplayBlockSize(); 
    
    while (parent != null) {
      parent.setSizeInBlocks(parent.getSizeInBlocks() + dSize, displayBlockSize);
      parent = parent.parent;
    }
    if (freeSpace != null) {
      masterRoot.setSizeInBlocks(masterRoot.getSizeInBlocks() - dSize, displayBlockSize);
      masterRoot.children[0].setSizeInBlocks(masterRoot.children[0].getSizeInBlocks()
          - dSize, displayBlockSize);
      freeSpace.setSizeInBlocks(freeSpace.getSizeInBlocks() - dSize, displayBlockSize);
    }
    // truncate children
    while (true) {
      long deltaBlocks = newBlocks - entry.getSizeInBlocks();
      if (deltaBlocks == 0) return;
      entry.setSizeInBlocks(entry.getSizeInBlocks() + deltaBlocks, displayBlockSize);
      if (entry.children == null || entry.children.length == 0)
        return;
      FileSystemEntry[] children = entry.children;
      long blocks = 0;
      FileSystemEntry prevEntry = entry;
      for (int i = 0; i < children.length; i++) {
        blocks += children[i].getSizeInBlocks();
        // if sum of sizes of children less then newSize continue
        if (newBlocks > blocks) continue;
        
        // size of children larger than newSize, need to trunc last child
        long lastChildSizeChange = blocks - newBlocks;
        long newChildSize = children[i].getSizeInBlocks() - lastChildSizeChange;
        // size of last child will be updated at the begining of while loop
        newBlocks = newChildSize;
        
        FileSystemEntry[] newChildren = new FileSystemEntry[i + 1];
        System.arraycopy(children, 0, newChildren, 0, i + 1);
        entry.children = newChildren;
        entry = children[i];
        break;
      }
      if (prevEntry == entry) {
        // Entry was truncated, but not its children
        break;
      }
    }
  }
  
  public final void restore(FileSystemEntry entry) {
    if (deletingEntry != null) {
      deleteDeletingEntry();
    }
    // if (cursor.position == masterRoot)
    //   cursor.position = entry;
    // cursor.refresh(this);
    // zoomCursor();
    // zoomOutCursor();
  }
  
  public final boolean sdcardIsEmpty() {
    return cursor.position == masterRoot;
  }
  
  @Override
  public final boolean onKeyDown(final int keyCode, final KeyEvent event) {
    if (sdcardIsEmpty())
      return super.onKeyDown(keyCode, event);
    
    if (keyCode == KeyEvent.KEYCODE_BACK) {
      Bundle outState = new Bundle();
      context.onSaveInstanceState(outState);
      Intent result = new Intent();
      result.putExtra(DiskUsage.STATE_KEY, outState);
      result.putExtra(DiskUsage.KEY_KEY, context.key);
      context.setResult(0, result);
      context.finish();
      return true;
    }
    
    if (deletingEntry != null) {
      switch (keyCode) {
      case KeyEvent.KEYCODE_DPAD_CENTER:
      case KeyEvent.KEYCODE_DPAD_LEFT:
      case KeyEvent.KEYCODE_DPAD_RIGHT:
      case KeyEvent.KEYCODE_DPAD_UP:
      case KeyEvent.KEYCODE_DPAD_DOWN:
        return true;
      }
      return super.onKeyDown(keyCode, event);
    }
    
    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
      cursor.down(this);
      zoomFitLabelMoveUp();
      zoomFitToScreen();
      titleNeedUpdate = true;
      return true; 
    }

    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
      cursor.up(this);
      zoomFitLabel();
      zoomFitToScreen();
      titleNeedUpdate = true;
      return true;
    }

    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
      back();
      return true;
    }

    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
      cursor.right(this);
      zoomFitLabelMoveUp();

      float requiredDepth = cursor.depth + 1 + (cursor.position.children == null ? 0 : 1) - maxLevels;
      if (viewDepth < requiredDepth) {
        prepareMotion();
        targetViewDepth = requiredDepth;
      }
      titleNeedUpdate = true;
      return true;
    }

    if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
      final FileSystemEntry selected = cursor.position;
      // FIXME: hack to disable removal of /sdcard
      if (selected == masterRoot.children[0]) return true;
      view(selected);
    }

    /*if ((keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ENTER)) {
      return back();
    }*/

    //Log.d("DiskUsage", "Key down = " + keyCode + " " + event);
    return super.onKeyDown(keyCode, event);
  }
  
  @Override
  protected final void onLayout(boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);
    screenHeight = getHeight();
    screenWidth = getWidth();
    minElementWidth = screenWidth / 8;
    maxElementWidth = screenWidth / 2;
    // FIXME: may be too large
    MotionFilter.dx = (screenHeight + screenWidth) / 50;

    minDistance = screenHeight > screenWidth ? screenHeight /  10 : screenWidth / 10; 
    Log.d("diskusage", "screen = " + screenWidth + "x" + screenHeight);
    FileSystemEntry.elementWidth = targetElementWidth = (int) (screenWidth / maxLevels);
    titleNeedUpdate = true;
    setZoomState();
  }

  public void restoreState(Bundle inState) {
    String cursorName = inState.getString("cursor");
    if (cursorName == null) return;
    FileSystemEntry entry = masterRoot.getEntryByName(cursorName);
    if (entry == null) return;
    cursor.set(this, entry);
    targetViewDepth = prevViewDepth = viewDepth = inState.getFloat("viewDepth");
    targetViewTop = prevViewTop = viewTop = inState.getLong("viewTop");
    targetViewBottom = prevViewBottom = viewBottom = inState.getLong("viewBottom");
    switch (inState.getInt("zoomState")) {
    case 0: zoomState = ZoomState.ZOOM_ALLOCATED; break;
    case 1: zoomState = ZoomState.ZOOM_FULL; break;
    default: zoomState = ZoomState.ZOOM_OTHER; break;
    }
    maxLevels = inState.getFloat("maxLevels");
  }

  public void saveState(Bundle outState) {
    outState.putString("cursor", cursor.position.path2());
    outState.putFloat("viewDepth", viewDepth);
    outState.putLong("viewTop", viewTop);
    outState.putLong("viewBottom", viewBottom);
    outState.putFloat("maxLevels", maxLevels);
    outState.putInt("zoomState",
        zoomState == ZoomState.ZOOM_ALLOCATED ? 0 : (
            zoomState == ZoomState.ZOOM_FULL ? 1 : 2));
  }

  public final void resetCursor() {
    cursor.set(this, masterRoot.children[0]);
  }

  public void startZoomAnimation() {
    long large = masterRoot.encodedSize * 10;
    long center = masterRoot.encodedSize / 2; 
    viewTop = center - large;
    viewBottom = center + large;
    viewDepth = 0;
    prepareMotion();
    animationDuration = 300;
    targetViewTop = 0;
    targetViewBottom = masterRoot.encodedSize;
    targetViewDepth = 0;
    zoomState = ZoomState.ZOOM_ALLOCATED;
    setZoomState();
  }
  
  private long getFreeSpaceZoom() {
    if (freeSpaceZoom != 0) return freeSpaceZoom;
    if (freeSpace == null) return masterRoot.encodedSize;
    
    freeSpaceZoom = masterRoot.encodedSize;
    FileSystemEntry.setupStrings(context);
    long busy = masterRoot.encodedSize - freeSpace.encodedSize;
    float message = FileSystemEntry.fontSize * 2 + 1f;
    float height = screenHeight / 41f * 40f;
    long required = (long)(busy * (height / (height - message)));
    required *= 40f / 40.5f;
    if (required < freeSpaceZoom * 0.9f)
      freeSpaceZoom = required;
    return freeSpaceZoom;
  }
  
  private void setZoomState() {
    if (screenHeight == 0) return;
    if (zoomState == ZoomState.ZOOM_ALLOCATED) {
      targetViewDepth = 0;
      targetViewTop = 0;
      targetViewBottom = getFreeSpaceZoom();
    } else if (zoomState == ZoomState.ZOOM_FULL) {
      targetViewDepth = 0;
      targetViewTop = 0;
      targetViewBottom = masterRoot.encodedSize;
    }
  }
  private void toggleZoomState() {
    zoomState = (zoomState == ZoomState.ZOOM_ALLOCATED) ?
      ZoomState.ZOOM_FULL : ZoomState.ZOOM_ALLOCATED;
    setZoomState();
  }
  
  private enum ZoomState {
    ZOOM_FULL,
    ZOOM_ALLOCATED,
    ZOOM_OTHER
  };
  ZoomState zoomState = ZoomState.ZOOM_OTHER;
}
