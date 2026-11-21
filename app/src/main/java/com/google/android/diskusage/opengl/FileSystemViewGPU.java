package com.google.android.diskusage.opengl;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import androidx.annotation.NonNull;
import com.google.android.diskusage.ui.FileSystemState;
import com.google.android.diskusage.ui.FileSystemState.FileSystemView;
import com.google.android.diskusage.ui.FileSystemState.MyMotionEvent;
import timber.log.Timber;

public final class FileSystemViewGPU extends SurfaceView
                                     implements FileSystemView, SurfaceHolder.Callback {
  FileSystemState eventHandler;
  private final AbstractRenderingThread thread;

  
  public FileSystemViewGPU(Context context, @NonNull FileSystemState eventHandler) {
    super(context);
    this.eventHandler = eventHandler;
    setFocusable(true);
    setFocusableInTouchMode(true);
    Timber.d("new FileSystemViewGPU");

//    setBackgroundColor(Color.GRAY);
    SurfaceHolder holder = getHolder();
    holder.setSizeFromLayout();
    holder.addCallback(this);
    eventHandler.setView(this);
    thread = new RenderingThread(context, eventHandler);
    thread.start();
  }

  @SuppressLint("ClickableViewAccessibility")
  @Override
  public final boolean onTouchEvent(final MotionEvent ev) {
    final MyMotionEvent myev = 
      eventHandler.multitouchHandler.newMyMotionEvent(ev);
    thread.addEvent(() -> eventHandler.onTouchEvent(myev));
    return true;
  }
  
  public final void runInRenderThread(final Runnable r) {
    thread.addEvent(r);
  }
  
  public void requestRepaintGPU() {
    if (thread != null) {
      thread.addEmptyEvent();
    }
  }
  
  public void requestRepaint() {}
  public void requestRepaint(int l, int t, int r, int b) {}

  @Override
  protected final void onDraw(final Canvas canvas) {}
  
  @Override
  public final boolean onKeyDown(final int keyCode, final KeyEvent event) {
    thread.addEvent(() -> eventHandler.onKeyDown(keyCode, event));
    switch (keyCode) {
      case KeyEvent.KEYCODE_BACK:
      case KeyEvent.KEYCODE_DPAD_CENTER:
      case KeyEvent.KEYCODE_DPAD_LEFT:
      case KeyEvent.KEYCODE_DPAD_RIGHT:
      case KeyEvent.KEYCODE_DPAD_UP:
      case KeyEvent.KEYCODE_DPAD_DOWN:
      case KeyEvent.KEYCODE_SEARCH:
        return true;
    }

    return super.onKeyDown(keyCode, event);
  }
  
  @Override
  protected final void onLayout(boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);
//    eventHandler.onLayout(changed, left, top, right, bottom, getWidth(), getHeight());
  }

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int width,
      int height) {
    Timber.d("Surface changed to: %s x %s", width, height);
    thread.addEvent(thread.new SurfaceChangedEvent(holder, width, height));
    requestRepaintGPU();
  }

  @Override
  public void surfaceCreated(SurfaceHolder holder) {
    thread.addEvent(thread.new SurfaceAvailableEvent(holder, true));
  }

  @Override
  public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
    holder.removeCallback(this);
    thread.addEvent(thread.new SurfaceAvailableEvent(holder, false));
  }
  
  @Override
  protected void onDetachedFromWindow() {
    Timber.d("FileSystemViewGPU.onDetachedFromWindow");
      super.onDetachedFromWindow();
      thread.addEvent(thread.new ExitEvent());
  }
  
  @Override
  public void invalidate() {
    super.invalidate();
    requestRepaintGPU();
  }

  @Override
  public void killRenderThread() {
    thread.addEvent(thread.new ExitEvent());
    // FIXME: doesn't work
//    try {
//      thread.join();
//    } catch (InterruptedException e) {
//      thread.interrupt();
//    }
  }
}
