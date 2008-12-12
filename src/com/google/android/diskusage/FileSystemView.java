/**
 * 
 */
package com.google.android.diskusage;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
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

import java.io.File;
import java.util.HashMap;

class FileSystemView extends View {
  FileSystemEntry masterRoot;
  //FileSystemEntry viewRoot;
  private Cursor cursor;
  boolean titleNeedUpdate = false;
  DiskUsage context;

  private float targetViewDepth;
  private long  targetViewTop;
  private long  targetViewBottom;

  private float prevViewDepth;
  private long  prevViewTop;
  private long  prevViewBottom;

  private float viewDepth;
  private long  viewTop;
  private long  viewBottom;

  private int screenWidth;
  private int screenHeight;

  float yscale;

  private long animationStartTime;
  private Interpolator interpolator = new DecelerateInterpolator();
  private static long animationDuration = 900; 
  private static final int maxLevels = 4;

  private float touchDepth;
  private long touchPoint;

  private FileSystemEntry touchEntry;
  private float touchX, touchY;
  private boolean touchMovement;
  private float touchOffsetX;
  private float touchOffsetY;
  private final boolean useCache = false;

  private FileSystemEntry menuForEntry;

  static HashMap<String, String> extensionToMime;

  static {
    extensionToMime = new HashMap<String, String>();
    extensionToMime.put("jpg", "image/jpg");
    extensionToMime.put("gif", "image/gif");
    extensionToMime.put("tif", "image/tif");
    extensionToMime.put("tiff", "image/tiff");
    extensionToMime.put("png", "image/png");
    extensionToMime.put("mp3", "audio/mp3");
    extensionToMime.put("wav", "audio/wav");
    extensionToMime.put("flac", "audio/flac");
    extensionToMime.put("ogg", "audio/ogg");
    extensionToMime.put("avi", "video/avi");
    extensionToMime.put("mpg", "video/mpg");
    extensionToMime.put("mp4", "video/mp4");
    extensionToMime.put("3gp", "video/3gp");
    extensionToMime.put("txt", "text/plain");
    extensionToMime.put("html", "text/html");
  }

  public FileSystemView(DiskUsage context, FileSystemEntry root) {
    super(context);
    this.context = context;

    //this.viewRoot = root;
    this.masterRoot = root;
    this.setBackgroundColor(0x80000000);
    this.setFocusable(true);
    this.setFocusableInTouchMode(true);
    targetViewBottom = root.size;

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
    } else {
      cacheCanvas.drawColor(Color.BLACK);
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
    Paint p = new Paint();
    p.setColor(Color.GRAY);
    p.setAlpha(100);

    canvas.drawColor(p.getColor());
    if (bounds.bottom != 0 || bounds.top != 0 || bounds.left != 0 || bounds.right != 0) {
      // Log.d("DiskUsage", "bounds: " + bounds);
      masterRoot.paint(canvas, bounds, cursor, viewTop, viewDepth, yscale, screenHeight);
    } else {
      Rect bounds2 = new Rect(0, 0, screenWidth * 2, screenHeight * 2);
      masterRoot.paint(canvas, bounds2, cursor, viewTop, viewDepth, yscale, screenHeight);
    }
  }

  @Override
  protected final void onDraw(final Canvas canvas) {
    try {
      boolean animation = false;
      long curr = System.currentTimeMillis();
      if (curr > animationStartTime + animationDuration) {
        // no animation
        viewTop = targetViewTop;
        viewBottom = targetViewBottom;
        viewDepth = targetViewDepth;
      } else {
        float f = interpolator.getInterpolation((curr - animationStartTime) / (float) animationDuration);
        viewTop = (long)(f * targetViewTop + (1-f) * prevViewTop);
        viewBottom = (long)(f * targetViewBottom + (1-f) * prevViewBottom); 
        viewDepth = f * targetViewDepth + (1-f) * prevViewDepth;

        animation = true;
      }

      Rect bounds = canvas.getClipBounds();
      yscale = screenHeight / (float)(viewBottom - viewTop);
      if (useCache && (touchMovement || animationDuration == 300)) {
        if (!checkCacheValid()) makeCacheBitmap();
        paintCached(canvas);
      } else {
        cacheBitmap = null;
        paintSlow(canvas, viewTop, viewBottom, viewDepth, bounds, screenHeight);
      }

      if (animation) {
        postInvalidateDelayed(20);
      } else if (titleNeedUpdate) {
        context.setTitle(cursor.position.toString() + " - DiskUsage");
        titleNeedUpdate = false;
      }

    } catch (Throwable t) {
      Log.d("DiskUsage", "Got exception", t);
    }
  }

  final void prepareMotion() {
    animationDuration = 900;
    prevViewDepth = viewDepth;
    prevViewTop = viewTop;
    prevViewBottom = viewBottom;
    animationStartTime = System.currentTimeMillis();
  }
  
  final void invalidate(Cursor cursor) {
    float cursorx0 = (cursor.depth - viewDepth) * FileSystemEntry.elementWidth;
    float cursory0 = (cursor.top - viewTop) * yscale;
    float cursorx1 = cursorx0 + FileSystemEntry.elementWidth;
    float cursory1 = cursory0 + cursor.position.size * yscale;
    invalidate((int)cursorx0, (int)cursory0, (int)cursorx1 + 1, (int)cursory1 + 1);
  }

  @Override
  public final boolean onTouchEvent(MotionEvent ev) {
    if (sdcardIsEmpty())
      return true;

    float newTouchX = ev.getX();
    float newTouchY = ev.getY();

    int action = ev.getAction();

    if (action == MotionEvent.ACTION_DOWN) {
      touchX = newTouchX;
      touchY = newTouchY;
      touchDepth = (FileSystemEntry.elementWidth * viewDepth + touchX) /
      FileSystemEntry.elementWidth;
      touchPoint = viewTop + (viewBottom - viewTop) * (long)touchY / screenHeight;
      touchEntry = masterRoot.findEntry((int)touchDepth + 1, touchPoint);

    } else if (action == MotionEvent.ACTION_MOVE) {
      touchOffsetX = newTouchX - touchX;
      touchOffsetY = newTouchY - touchY;
      if (Math.abs(touchOffsetX) < 10 && Math.abs(touchOffsetY)< 10 && !touchMovement)
        return true;
      touchMovement = true;
      
      viewDepth -= touchOffsetX / FileSystemEntry.elementWidth;
      if (viewDepth < -0.4f) viewDepth = -0.4f;
      targetViewDepth = viewDepth;

      long offset = (long)(touchOffsetY / yscale);
      long allowedOverflow = (long)(screenHeight / 10 / yscale);
      viewTop -= offset;
      viewBottom -= offset;

      if (viewTop < -allowedOverflow) {
        long oldTop = viewTop;
        viewTop = -allowedOverflow;
        viewBottom += viewTop - oldTop;
      }

      if (viewBottom > masterRoot.size + allowedOverflow) {
        long oldBottom = viewBottom;
        viewBottom = masterRoot.size + allowedOverflow;
        viewTop += viewBottom - oldBottom;
      }
      targetViewTop = viewTop;
      targetViewBottom = viewBottom;
      animationStartTime = 0;
      touchX = newTouchX;
      touchY = newTouchY;
      postInvalidate();
    } else if (action == MotionEvent.ACTION_UP) {
      if (!touchMovement) {
        // FIXME: broken
        if (masterRoot.depth(touchEntry) > (int)touchDepth + 1) return true;
        cursor.set(this, touchEntry);
        zoomCursor();
        zoomOutCursor();
        return true;
      }
      touchMovement = false;
      if (animationStartTime != 0) return true;
      prepareMotion();
      animationDuration = 300;
      if (targetViewTop < 0) {
        long oldTop = targetViewTop;
        targetViewTop = 0;
        targetViewBottom += targetViewTop - oldTop;
      } else if (targetViewBottom > masterRoot.size) {
        long oldBottom = targetViewBottom;
        targetViewBottom = masterRoot.size;
        targetViewTop += targetViewBottom - oldBottom;
      }

      if (viewDepth < 0) {
        targetViewDepth = 0;
      } else {
        targetViewDepth = Math.round(targetViewDepth);
      }
      postInvalidate();
    }
    return true;
  }

  public final void zoomCursor() {
    if (cursor.position.size == 0) {
      //Log.d("DiskUsage", "position is of zero size");
      return;
    }
    float yscale = screenHeight / (float)(targetViewBottom - targetViewTop);

    if (cursor.position.size * yscale > FileSystemEntry.fontSize * 2) {
      //Log.d("DiskUsage", "position large enough to contain label");
    } else {
      //Log.d("DiskUsage", "zoom in");
      float new_yscale = FileSystemEntry.fontSize * 2.5f / cursor.position.size;
      prepareMotion();

      targetViewTop = targetViewBottom - (long) (screenHeight / new_yscale);

      if (targetViewTop > cursor.top) {
        //Log.d("DiskUsage", "moving down to fit view after zoom in");
        // 10% from top
        long offset = cursor.top - (long)(targetViewTop * 0.9 + targetViewBottom * 0.1);
        targetViewTop += offset;
        targetViewBottom += offset;

        if (targetViewTop < 0) {
          //Log.d("DiskUsage", "at the top");
          targetViewBottom -= targetViewTop;
          targetViewTop = 0;
        }
      }
    }

    if (targetViewBottom < cursor.top + cursor.position.size) {
      //Log.d("DiskUsage", "move up as needed");
      prepareMotion();

      long offset = cursor.top + cursor.position.size 
      - (long)(targetViewTop * 0.1 + targetViewBottom * 0.9);
      targetViewTop += offset;
      targetViewBottom += offset;
      if (targetViewBottom > masterRoot.size) {
        long diff = targetViewBottom - masterRoot.size;
        targetViewBottom = masterRoot.size;
        targetViewTop -= diff;
      }
    }
    postInvalidateDelayed(20);
  }

  public final void zoomOutCursor() {
    if (targetViewTop < cursor.top &&
        targetViewBottom > cursor.top + cursor.position.size) {

      // Log.d("DiskUsage", "fits in, no need for zoom out");
      return;
    }

    //Log.d("DiskUsage", "zoom out");

    prepareMotion();

    FileSystemEntry viewRoot = cursor.position.parent;
    targetViewTop = masterRoot.getOffset(viewRoot);
    long size = viewRoot.size;
    targetViewBottom = targetViewTop + size;
    zoomCursor();
    postInvalidateDelayed(20);
  }

  final boolean back() {
    FileSystemEntry newpos = cursor.position.parent;
    if (newpos == masterRoot) return false;
    cursor.set(this, newpos);

    if (cursor.depth < targetViewDepth) {
      prepareMotion();
      targetViewDepth = cursor.depth;
    }
    zoomOutCursor();
    titleNeedUpdate = true;
    return true;
  }
  
  public final void onPrepareOptionsMenu(Menu menu) {
    //Log.d("DiskUsage", "onCreateContextMenu");
    menu.clear();
    if (sdcardIsEmpty()) return;
    
    menuForEntry = cursor.position;
    // FIXME: hack to disable removal of /sdcard
    if (menuForEntry == masterRoot.children[0]) return;
    
    menu.add(context.getString(R.string.button_show))
      .setOnMenuItemClickListener(new OnMenuItemClickListener() {
      public boolean onMenuItemClick(MenuItem item) {
        String path = menuForEntry.path();
        Log.d("DiskUsage", "show " + path);
        FileSystemView.this.view(menuForEntry);
        return true;
      }
    });
    menu.add(context.getString(R.string.button_delete))
      .setOnMenuItemClickListener(new OnMenuItemClickListener() {
      public boolean onMenuItemClick(MenuItem item) {
        String path = menuForEntry.path();
        Log.d("DiskUsage", "ask for deletion of " + path);
        FileSystemView.this.askForDeletion(menuForEntry);
        return true;
      }
    });
  }
  
  private void view(FileSystemEntry entry) {
    String path = entry.path();
    Intent intent = new Intent(Intent.ACTION_VIEW);
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    Uri uri = Uri.fromFile(new File(entry.path()));
    String[] types = { "audio/", "video/", "text/" };
    int dot = path.lastIndexOf(".");
    int slash = path.lastIndexOf("/");
    if (dot > slash) {
      String extension = path.substring(dot + 1).toLowerCase(); 
      for (int i = 0; i < types.length; i++) {
        String mime = extensionToMime.get(extension);
        if (mime != null) {
          try {
            intent.setDataAndType(uri, mime);
            context.startActivity(intent);
            return;
          } catch (ActivityNotFoundException e) {
          }
        }
      }
    }
    Toast.makeText(context, "No viewer found", Toast.LENGTH_SHORT).show();
  }
  
  private void askForDeletion(final FileSystemEntry entry) {
    final String path = entry.path();
    Log.d("DiskUsage", "Deletion requested for " + path);
    
    new AlertDialog.Builder(this.context)
    .setTitle("Delete " + path +
        (new File(path).isDirectory() ? " directory?" : " file?"))
    .setPositiveButton(context.getString(R.string.button_delete),
        new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        BackgroundDelete.startDelete(FileSystemView.this, entry);
      }
    })
    .setNegativeButton(context.getString(R.string.button_cancel),
        new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int whichButton) {
      }
    }).create().show();
  }
  
  public void remove(FileSystemEntry entry) {
    this.invalidate();
    cursor.set(this, entry);
    cursor.up(this);
    cursor.left(this);
    entry.remove();
    if (cursor.position == entry) {
      cursor.position = masterRoot;
    }
    cursor.refresh(this);
    zoomCursor();
    zoomOutCursor();
  }

  public void restore(FileSystemEntry entry) {
    if (cursor.position == masterRoot)
      cursor.position = entry;
    cursor.refresh(this);
    zoomCursor();
    zoomOutCursor();
  }
  
  public boolean sdcardIsEmpty() {
    return cursor.position == masterRoot;
  }
  
  @Override
  public final boolean onKeyDown(final int keyCode, final KeyEvent event) {
    if (sdcardIsEmpty())
      return super.onKeyDown(keyCode, event);
    
    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
      cursor.down(this);
      zoomCursor();
      titleNeedUpdate = true;
      return true; 
    }

    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
      cursor.up(this);
      zoomOutCursor();
      titleNeedUpdate = true;
      return true;
    }

    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
      back();
      return true;
    }

    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
      cursor.right(this);
      zoomCursor();
      if (cursor.depth - viewDepth > maxLevels - 1) {
        prepareMotion();
        targetViewDepth++;
      }
      titleNeedUpdate = true;
      return true;
    }

    if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
      final FileSystemEntry selected = cursor.position;
      // FIXME: hack to disable removal of /sdcard
      if (selected == masterRoot.children[0]) return true   ;
      askForDeletion(selected);
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
    cursor = new Cursor(masterRoot);
    screenHeight = getHeight();
    screenWidth = getWidth();
    FileSystemEntry.elementWidth = screenWidth / maxLevels;
    titleNeedUpdate = true;
  }
}