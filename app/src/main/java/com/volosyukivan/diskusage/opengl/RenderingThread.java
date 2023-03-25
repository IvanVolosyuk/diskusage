package com.volosyukivan.diskusage.opengl;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.opengl.GLUtils;
import androidx.annotation.NonNull;
import com.volosyukivan.diskusage.R;
import com.volosyukivan.diskusage.filesystem.entity.FileSystemEntry;
import com.volosyukivan.diskusage.ui.FileSystemState;
import timber.log.Timber;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import javax.microedition.khronos.opengles.GL10;


public class RenderingThread extends AbstractRenderingThread {
  private final Context context;
  private final FileSystemState eventHandler;
  
  private static final float[][] vertexData = {
    { 0.1f,  0.2f, 0},
    { 0.9f,  0.2f, 0},
    { 0.9f,  0.9f, 0},
    { 0.1f,  0.9f, 0}
  };
  
  private final ShortBuffer indicies;
  private final FloatBuffer vertexBuffer;
  private final FloatBuffer texCoords;
  private final FloatBuffer textTexCoords;
  public Square dirSquare;
  public Square fileSquare;
  public Square specialSquare;
  public SmallSquare smallSquare;
  public CursorFrame cursorSquare;
  
  private final static int TEXTURE_SIZE = 1 << 7;

  
  float[] matrix = new float[16];
  
  private static final int MAX_RECTS = 100;
  
  private static final int MAX_INDEXES = MAX_RECTS * 6;
  private static final int MAX_VERTEX = MAX_RECTS * 4;
  
  private static final int SIZEOF_SHORT = 2;
  private static final int SIZEOF_FLOAT = 4;
  
  private static final int MAX_TEXT_DRAWS_PER_TEXTURE = 100; 
  private static final int MAX_TEXT_VERTEXES = MAX_TEXT_DRAWS_PER_TEXTURE * 4;
  private static final int MAX_TEXT_TEXCOORDS = MAX_TEXT_VERTEXES * 2;
  
//  private float[] dirVertexes = new float[MAX_VERTEX * 3];
//private float[] fileVertexes = new float[MAX_VERTEX * 3];
  private final float[] textureVertexes = new float[4 * 3];
  
  
  private BitmapMap currentBitmapMap;
  private Bitmap editedBitmap;
  private Canvas editedCanvas;

  private static final Paint textPaint = new Paint();
  private int textHeight;
  private float textBaseline;
  private static final int padding = FileSystemEntry.padding;
  ArrayList<BitmapMap> bitmaps = new ArrayList<>();
  
  private static final float divTexSize = 1.f / TEXTURE_SIZE;
  private float max_usage;
//  private static final Paint textBgPaint = new Paint();
  static {
    textPaint.setColor(Color.parseColor("#FFFFFF"));
    textPaint.setStyle(Paint.Style.FILL_AND_STROKE);
    textPaint.setFlags(textPaint.getFlags() | Paint.ANTI_ALIAS_FLAG);
    textPaint.setShadowLayer(padding, 1, 1, Color.BLACK);
//    textBgPaint.setColor(Color.parseColor("#000000"));
//    textBgPaint.setStyle(Paint.Style.STROKE);
//    textBgPaint.setFlags(textPaint.getFlags() | Paint.ANTI_ALIAS_FLAG);
  }
  
  public void updateFonts(@NonNull Context context) {
    float scaledDensity = context.getResources().getDisplayMetrics().scaledDensity;
    // float density = context.getResources().getDisplayMetrics().density;
    float dpi = 160 * scaledDensity;
    int width = context.getResources().getDisplayMetrics().widthPixels;
    int height = context.getResources().getDisplayMetrics().heightPixels;
    int min = Math.min(width, height);
    float minInch = min / dpi; // my tablet: 5 inch height
                               // my phone: 2 inc width
    Timber.d("RenderingThread.updateFonts(): Screen inch = %s", minInch);
    
    float defaultSize = textPaint.getTextSize();
    textPaint.setTextSize(20);
    
    // Atleast 4 times "Storage Card" should fit into the screen
    float textSize = 20 * min / (textPaint.measureText("Storage card") * 4);
    
    // 20 px font, seems confortable enough, if we end up with the font larger
    // than that, we may want to fit 2x more data.
    if (textSize > 20) {
      textSize /= 2;
      
      // In case we cannot fit 2x more data, we at least fit [1.0, 2.0]x more.
      if (textSize < 20) {
        textSize = 20;
      }
    }
    
    // For low DPI devices, font size should never go below 12 px (which seems to be default value).
    if (textSize < defaultSize) textSize = defaultSize;
    
    // For very high DPI devices, we might want to check if the physical size of letters is sufficient
    // Let's say, 20 px font on 300 dpi devices seems readable enough: 
    if (textSize / dpi < 20 / 300.f) {
      textSize = 20.f / 300.f * dpi;
    }
    
    textPaint.setTextSize(textSize);
    textBaseline = - textPaint.ascent() + FileSystemEntry.padding;
    textHeight = (int)(textPaint.descent() - textPaint.ascent() + 1 + 2 * FileSystemEntry.padding);
    max_usage = ((TEXTURE_SIZE - 1f) / textHeight) * (TEXTURE_SIZE - 2);
    FileSystemEntry.updateFonts(textSize);
  }
  
  public RenderingThread(
      Context context,
      FileSystemState eventHandler) {
    updateFonts(context);
    this.context = context;
    this.eventHandler = eventHandler;
    
    ByteBuffer pb = ByteBuffer.allocateDirect(MAX_INDEXES * SIZEOF_SHORT);
    pb.order(ByteOrder.nativeOrder());
    indicies = pb.asShortBuffer();
    
    ByteBuffer tbb = ByteBuffer.allocateDirect(MAX_VERTEX * SIZEOF_FLOAT * 2);
    tbb.order(ByteOrder.nativeOrder());
    texCoords = tbb.asFloatBuffer();

    ByteBuffer vbb = ByteBuffer.allocateDirect(MAX_VERTEX * SIZEOF_FLOAT * 3);
    vbb.order(ByteOrder.nativeOrder());
    vertexBuffer = vbb.asFloatBuffer();

    ByteBuffer tbb2 = ByteBuffer.allocateDirect(MAX_TEXT_VERTEXES * SIZEOF_FLOAT * 2);
    tbb2.order(ByteOrder.nativeOrder());
    textTexCoords = tbb2.asFloatBuffer();
    textTexCoords.position(0);

    int vertex = 0;
    
    for (int i = 0; i < MAX_RECTS; i++) {
      indicies.put(new short[] {
          (short)(vertex), (short)(1 + vertex), (short)(2 + vertex),
          (short)(vertex), (short)(2 + vertex), (short)(3 + vertex)});
      
      for (int x = 0; x < 4; x++) {
        texCoords.put(vertexData[x][0]);
        texCoords.put(vertexData[x][1]);
      }
      vertex += 4;
    }
    
    indicies.position(0);
    texCoords.position(0);
  }
  
  public void drawVertexes(
          @NonNull float[] out, int pos, float x0, float y0, float x1, float y1) {
    out[pos] = x0;
    out[pos + 1] = y0;
    
    out[pos + 3] = x1;
    out[pos + 4] = y0;
    
    out[pos + 6] = x1;
    out[pos + 7] = y1;
    
    out[pos + 9] = x0;
    out[pos + 10] = y1;
  }
  
  public class Square {
    private int nrects = 0;
    private final int texture_id;
    private final float[] vertexes = new float[MAX_VERTEX * 3];
    
    Square(int resid) {
      texture_id = LoadTexture(getBitmap(resid));
    }
    
    public final void draw(float x0, float y0, float x1, float y1) {
      int pos = nrects * 12;
      drawVertexes(vertexes, pos, x0, y0, x1, y1);
      nrects++;
      
      if (nrects >= MAX_RECTS) {
        flush();
      }
    }
    
    public void flush() {
      if (nrects == 0) return;
      gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, texCoords);
      gl.glBindTexture(GL10.GL_TEXTURE_2D, texture_id);
      vertexBuffer.put(vertexes, 0, nrects * 12);
      vertexBuffer.position(0);
      gl.glDrawElements(GL10.GL_TRIANGLES, nrects * 6,
          GL10.GL_UNSIGNED_SHORT, indicies);
      nrects = 0;
    }
  }
  
  public class SmallSquare {
    private int nrects = 0;
    private final int texture_id;
    private final float[] vertexes = new float[MAX_VERTEX * 3];
    private final FloatBuffer texSmallCoordsBuffer;
    private final float[] texSmallCoords = new float[MAX_VERTEX * 2];

    
    SmallSquare(int resid) {
      texture_id = LoadTexture(getBitmap(resid));
      ByteBuffer tbb = ByteBuffer.allocateDirect(
          MAX_VERTEX * SIZEOF_FLOAT * 2);
      tbb.order(ByteOrder.nativeOrder());
      texSmallCoordsBuffer = tbb.asFloatBuffer();
      for (int i = 0; i < MAX_RECTS; i++) {
        // 0 1  2 3  4 5  6 7
        // 0 0, 1 0, 1 n, 0 n 
        texSmallCoords[i * 8 + 2] = 1;
        texSmallCoords[i * 8 + 4] = 1;
      }
    }
    
    public final void draw(float x0, float y0, float x1, float y1) {
      int pos = nrects * 12;
      drawVertexes(vertexes, pos, x0, y0, x1, y1);
      texSmallCoords[nrects * 8 + 5] = (y1 - y0) / 4;
      texSmallCoords[nrects * 8 + 7] = (y1 - y0) / 4;
      nrects++;
      
      if (nrects >= MAX_RECTS) {
        flush();
      }
    }
    
    public void flush() {
      if (nrects == 0) return;
      texSmallCoordsBuffer.put(texSmallCoords, 0, nrects * 8);
      texSmallCoordsBuffer.position(0);
      gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, texSmallCoordsBuffer);
      gl.glBindTexture(GL10.GL_TEXTURE_2D, texture_id);
      vertexBuffer.put(vertexes, 0, nrects * 12);
      vertexBuffer.position(0);
      gl.glDrawElements(GL10.GL_TRIANGLES, nrects * 6,
          GL10.GL_UNSIGNED_SHORT, indicies);
      nrects = 0;
    }
  }
  
  public final class CursorFrame {
    private final int white;
//    private final int black;
    private boolean dirty = false;
    private final float[] vertexes = new float[4 * 4 * 3];

    
    CursorFrame() {
      white = LoadTexture(getBitmap(R.drawable.white_gradient));
//      black = LoadTexture(getBitmap(R.drawable.black_gradient));
    }
    
    public final void drawVertexes(int pos, float x0, float y0,
        float xoff1, float yoff1, float xoff2, float yoff2) {
      vertexes[pos] = x0;
      vertexes[pos + 1] = y0;
      vertexes[pos + 3] = x0 + xoff1;
      vertexes[pos + 4] = y0 + yoff1;
      vertexes[pos + 6] = x0 + xoff1 + xoff2;
      vertexes[pos + 7] = y0 + yoff1 + yoff2;
      vertexes[pos + 9] = x0 + xoff2;
      vertexes[pos + 10] = y0 + yoff2;
    }
    
    public final void drawFrame(float x0, float y0, float x1, float y1) {
      drawVertexes(0, x0, y0, x1 - x0, 0, 0, 8);
      drawVertexes(12, x0, y1, 0, y0 - y1, 8, 0);
      drawVertexes(24, x1, y0, 0, y1 - y0, -8, 0);
      drawVertexes(36, x1, y1, x0 - x1, 0, 0, -8);
      dirty = true;
    }

    public void flush() {
      if (!dirty) return;
      dirty = false;
      gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, texCoords);
      gl.glEnable(GL10.GL_BLEND);
//      gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA);
//      gl.glBindTexture(GL10.GL_TEXTURE_2D, black);
      gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE);
      gl.glBindTexture(GL10.GL_TEXTURE_2D, white);
      vertexBuffer.put(vertexes, 2 * 12, 2 * 12);
      vertexBuffer.position(0);
      gl.glDrawElements(GL10.GL_TRIANGLES, 2 * 6,
          GL10.GL_UNSIGNED_SHORT, indicies);
      gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, texCoords);
      vertexBuffer.put(vertexes, 0, 2 * 12);
      vertexBuffer.position(0);
      gl.glDrawElements(GL10.GL_TRIANGLES, 2 * 6,
          GL10.GL_UNSIGNED_SHORT, indicies);
      gl.glDisable(GL10.GL_BLEND);
    }
  }

  public int newTextureId() {
    int[] ids = new int[1];
    gl.glGenTextures(1, ids, 0);
    return ids[0];
  }
  
  private class BitmapMap implements Comparable<BitmapMap> {
    ArrayList<TextPixels> textPixelsArray = new ArrayList<>();
    int textureid;
    int usage;
    // int last_usage;
    int y;
    int x;
    int build_x;
    int build_y;
    Bitmap bitmap;
    Canvas canvas;
    float[] texCoords = new float[MAX_TEXT_TEXCOORDS];
    float[] vertexes = new float[MAX_TEXT_VERTEXES * 3];
    int nrect;
    public boolean inuse;
    
    BitmapMap() {
      bitmaps.add(this);
      textureid = newTextureId();
      edit();
    }
    
    
    Paint clearPaint;
    {
      clearPaint = new Paint();
      clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }
    
    public void edit() {
      if (editedBitmap == null) {
        editedBitmap = Bitmap.createBitmap(
            TEXTURE_SIZE, TEXTURE_SIZE, Bitmap.Config.ARGB_8888);
        editedCanvas = new Canvas(editedBitmap);
      } else {
        editedCanvas.clipRect(new Rect(0, 0, TEXTURE_SIZE, TEXTURE_SIZE));
      }
      editedCanvas.drawPaint(clearPaint);
      bitmap = editedBitmap;
      canvas = editedCanvas;
      x = 1;
      y = 0;
      build_x = 1;
      build_y = 0;
    }
    
    public void reset() {
      flush();
      for (TextPixels textPixels : textPixelsArray) {
        textPixels.reset();
      }
      textPixelsArray.clear();
      edit();
    }
    
    public void flushNoDeps() {
      if (bitmap != null) {
        buildTexture();
      }
      
      gl.glBindTexture(GL10.GL_TEXTURE_2D, textureid);
      gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, textTexCoords);
      gl.glEnable(GL10.GL_BLEND);
      gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA);

      textTexCoords.put(texCoords, 0, nrect * 8);
      vertexBuffer.put(vertexes, 0, nrect * 12);
      textTexCoords.position(0);
      vertexBuffer.position(0);
      gl.glDrawElements(GL10.GL_TRIANGLES, nrect * 6,
          GL10.GL_UNSIGNED_SHORT, indicies);
      nrect = 0;
      gl.glDisable(GL10.GL_BLEND);

    }
    
    public void flush() {
      smallSquare.flush();
      dirSquare.flush();
      fileSquare.flush();
      specialSquare.flush();
      cursorSquare.flush();
      flushNoDeps();
    }
    
    public void buildTexture() {
      if (build_x == x && build_y == y) return;
      build_x = x;
      build_y = y;
      gl.glBindTexture(GL10.GL_TEXTURE_2D, textureid);
      GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bitmap, 0);
      gl.glTexEnvf(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_MODE,
          GL10.GL_REPLACE);
      gl.glTexParameterx(GL10.GL_TEXTURE_2D,
          GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_NEAREST);
      gl.glTexParameterx(GL10.GL_TEXTURE_2D,
          GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST);
    }

    public void commit() {
      buildTexture();
//      bitmap.recycle();
      bitmap = null;
      canvas = null;
      inuse = true;
    }
    
    public int score() {
      if (bitmap != null) return Integer.MAX_VALUE;
      return usage;
    }

    @Override
    public int compareTo(@NonNull BitmapMap another) {
      int score = score();
      int another_score = another.score();
      return Integer.compare(score, another_score);
    }

    public void destroy() {
      for (TextPixels textPixels : textPixelsArray) {
        textPixels.reset();
      }
    }
  }
  
  public BitmapMap getCurrentBitmapMap() {
    if (currentBitmapMap == null) {
      currentBitmapMap = new BitmapMap();
    }
    if (currentBitmapMap == null)
      throw new NullPointerException("no bitmap");
    return currentBitmapMap;
  }
  
  public boolean hasReusableBitmap() {
    // Avoid to hijack texture we just draw into.
    // We still have references in TextPixels in current stack
    // in function: TextPixels.draw()
    // and it is also bad to reuse the same texture as we don't
    // cache anything this way.
    return !bitmaps.get(0).inuse;
  }
  
  public BitmapMap getLeastUsedBitmap() {
    BitmapMap bitmapMap = bitmaps.remove(0);
    bitmaps.add(bitmapMap);
    bitmapMap.reset();
    return bitmapMap;
  }
  
  public void nextBitmapMap() {
    currentBitmapMap.commit();
    if (bitmaps.size() >= 40 && hasReusableBitmap()) {
//      Log.d("diskusage", "get least used bitmap, bitmaps = 5");
      currentBitmapMap = getLeastUsedBitmap();
      return;
    }
    
//    float bitmapUsage = bitmaps.get(0).last_usage / max_usage;
//    if (bitmapUsage < 0.2 && hasReusableBitmap()) {
////      Log.d("diskusage", "get least used bitmap, usage = " + bitmapUsage);
//      currentBitmapMap = getLeastUsedBitmap();
//      return;
//    }
    
//    Log.d("diskusage", "new bitmap");
    currentBitmapMap = new BitmapMap();
  }
  
  static class TextPixels {
    private final String message;
    private final int offset;
    // Reminder of size after removing offset
    private int size;
    
    private BitmapMap bitmapMap;
    private int mapX, mapY, mapSize;
    private TextPixels nextPixels;
    
    TextPixels(String message) {
      this.message = message;
      this.size = 0;
      this.offset = 0;
    }
    
    public void reset() {
      bitmapMap = null;
      nextPixels = null;
      size = 0;
    }

    TextPixels(String message, int size, int offset) {
      this.message = message;
      this.size = size;
      this.offset = offset;
      
    }

    public void draw(@NonNull RenderingThread rt, float x0, float y0, int elementWidth) {
      int textHeight = rt.textHeight;
      float textBaseline = rt.textBaseline;
      if (size == 0) {
        size = (int)(textPaint.measureText(message) + 1 + 2 * FileSystemEntry.padding);
      }
      if (bitmapMap == null) {
        bitmapMap = rt.getCurrentBitmapMap();
        bitmapMap.textPixelsArray.add(this);
        mapX = bitmapMap.x + 1;
        mapY = bitmapMap.y;
        int todraw = Math.min(size, elementWidth + 20);
        int sizeAvailable = (TEXTURE_SIZE - 2) - mapX;
        int drawing = Math.min(sizeAvailable, todraw);
        // FIXME: allow 1 additional pixel on line break
        Canvas canvas = bitmapMap.canvas;
        canvas.save();
        canvas.clipRect(new Rect(mapX - 1, mapY,
                                 mapX + drawing + 1, mapY + textHeight));
        canvas.drawText(message, mapX - offset + padding, mapY + textBaseline, textPaint);
        canvas.restore();
        int newx = bitmapMap.x = mapX + drawing + 1;
        if (newx > TEXTURE_SIZE - 20) {
          bitmapMap.x = 1;
          int newy = bitmapMap.y = mapY + textHeight;
          if (newy > (TEXTURE_SIZE - 1) - textHeight) rt.nextBitmapMap();
        }
        mapSize = drawing;
      }
      int todraw = Math.min(size, elementWidth);
      int drawing = Math.min(todraw, mapSize);
      bitmapMap.usage += drawing;
      float tex_x0 = mapX * divTexSize;
      float tex_y0 = mapY * divTexSize;
      float tex_x1 = (mapX + drawing) * divTexSize; 
      float tex_y1 = (mapY + textHeight) * divTexSize;
      int nrect = bitmapMap.nrect;
      int off = nrect * 8;
      float[] texCoordsArray = bitmapMap.texCoords;
      texCoordsArray[off] = tex_x0; texCoordsArray[off + 1] = tex_y0;
      texCoordsArray[off + 2] = tex_x1; texCoordsArray[off + 3] = tex_y0;
      texCoordsArray[off + 4] = tex_x1; texCoordsArray[off + 5] = tex_y1;
      texCoordsArray[off + 6] = tex_x0; texCoordsArray[off + 7] = tex_y1;
      rt.drawVertexes(bitmapMap.vertexes, nrect * 12,
          x0, y0 - textBaseline, x0 + drawing, y0  + textHeight - rt.textBaseline);
      
      int newrect = bitmapMap.nrect = nrect + 1;
      if (newrect >= MAX_TEXT_DRAWS_PER_TEXTURE) {
        bitmapMap.flush();
      }
      if (drawing != todraw) {
        if (nextPixels == null) {
          nextPixels = new TextPixels(message, size - drawing, offset + drawing);
        }
        nextPixels.draw(rt, x0 + drawing, y0, elementWidth - drawing);
      }
    }
  }
  
  public void flushTexture() {
    vertexBuffer.put(textureVertexes, 0, 12);
    vertexBuffer.position(0);
    gl.glDrawElements(GL10.GL_TRIANGLES, 6,
        GL10.GL_UNSIGNED_SHORT, indicies);
  }

  Bitmap getBitmap(int resid) {
    Drawable drawable = context.getResources().getDrawable(resid);
    Bitmap bitmap = Bitmap.createBitmap(
        16, 16, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    canvas.drawColor(Color.TRANSPARENT);
    drawable.setBounds(0, 0, 16, 16);
    drawable.draw(canvas);
    return bitmap;
  }
  
  private int LoadTexture(Bitmap bitmap) {
    int texture_id = newTextureId();
//    Bitmap bitmap = getBitmap(resid);

    gl.glBindTexture(GL10.GL_TEXTURE_2D, texture_id);
    GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bitmap, 0);
    gl.glTexEnvf(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_MODE,
        GL10.GL_REPLACE);
    bitmap.recycle();
    gl.glTexParameterx(GL10.GL_TEXTURE_2D,
        GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
    gl.glTexParameterx(GL10.GL_TEXTURE_2D,
        GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
    return texture_id;
  }
  
  private void LoadTextures(GL10 gl) {
    dirSquare = new Square(R.drawable.dirbg_new);
    fileSquare = new Square(R.drawable.filebg_new);
    specialSquare = new Square(R.drawable.special);
    smallSquare = new SmallSquare(R.drawable.small);
    cursorSquare = new CursorFrame();
  }
  
  public void flush() {
    smallSquare.flush();
    dirSquare.flush();
    fileSquare.flush();
    specialSquare.flush();
    cursorSquare.flush();
    
    for (BitmapMap bitmap : bitmaps) {
      bitmap.flushNoDeps();
    }
  }
  
  @Override
  public boolean renderFrame(@NonNull GL10 gl) {
//    renderFrameStart();
    int color = Color.GRAY; // context.getResources().getColor(android.R.color.background_light);
    float r = ((color >> 16) & 255) / 255.f;
    float g = ((color >> 8) & 255) / 255.f;
    float b = (color & 255) / 255.f;
    gl.glClearColor(r, g, b, 1.f);
    gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);
    gl.glLoadIdentity();
    gl.glScalef(0.5f, 0.5f, 1.f);
    
    boolean renderRequested = eventHandler.onDrawGPU(this);
    flush();
//    Collections.sort(bitmaps);
//    for (BitmapMap bitmap : bitmaps) {
//      bitmap.last_usage = bitmap.usage;
//      bitmap.inuse = false;
//      bitmap.usage = 0;
//    }
    return renderRequested;
  }

  @Override
  public void createResources(GL10 gl) {
    Timber.d("***** Surface Created *****");
    // Load textures
    LoadTextures(gl);
  }
  
  public void releaseResources(GL10 gl) {
    Timber.d("***** Surface Destroyed *****");
    for (BitmapMap bitmap : bitmaps) {
      bitmap.destroy();
    }
    bitmaps.clear();
    currentBitmapMap = null;
  }
  
  @Override
  public void sizeChanged(@NonNull GL10 gl, int width, int height) {
    Timber.d("***** Surface Size Changed *****");
//    FileSystemEntry.elementWidth = 100;// FIXME??;
//    FileSystemEntry.fontSize = 20; // FIXME
    eventHandler.layout(true, 0, 0, width, height, width, height);
    // Init projection
    
    gl.glHint(GL10.GL_PERSPECTIVE_CORRECTION_HINT,
        GL10.GL_FASTEST);
    gl.glViewport(0, 0, width, height);
    Timber.d("RenderingThread.sizeChanged(): Updated viewport = %s x %s", width, height);
    
    
    gl.glMatrixMode(GL10.GL_PROJECTION);
    gl.glLoadIdentity();
    //  0  4  8 12
    //  1  5  9 13
    //  2  6 10 14
    //  3  7 11 15
    matrix[0] = 4.f / width;
    matrix[5] = -4.f / height;
    matrix[10] = 1.f;
    matrix[15] = 1.f;
    matrix[12] = -1f;
    matrix[13] = 1f;
    
    gl.glLoadMatrixf(matrix, 0);
    
    gl.glMatrixMode(GL10.GL_MODELVIEW);


    gl.glEnable(GL10.GL_DITHER);
    gl.glEnable(GL10.GL_CULL_FACE);
    gl.glShadeModel(GL10.GL_SMOOTH);
//    gl.glEnable(GL10.GL_DEPTH_TEST);
//    gl.glDepthFunc(GL10.GL_LESS);
    gl.glFrontFace(GL10.GL_CW);
    
    gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
    gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
    gl.glEnable(GL10.GL_TEXTURE_2D);
    gl.glVertexPointer(3, GL10.GL_FLOAT, 0, vertexBuffer);
//    gl.glEnable(GL10.GL_DEPTH_TEST);
    eventHandler.draw300ms();
  }
}
