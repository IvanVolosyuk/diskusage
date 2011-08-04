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

import java.util.ArrayList;
import java.util.Arrays;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.Toast;

import com.google.android.diskusage.entity.FileSystemEntry;
import com.google.android.diskusage.entity.FileSystemFreeSpace;
import com.google.android.diskusage.entity.FileSystemSuperRoot;
import com.google.android.diskusage.entity.FileSystemSystemSpace;
import com.google.android.diskusage.opengl.FileSystemViewGPU;
import com.google.android.diskusage.opengl.RenderingThread;

public class FileSystemState {
  
  public interface FileSystemView {
    /** Does nothing in GPU View. */
    public void requestRepaint();
    /** Does nothing in GPU View. */
    public void requestRepaint(int l, int t, int r, int b);
    /** Sends event to wake up rendering thread. */
    public void requestRepaintGPU();
    /** Post event to main thread from other thread. */
    public boolean post(Runnable r);
    /** Run action in renderer thread. */
    public void runInRenderThread(Runnable r);
    public void killRenderThread();
  };
  
  static class MainThreadAction {
    protected DiskUsage context;
    
    public MainThreadAction(DiskUsage context) {
      this.context = context;
    }
    public void updateTitle(FileSystemEntry position) {
      context.setSelectedEntity(position);
    }

    public void warnOnFileSelect() {
      Toast.makeText(context,
          "Press menu to preview or delete", Toast.LENGTH_SHORT).show();
    }
    
    public void view(FileSystemEntry entry) {
      context.view(entry);
    }
    public void finishOnBack() {
      context.finishOnBack();
    }
    public void searchRequest() {
      context.searchRequest();
    }
    
    public MainThreadAction indirect() {
      return new MainThreadActionIndirect(context);
    }
    public MainThreadAction direct() {
      return new MainThreadAction(context);
    }
  }
  
  static class MainThreadActionIndirect extends MainThreadAction {
    public MainThreadActionIndirect(DiskUsage context) {
      super(context);
    }
    
    public void updateTitle(final FileSystemEntry position) {
      context.handler.post(new Runnable() {
        @Override
        public void run() {
          context.setSelectedEntity(position);
        }
      });
    }

    public void warnOnFileSelect() {
      context.handler.post(new Runnable() {
        @Override
        public void run() {
          Toast.makeText(context,
              "Press menu to preview or delete", Toast.LENGTH_SHORT).show();
        }
      });
    }
    
    public void view(final FileSystemEntry entry) {
      context.handler.post(new Runnable() {
        @Override
        public void run() {
          context.view(entry);
        }
      });
    }
    public void finishOnBack() {
      context.handler.post(new Runnable() {
        @Override
        public void run() {
          context.finishOnBack();
        }
      });
    }
    
    public void searchRequest() {
      context.handler.post(new Runnable() {
        @Override
        public void run() {
          context.searchRequest();
        }
      });
    }
  }
  
  private FileSystemView view;
  FileSystemSuperRoot masterRoot;
  //FileSystemEntry viewRoot;
  private Cursor cursor;
//  boolean titleNeedUpdate = false;
  MainThreadAction mainThreadAction;
  
  private int numSpecialEntries = 0;
  private FileSystemFreeSpace freeSpace;
  private FileSystemSystemSpace systemSpace;
  private long freeSpaceZoom = 0;

  private float targetViewDepth;
  private long  targetViewTop;
  private long  targetViewBottom;
  private int   targetElementWidth;

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

  private float yscale;

  private long animationStartTime;
  private Interpolator interpolator = new DecelerateInterpolator();
  private static long animationDuration = 900; 
  private static long deletionAnimationDuration = 900; 
  private float maxLevels = 3.2f;

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
  private float touchWidth;
  private float touchPointX; 
  private float minDistance;
  private float minDistanceX;
  private int minElementWidth;
  private int maxElementWidth;
  
  private int stats_num_deletions = 0;
  private boolean screenTouching;
  
  public static class VersionedMultitouchHandler {
    boolean handleTouch(MyMotionEvent ev) {
      return false;
    }
    private static VersionedMultitouchHandler newInstance(
        FileSystemState view) {
      final int sdkVersion = Integer.parseInt(Build.VERSION.SDK);
      VersionedMultitouchHandler detector = null;
      if (sdkVersion < Build.VERSION_CODES.ECLAIR) {
        detector = new VersionedMultitouchHandler();
      } else {
        detector = view.new MultiTouchHandler();
      }
      return detector;
    }
    
    public MyMotionEvent newMyMotionEvent(MotionEvent ev) {
      MyMotionEvent myev = new MyMotionEvent(ev);
      setupMulti(ev, myev);
      return myev;
    }
    protected void setupMulti(MotionEvent ev, MyMotionEvent myev) {}
  }
  
  private final class MultiTouchHandler extends VersionedMultitouchHandler {
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
    
    @Override
    public void setupMulti(MotionEvent ev, MyMotionEvent myev) {
      int pointerCount;
      float[] xx, yy;
      pointerCount = ev.getPointerCount();
      xx = new float[pointerCount];
      yy = new float[pointerCount];
      for (int i = 0; i < pointerCount; i++) {
        xx[i] = ev.getX(i);
        yy[i] = ev.getY(i);
      }
      myev.setupMulti(pointerCount, xx, yy);
    }
    
    @Override
    boolean handleTouch(MyMotionEvent ev) {
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
        requestRepaint();
        return true;
      }
      return true;
    }
  };
  
  public VersionedMultitouchHandler multitouchHandler =
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
    requestRepaint();
    return;
  }
  
  private static class MotionFilter {
    public static float dx = 5;
    float cur;
    float cur2;
    float dx2;; 
    
    private float noFilter(float value) {
      cur = value;
      cur2 = value;
      dx2 = 0;
      return value;
    }
    
    private float doFilter(float val) {
      if (val > cur + dx) {
        cur += val - (cur + dx);
        dx2--;
        if (dx2 < 0) dx2 = 0;
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
  
  public static class MyMotionEvent {
    long eventTime;
    float x, y;
    float[] xx, yy;
    int action;
    int pointerCount;
    
    public MyMotionEvent(MotionEvent ev) {
      eventTime = ev.getEventTime();
      x = ev.getX();
      y = ev.getY();
      action = ev.getAction();
    }
    
    public void setupMulti(int pointerCount, float[] xx, float[] yy) {
      this.pointerCount = pointerCount;
      this.xx = xx;
      this.yy = yy;
    }

    public float getX() { return x; }
    public float getY() { return y; }
    public int getAction() { return action; }
    public long getEventTime() { return eventTime; }
    public Integer getPointerCount() { return pointerCount; }
    public float getX(int i) { return xx[i]; }
    public float getY(int i) { return yy[i]; }
  }

  public final boolean onTouchEvent(MyMotionEvent ev) {
    try { // finally requestRepaintGPU()
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
        requestRepaint();
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
        touchSelect(touchEntry, ev.getEventTime());
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
      prepareMotion(ev.getEventTime());
      animationDuration = 300;
      requestRepaint();
    }
    
    } finally {
      requestRepaintGPU();
    }
    return true;
  }
  
  public FileSystemState(
      DiskUsage context, FileSystemSuperRoot root) {
    this.mainThreadAction = new MainThreadAction(context);

    zoomState = ZoomState.ZOOM_ALLOCATED;
    targetViewBottom = root.encodedSize;
    //this.viewRoot = root;
    this.masterRoot = root;
    updateSpecialEntries();
    resetCursor();
  }
  
  public void resetCursor() {
    // FIXME: dirty hacks
    cursor = new Cursor(this, masterRoot);
    touchEntry = null;
    touchMovement = false;
  }
  
  private void rescanFinished(FileSystemSuperRoot newRoot) {
    masterRoot = newRoot;
    updateSpecialEntries();
    cursor = new Cursor(this, masterRoot);
    requestRepaint();
    requestRepaintGPU();
  }
  
  public void replaceRootKeepCursor(final FileSystemSuperRoot newRoot,
                                    String searchQuery) {
    view.runInRenderThread(new Runnable() {
      @Override
      public void run() {
        FileSystemEntry oldPosition = cursor.position;
        FileSystemEntry newPosition =
          newRoot.getEntryByName(oldPosition.path2(), false);
        if (newPosition == null) newPosition = newRoot.children[0];
        int newDepth = newRoot.depth(newPosition);
        int oldDepth = masterRoot.depth(cursor.position);
        for (; oldDepth > newDepth; oldDepth--) {
          oldPosition = oldPosition.parent;
        }
        long oldTop = masterRoot.getOffset(oldPosition);
        long oldSize = oldPosition.encodedSize;
        long oldBottom = oldTop + oldSize;
        long newTop = newRoot.getOffset(newPosition);
        long newSize = newPosition.encodedSize;
        long newBottom = newTop + newSize;
        double above = (oldTop - targetViewTop) / (double)oldSize;
        double bellow = (targetViewBottom - oldBottom) / (double)oldSize;
        long newViewTop = newTop - (long)(above * newSize);
        long newViewBottom = (long)(bellow * newSize) + newBottom;
        prepareMotion(SystemClock.uptimeMillis());
        targetViewTop = viewTop = newViewTop;
        targetViewBottom = viewBottom = newViewBottom;
        if (targetViewTop > newTop) targetViewTop = newTop;
        if (targetViewBottom < newBottom) targetViewBottom = newBottom;
        animationDuration = 300;
        rescanFinished(newRoot);
        cursor.set(FileSystemState.this, newPosition);
      }
    });
  }
  
  public void startZoomAnimationInRenderThread(final FileSystemSuperRoot newRoot,
      final boolean animate, final boolean keepCursor) {
    view.runInRenderThread(new Runnable() {
      @Override
      public void run() {
        if (newRoot != null) rescanFinished(newRoot);
        if (animate) {
          long large = masterRoot.encodedSize * 10;
          long center = masterRoot.encodedSize / 2; 
          viewTop = center - large;
          viewBottom = center + large;
          viewDepth = 0;
          prepareMotion(SystemClock.uptimeMillis());
          animationDuration = 300;
          targetViewTop = 0;
          targetViewBottom = masterRoot.encodedSize;
          targetViewDepth = 0;
          zoomState = ZoomState.ZOOM_ALLOCATED;
          setZoomState();
        }
      }
    });
  }
  
  public void defaultZoom() {
    zoomState = ZoomState.ZOOM_ALLOCATED;
    setZoomState();
  }
  
  public void setView(FileSystemView view) {
    this.view = view;
    if (view instanceof FileSystemViewGPU) {
      mainThreadAction = mainThreadAction.indirect();
    } else {
      mainThreadAction = mainThreadAction.direct();
    }
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
  
  private final boolean preDraw() {
    fadeAwayEntry();

    boolean animation = deletingEntry != null;
    long curr = SystemClock.uptimeMillis();
    if (curr > animationStartTime + animationDuration) {
      // no animation
      viewTop = targetViewTop;
      viewBottom = targetViewBottom;
      viewDepth = targetViewDepth;
      FileSystemEntry.elementWidth = targetElementWidth;
      maxLevels = screenWidth / (float) targetElementWidth;
    } else {
      double f = (double) interpolator.getInterpolation((curr - animationStartTime) / (float) animationDuration);
      viewTop = (long)(f * targetViewTop + (1-f) * prevViewTop);
      viewBottom = (long)(f * targetViewBottom + (1-f) * prevViewBottom); 
      viewDepth = (float)(f * targetViewDepth + (1-f) * prevViewDepth);
      FileSystemEntry.elementWidth = (int)(f * targetElementWidth + (1-f) * prevElementWidth);

      animation = true;
    }

    long dt = (viewBottom - viewTop) / 40;
    displayTop = viewTop - dt;
    displayBottom = viewBottom + dt;
    
    yscale = screenHeight / (float)(displayBottom - displayTop);
    return animation;
  }
  
  private final boolean postDraw(boolean animation) {
    boolean needRepaint = false;
    if (animation) {
//      view.requestRepaint();
      return true;
    } else if (!screenTouching) {
      if (targetViewTop < 0 || targetViewBottom > masterRoot.encodedSize
          || viewDepth < 0 || FileSystemEntry.elementWidth > maxElementWidth) {
        prepareMotion(SystemClock.uptimeMillis());
        animationDuration = 300;
//        view.requestRepaint();
        needRepaint = true;
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
    return needRepaint;
  }
  
  private final void paintSlowGPU(final RenderingThread rt,
      long viewTop, long viewBottom, float viewDepth, int screenWidth, int screenHeight) {
    Rect bounds2 = new Rect(0, 0, screenWidth, screenHeight);
    masterRoot.paintGPU(rt, bounds2, cursor, viewTop, viewDepth, yscale, screenHeight, numSpecialEntries);
  }
  
  public boolean onDrawGPU(RenderingThread rt) {
    Log.d("diskusage", "drawFrame (pre) viewTop = " + viewTop + " viewBottom = " + viewBottom);

    try {
      boolean animation = preDraw();
      Log.d("diskusage", "drawFrame viewTop = " + viewTop + " viewBottom = " + viewBottom);
      paintSlowGPU(rt, displayTop, displayBottom, viewDepth, screenWidth, screenHeight);
      return postDraw(animation);
    } catch (Throwable t) {
      Log.d("DiskUsage", "Got exception", t);
    }
    return false;
  }
  
  private final void paintSlow(final Canvas canvas,
      long viewTop, long viewBottom, float viewDepth, Rect bounds, int screenHeight) {
    if (bounds.bottom != 0 || bounds.top != 0 || bounds.left != 0 || bounds.right != 0) {
      masterRoot.paint(canvas, bounds, cursor, viewTop, viewDepth, yscale, screenHeight, numSpecialEntries);
    } else {
      Rect bounds2 = new Rect(0, 0, screenWidth, screenHeight);
      masterRoot.paint(canvas, bounds2, cursor, viewTop, viewDepth, yscale, screenHeight, numSpecialEntries);
    }
  }
  
  public final void onDraw(final Canvas canvas) {
    try {
      boolean animation = preDraw();
      Rect bounds = canvas.getClipBounds();
      paintSlow(canvas, displayTop, displayBottom, viewDepth, bounds, screenHeight);
      boolean needRepaint = postDraw(animation);
      if (needRepaint) {
        requestRepaint();
      }
    } catch (Throwable t) {
      Log.d("DiskUsage", "Got exception", t);
    }
  }
  
  public final void prepareMotion(long time) {
    Log.d("diskusage", "prepare motion");
    animationDuration = 900;
    prevViewDepth = viewDepth;
    prevViewTop = viewTop;
    prevViewBottom = viewBottom;
    prevElementWidth = FileSystemEntry.elementWidth;
    animationStartTime = time;
  }
  
  final void invalidate(Cursor cursor) {
    float cursorx0 = (cursor.depth - viewDepth) * FileSystemEntry.elementWidth;
    float cursory0 = (cursor.top - displayTop) * yscale;
    float cursorx1 = cursorx0 + FileSystemEntry.elementWidth;
    float cursory1 = cursory0 + cursor.position.encodedSize * yscale;
    requestRepaint((int)cursorx0, (int)cursory0, (int)cursorx1 + 2, (int)cursory1 + 2);
  }
  
  long prevMoveTime;

  /*
   * TODO:
   * Add Message to the screen in DeleteActivity
   * Check that DeleteActivity has right title
   * multitouch on eclair
   * Fling works bad on eclair, use 10ms approximation for last movement
   */
  private void touchSelect(FileSystemEntry entry, long eventTime) {
    FileSystemEntry prevCursor = cursor.position;
    int prevDepth = cursor.depth;
    cursor.set(this, entry);
    int currDepth = cursor.depth;
    prepareMotion(eventTime);
    
    if ((entry == masterRoot.children[0]) || (entry instanceof FileSystemFreeSpace)) {
      Log.d("diskusage", "special case for " + entry.name);
      toggleZoomState();
      return;
    }
    
    zoomState = ZoomState.ZOOM_OTHER;
    
    zoomFitLabelMoveUp(eventTime);
    zoomFitToScreen(eventTime);
    boolean has_children = entry.children != null && entry.children.length != 0; 
    if (!has_children) {
      Log.d("diskusage", "zoom file");
      fullZoom = false;
      if (targetViewTop == prevViewTop
          && targetViewBottom == prevViewBottom) {
        if ((!warnOnFileSelect) && (!(entry instanceof FileSystemSystemSpace))) {
          mainThreadAction.warnOnFileSelect();
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
      zoomFitLabelMoveUp(eventTime);
      zoomFitToScreen(eventTime);
    }
    long freeSpaceClip = getFreeSpaceZoom();
    if (targetViewBottom > freeSpaceClip) {
      targetViewBottom = freeSpaceClip;
      if (targetViewTop == 0) zoomState = ZoomState.ZOOM_ALLOCATED;
    }
  }
  
  private final void zoomFitLabel(long eventTime) {
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
      prepareMotion(eventTime);

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

  private final void zoomFitLabelMoveUp(long eventTime) {
    if (cursor.position.encodedSize == 0) {
      //Log.d("DiskUsage", "position is of zero size");
      return;
    }
    
    zoomFitLabel(eventTime);

    if (targetViewBottom < cursor.top + cursor.position.encodedSize) {
      //Log.d("DiskUsage", "move up as needed");
      prepareMotion(eventTime);

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
    requestRepaint();
  }

  private final void zoomFitToScreen(long eventTime) {
    if (targetViewTop < cursor.top &&
        targetViewBottom > cursor.top + cursor.position.encodedSize) {

      // Log.d("DiskUsage", "fits in, no need for zoom out");
      return;
    }

    //Log.d("DiskUsage", "zoom out");

    prepareMotion(eventTime);

    FileSystemEntry viewRoot = cursor.position.parent;
    targetViewTop = masterRoot.getOffset(viewRoot);
    long size = viewRoot.encodedSize;
    targetViewBottom = targetViewTop + size;
    zoomFitLabelMoveUp(eventTime);
    requestRepaint();
  }

  private final boolean back(long eventTime) {
    FileSystemEntry newpos = cursor.position.parent;
    if (newpos == masterRoot) {
      return false;
    }
    cursor.set(this, newpos);
    
    if (masterRoot.children != null && newpos == masterRoot.children[0]) {
      prepareMotion(eventTime);
      zoomState = ZoomState.ZOOM_FULL;
      setZoomState();
      return true;
    }

    int requiredDepth = cursor.depth - (cursor.position.parent == masterRoot ? 0 : 1); 
    if (targetViewDepth > requiredDepth) {
      prepareMotion(eventTime);
      targetViewDepth = requiredDepth;
    }
    zoomFitToScreen(eventTime);
    return true;
  }
  
  public boolean isGPU() {
    return view instanceof FileSystemViewGPU;
  }
  
  private final void moveAwayCursor(FileSystemEntry entry) {
    if (cursor.position != entry) return;
//    FIXME: should not be needed
//    view.requestRepaint();
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
  
  public final void removeInRenderThread(final FileSystemEntry entry) {
    view.runInRenderThread(new Runnable() {
      @Override
      public void run() {
        stats_num_deletions++;
        fadeAwayEntryStart(entry, FileSystemState.this);
      }
    });
    requestRepaintGPU();
    requestRepaint();
  }
  
  private FileSystemEntry deletingEntry = null;
  private long deletingAnimationStartTime = 0;
  private long deletingInitialSize;
  
  private final void deleteDeletingEntry() {
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
      freeSpace.clearDrawingCache();
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
    cursor.set(this, cursor.position);
  }
  
  private final void fadeAwayEntryStart(FileSystemEntry entry, FileSystemState view) {
    if (deletingEntry != null) {
      deleteDeletingEntry();
    }
    deletingAnimationStartTime = 0;
    deletingEntry = entry;
    FileSystemEntry.deletedEntry = entry;
    deletingInitialSize = entry.getSizeInBlocks();
  }
  
  // Should be called from main thread
  void requestRepaint() {
    // Does nothing in GPU View
    view.requestRepaint();
  }
  
  // Should be called from main thread
  private void requestRepaint(int l, int t, int r, int b) {
    // Does nothing in GPU View
    view.requestRepaint(l, t, r, b);
  }
  
  // Should be called from main thread
  void requestRepaintGPU() {
    // Only for GPU View
    view.requestRepaintGPU();
  }
  
  // *** Called from different threads ***
  void post(Runnable r) {
    view.post(r);
  }
  
  private final void fadeAwayEntry() {
    FileSystemEntry entry = deletingEntry;
    if (entry == null) return;
//    Log.d("diskusage", "deletion in progress");
    
    long time = SystemClock.uptimeMillis();
    
    if (deletingAnimationStartTime == 0) {
      deletingAnimationStartTime = time;
    }
    long dt = time - deletingAnimationStartTime;
//    Log.d("diskusage", "dt = + " + dt);
    if (dt > deletionAnimationDuration) {
      deleteDeletingEntry();
      return;
    }
    requestRepaint();
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
    view.runInRenderThread(new Runnable() {
      @Override
      public void run() {
        if (deletingEntry != null) {
          deleteDeletingEntry();
        }
      }
    });
  }
  
  public final boolean sdcardIsEmpty() {
    return cursor.position == masterRoot;
  }
  
  public final boolean onKeyDown(final int keyCode, final KeyEvent event) {
    if (sdcardIsEmpty())
      return false;
    
    try { // finally requestRepaintGPU()
      
    if (keyCode == KeyEvent.KEYCODE_SEARCH) {
      mainThreadAction.searchRequest();
      return true;
    }
    
    if (keyCode == KeyEvent.KEYCODE_BACK) {
      mainThreadAction.finishOnBack();
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
      return false;
    }
    
    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
      cursor.down(this);
      zoomFitLabelMoveUp(event.getEventTime());
      zoomFitToScreen(event.getEventTime());
      return true; 
    }

    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
      cursor.up(this);
      zoomFitLabel(event.getEventTime());
      zoomFitToScreen(event.getEventTime());
      return true;
    }

    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
      back(event.getEventTime());
      return true;
    }

    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
      cursor.right(this);
      zoomFitLabelMoveUp(event.getEventTime());

      float requiredDepth = cursor.depth + 1 + (cursor.position.children == null ? 0 : 1) - maxLevels;
      if (viewDepth < requiredDepth) {
        prepareMotion(event.getEventTime());
        targetViewDepth = requiredDepth;
      }
      return true;
    }

    if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
      final FileSystemEntry selected = cursor.position;
      // FIXME: hack to disable removal of /sdcard
      if (selected == masterRoot.children[0]) return true;
      mainThreadAction.view(selected);
    }

    /*if ((keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ENTER)) {
      return back();
    }*/

    //Log.d("DiskUsage", "Key down = " + keyCode + " " + event);
    } finally {
      requestRepaintGPU();
    }
    return false;
  }
  
  // FIXME: can be called from different thread
  public final void onLayout(
      boolean changed, int left, int top, int right,
      int bottom, int width, int height) {
    screenWidth = width;
    screenHeight = height;
    minElementWidth = screenWidth / 8;
    maxElementWidth = screenWidth / 2;
    // FIXME: may be too large
    MotionFilter.dx = (screenHeight + screenWidth) / 50;

    minDistance = screenHeight > screenWidth ? screenHeight /  10 : screenWidth / 10; 
    Log.d("diskusage", "screen = " + screenWidth + "x" + screenHeight);
    FileSystemEntry.elementWidth = targetElementWidth = (int) (screenWidth / maxLevels);
    setZoomState();
  }

  public void restoreStateInRenderThread(final Bundle inState) {
    view.runInRenderThread(new Runnable() {
      @Override
      public void run() {
        String cursorName = inState.getString("cursor");
        if (cursorName == null) return;
        FileSystemEntry entry = masterRoot.getEntryByName(cursorName, true);
        if (entry == null) return;
        cursor.set(FileSystemState.this, entry);
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
    });
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

  private long getFreeSpaceZoom() {
    if (freeSpaceZoom != 0) return freeSpaceZoom;
    if (freeSpace == null) return masterRoot.encodedSize;
    
    freeSpaceZoom = masterRoot.encodedSize;
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
  private ZoomState zoomState = ZoomState.ZOOM_OTHER;

  public void killRenderThread() {
    view.killRenderThread();
  }

  public void draw300ms() {
    long curr = SystemClock.uptimeMillis();
    if (curr > animationStartTime + animationDuration) {
      viewTop = targetViewTop;
      viewBottom = targetViewBottom;
      prepareMotion(SystemClock.uptimeMillis());
      animationDuration = 300;
    }
  }
}
