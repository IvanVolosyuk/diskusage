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

package com.google.android.diskusage.entity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;

import com.google.android.diskusage.Cursor;
import com.google.android.diskusage.R;
import com.google.android.diskusage.opengl.DrawingCache;
import com.google.android.diskusage.opengl.RenderingThread;

public class FileSystemEntry {
  private static final Paint bg = new Paint();
  private static final Paint bg_emptySpace = new Paint();
  private static final Paint cursor_fg = new Paint();
  private static final Paint fg_rect = new Paint();
//  private static final Paint fg_rect = new Paint();
  public static final Paint fg2 = new Paint();
  private static final Paint fill_bg = new Paint();
  private static final Paint textPaintFolder = new Paint();
  private static final Paint textPaintFile = new Paint();
  public static float ascent;
  public static float descent;
  private static String n_bytes;
  private static String n_kilobytes;
  private static String n_megabytes;
  private static String n_megabytes10;
  private static String n_megabytes100;
  private static String dir_name_size_num_dirs;
  private static String dir_empty;
  private static String dir_name_size;
  public static FileSystemEntry deletedEntry;

  public boolean hasChildren() {
    return children != null && children.length != 0;
  }

  /**
   * Font size. Also accessed from FileSystemView.
   */
  public static float fontSize;

  /**
   * Width of one element. Setup from FileSystemView when geometry changes.
   */
  public static int elementWidth;

  static {
    bg.setColor(Color.parseColor("#060118"));
    bg_emptySpace.setColor(Color.parseColor("#063A43"));
    bg.setStyle(Paint.Style.FILL);
//    bg.setAlpha(255);
    fg_rect.setColor(Color.WHITE);
    fg_rect.setStyle(Paint.Style.STROKE);
    fg_rect.setFlags(fg_rect.getFlags() | Paint.ANTI_ALIAS_FLAG);
//    fg_rect.setColor(Color.WHITE);
//    fg_rect.setStyle(Paint.Style.STROKE);
    fg2.setColor(Color.parseColor("#18C5E7"));
    fg2.setStyle(Paint.Style.STROKE);
    fg2.setFlags(fg2.getFlags() | Paint.ANTI_ALIAS_FLAG);
    fill_bg.setColor(Color.WHITE);
    fill_bg.setStyle(Paint.Style.FILL);
    cursor_fg.setColor(Color.YELLOW);
    cursor_fg.setStyle(Paint.Style.STROKE);

    textPaintFolder.setColor(Color.WHITE);
    textPaintFolder.setStyle(Paint.Style.FILL_AND_STROKE);
    textPaintFolder.setFlags(textPaintFolder.getFlags() | Paint.ANTI_ALIAS_FLAG);

    textPaintFile.setColor(Color.parseColor("#18C5E7"));
    textPaintFile.setStyle(Paint.Style.FILL_AND_STROKE);
    textPaintFile.setFlags(textPaintFile.getFlags() | Paint.ANTI_ALIAS_FLAG);
  }

  // Object Fields:


  // The size suitable for painting without any operations (and sorting)
  // Bit layout:
  // 40 bits      | 24 bits
  // sizeInBlocks | reminder
  // reminder is encoded file size information suitable for formating sizeString.
  // reminder:
  // 3 bits         | 21 bits  (2**18 = 44,040,192)
  // sizeMultiplier | size in multiplier of bytes
  // sizeMultiplier:
  // 000 = multiplier=1,         format=(n_bytes "%d bytes", size)
  // 001 = multiplier=1024,      format=(n_kilobytes "%d KiB", size)
  // 010 = multiplier=1024,      format=(n_megabytes "%5.2f MiB", size / 1024.f)
  // 011 = multiplier=1024,      format=(n_megabytes10 "%5.1f MiB", size / 1024.f)
  // 100 = multiplier=1024*1024, format=(n_megabytes100 "%d MiB", size)

  // Ranges for sizeMultipliers:
  // 0: sz < 1024:               "%4.0f bytes", sz
  // 1: sz < 1024 * 1024:        "%4.0f KiB", sz * (1f / 1024)
  // 2: sz < 1024 * 1024 * 10:   "%5.2f MiB", sz * (1f / 1024 / 1024)
  // 3: sz < 1024 * 1024 * 200:  "%5.1f MiB", sz * (1f / 1024 / 1024)
  // 4: sz >= 1024 * 1024 * 200: "%4.0f MiB", sz * (1f / 1024 / 1024)
  //
  // FIXME: remove outdate info:
  // reminder can be 0..blockSize (inclusive)
  // size in bytes = (size in blocks * blockSize) + reminder - blockSize;
  public long encodedSize;
  public FileSystemEntry parent;
  public FileSystemEntry[] children;
  public String name;
//  public String sizeString;
  public DrawingCache drawingCache;

  private static final int MULTIPLIER_SHIFT=18;

  private static final int MULTIPLIER_MASK = 7 << MULTIPLIER_SHIFT;
  private static final int MULTIPLIER_BYTES = 0 << MULTIPLIER_SHIFT;
  private static final int MULTIPLIER_KBYTES = 1 << MULTIPLIER_SHIFT;
  private static final int MULTIPLIER_MBYTES = 2 << MULTIPLIER_SHIFT;
  private static final int MULTIPLIER_MBYTES10 = 3 << MULTIPLIER_SHIFT;
  private static final int MULTIPLIER_MBYTES100 = 4 << MULTIPLIER_SHIFT;
  private static final int SIZE_MASK = (1 << MULTIPLIER_SHIFT) - 1;

//  static int blockSize;
  // will take for a while to make this break
  // 16Mb block size on mobile device... probably in year 2020.
  // probably 32 bits for maximum number of block will break before ~2016
  public static final int blockOffset = 24;
  static final long blockMask = (1l << blockOffset) - 1;

  public long getSizeInBlocks() {
    return encodedSize >> blockOffset;
  }

  public static String calcSizeStringFromEncoded(long encodedSize) {
    int size = SIZE_MASK & (int)encodedSize;
    switch (MULTIPLIER_MASK & (int)encodedSize) {
    case MULTIPLIER_BYTES: return String.format(n_bytes, size);
    case MULTIPLIER_KBYTES: return String.format(n_kilobytes, size);
    case MULTIPLIER_MBYTES: return String.format(n_megabytes, size * (1f / 1024));
    case MULTIPLIER_MBYTES10: return String.format(n_megabytes10, size * (1f / 1024));
    case MULTIPLIER_MBYTES100: return String.format(n_megabytes100, size);
    }
    return "";
  }

  public void clearDrawingCache() {
    if (drawingCache != null) {
      drawingCache.resetSizeString();
    }
  }

  private long makeBytesPart(long size) {
    if (size < 1024) return size;
    if (size < 1024 * 1024) return MULTIPLIER_KBYTES | (size >> 10);
    if (size < 1024 * 1024 * 10 ) return MULTIPLIER_MBYTES | (size >> 10);
    if (size < 1024 * 1024 * 200) return MULTIPLIER_MBYTES10 | (size >> 10);
    return MULTIPLIER_MBYTES100 | (size >> 20);
  }

  public void setSizeInBlocks(long blocks, int blockSize) {
    long bytes = blocks * blockSize;
    encodedSize = (blocks << blockOffset) | makeBytesPart(bytes);
  }

  public FileSystemEntry initSizeInBytes(long bytes, int blockSize) {
    long blocks = (bytes + blockSize - 1) / blockSize;
    encodedSize = (blocks << blockOffset) | makeBytesPart(bytes);
    return this;
  }

  public FileSystemEntry initSizeInBytesAndBlocks(long bytes, long blocks, int blockSize) {
    encodedSize = (blocks << blockOffset) | makeBytesPart(bytes);
    return this;
  }

  public FileSystemEntry setChildren(FileSystemEntry[] children, int blockSize) {
    this.children = children;
    long blocks = 0;
    if (children == null) return this;
    for (int i = 0; i < children.length; i++) {
      blocks += children[i].getSizeInBlocks();
      children[i].parent = this;
    }
    setSizeInBlocks(blocks, blockSize);
    return this;
  }


  public static class ExcludeFilter {
    public final Map<String, ExcludeFilter> childFilter;

    private static void addEntry(
        TreeMap<String, ArrayList<String>> filter, String name, String value) {
      ArrayList<String> entry = filter.get(name);
      if (entry == null) {
        entry = new ArrayList<String>();
        filter.put(name, entry);
      }
      entry.add(value);
    }

    public ExcludeFilter(ArrayList<String> exclude_paths) {
      if (exclude_paths == null) {
        this.childFilter = null;
        return;
      }
      TreeMap<String, ArrayList<String>> filter =
        new TreeMap<String, ArrayList<String>>();
      for(String path : exclude_paths) {
        String[] parts = path.split("/", 2);
        if (parts.length < 2) {
          addEntry(filter, path, null);
        } else {
          addEntry(filter, parts[0], parts[1]);
        }
      }
      TreeMap<String, ExcludeFilter> excludeFilter = new TreeMap<String, ExcludeFilter>();
      for (Entry<String, ArrayList<String>> entry : filter.entrySet()) {
        boolean has_null = false;
        for (String part : entry.getValue()) {
          if (part == null) {
            has_null = true;
            break;
          }
        }
        if (has_null) {
          excludeFilter.put(entry.getKey(), new ExcludeFilter(null));
        } else {
          excludeFilter.put(entry.getKey(), new ExcludeFilter(entry.getValue()));
        }
      }
      this.childFilter = excludeFilter;
    }
  }

  protected FileSystemEntry(FileSystemEntry parent, String name) {
    this.name = name;
    this.parent = parent;
  }

  public static FileSystemEntry makeNode(
      FileSystemEntry parent, String name) {
    return new FileSystemEntry(parent, name);
  }

  public static class Compare implements Comparator<FileSystemEntry> {
    @Override
    public final int compare(FileSystemEntry aa, FileSystemEntry bb) {
      if (aa.encodedSize == bb.encodedSize) {
        return 0;
      }
      return aa.encodedSize < bb.encodedSize ? 1 : -1;
    }
  }

  /**
   * For sorting according to size.
   */
  public static Compare COMPARE = new Compare();

  public FileSystemEntry create() {
    return new FileSystemEntry(null, this.name);
  }

  public class SearchInterruptedException extends RuntimeException {
    private static final long serialVersionUID = -3986013022885904101L;
  };

  public FileSystemEntry copy() {
    if (Thread.interrupted()) throw new SearchInterruptedException();
    FileSystemEntry copy = create();
    if (this.children != null) {
      FileSystemEntry[] children = new FileSystemEntry[this.children.length];
      for (int i = 0; i < this.children.length; i++) {
        FileSystemEntry childCopy = children[i] = this.children[i].copy();
        childCopy.parent = copy;
      }
      copy.children = children;
    }
    copy.encodedSize = this.encodedSize;
    return copy;
  }

  public FileSystemEntry filterChildren(CharSequence pattern, int blockSize) {
//    res = Pattern.compile(Pattern.quote(pattern.toString()), Pattern.CASE_INSENSITIVE).matcher(name).find();

    if (children == null) return null;
    ArrayList<FileSystemEntry> filtered_children = new ArrayList<FileSystemEntry>();

    for (FileSystemEntry child : this.children) {
      FileSystemEntry childCopy = child.filter(pattern, blockSize);
      if (childCopy != null) {
        filtered_children.add(childCopy);
      }
    }
    if (filtered_children.size() == 0) return null;
    FileSystemEntry[] children = new FileSystemEntry[filtered_children.size()];
    filtered_children.toArray(children);
    Arrays.sort(children, COMPARE);
    FileSystemEntry copy = create();
    copy.children = children;
    long size = 0;


    for (FileSystemEntry child : children) {
      size += child.getSizeInBlocks();
      child.parent = copy;
    }
    copy.setSizeInBlocks(size, blockSize);
    return copy;
  }

  public FileSystemEntry filter(CharSequence pattern, int blockSize) {
    if (name.toLowerCase().contains(pattern)) {
      return copy();
    }
    return filterChildren(pattern, blockSize);
  }

  /**
   * Find index of directChild in 'children' field of this entry.
   * @param directChild
   * @return index of the directChild in 'children' field.
   */
  public final int getIndexOf(FileSystemEntry directChild) {
    FileSystemEntry[] children0 = children;
    int len = children0.length;
    int i;

    for (i = 0; i < len; i++) {
      if (children0[i] == directChild) return i;
    }

    throw new RuntimeException("something broken");
  }

  /**
   * Find entry which follows this entry in its the parent.
   * @return next entry in the same parent or this entry if there is no more entries
   */
  public final FileSystemEntry getNext() {
    int index = parent.getIndexOf(this);
    if (index + 1 == parent.children.length) return this;
    return parent.children[index + 1];
  }

  /**
   * Find entry which precedes this entry in its the parent.
   * @return previous entry in the same parent or this entry if the entry is first
   */
  public final FileSystemEntry getPrev() {
    int index = parent.getIndexOf(this);
    if (index == 0) return this;
    return parent.children[index - 1];
  }

  private DrawingCache getDrawingCache() {
    if (drawingCache != null) return drawingCache;
    DrawingCache drawingCache = new DrawingCache(this);
    this.drawingCache = drawingCache;
    return drawingCache;
  }

  // Copy pasted from paint() and changed to lower overhead on generic drawing code
  private static void paintSpecialGPU(long parent_size, FileSystemEntry[] entries,
      RenderingThread rt, float xoffset, float yoffset, float yscale,
      long clipLeft, long clipRight, long clipTop, long clipBottom,
      int screenHeight, int numSpecial) {

    // Deep one level in hierarchy:
    entries = entries[0].children;
    xoffset += elementWidth;
    clipLeft -= elementWidth;
    clipRight -= elementWidth;

    // Paint as paint():
    FileSystemEntry children[] = entries;
    int len = children.length;
    long child_clipTop = clipTop;
    long child_clipBottom = clipBottom;
    float child_xoffset = xoffset + elementWidth;

    // Fast skip ordinary entries, FIXME: make root node special node with extra
    // field to get rid of this
    for (int i = 0; i < len - numSpecial; i++) {
      FileSystemEntry c = children[i];
      long csize = c.encodedSize;
      parent_size -= csize;

      float top = yoffset;
      float bottom = top + csize * yscale;

      if (child_clipBottom < 0) {
        return;
      }
      child_clipTop -= csize;
      child_clipBottom -= csize;
      yoffset = bottom;
    }

    for (int i = len - numSpecial; i < len; i++) {
      FileSystemEntry c = children[i];
      long csize = c.encodedSize;
      parent_size -= csize;

      float top = yoffset;
      float bottom = top + csize * yscale;
      ///Log.d("DiskUsage", "child: child_clip_y0 = " + child_clip_y0);
      ///Log.d("DiskUsage", "child: child_clip_y1 = " + child_clip_y1);

      if (child_clipTop > csize) {

        child_clipTop -= csize;
        child_clipBottom -= csize;
        yoffset = bottom;
        continue;
      }

      if (child_clipBottom < 0) {
        ///Log.d("DiskUsage", "skipped rest starting from " + c.name);
        return;
      }

      if (clipLeft < elementWidth) {
          // FIXME
          float windowHeight0 = screenHeight;
          float fontSize0 = fontSize;
          float top0 = top;
          float bottom0 = bottom;

          // FIXME: bg_emptySpace
          rt.specialSquare.draw(xoffset, top0, child_xoffset, bottom0);

          if (bottom - top > fontSize0 * 2) {
            float pos = (top + bottom) * 0.5f;
            if (pos < fontSize0) {
              if (bottom > 2 * fontSize0) {
                pos = fontSize0;
              } else {
                pos = bottom - fontSize0;
              }
            } else if (pos > windowHeight0 - fontSize0) {
              if (top < windowHeight0 - 2 * fontSize0) {
                pos = windowHeight0 - fontSize0;
              } else {
                pos = top + fontSize0;
              }
            }
            float pos1 = pos - descent;
            float pos2 = pos - ascent;

            DrawingCache cache = c.getDrawingCache();
//            String sizeString = cache.getSizeString();
//            int cliplen = fg2.breakText(c.name, true, elementWidth - 4, null);
//            String clippedName = c.name.substring(0, cliplen);
//            canvas.drawText(clippedName,  xoffset + 2, pos1, fg);
//            canvas.drawText(sizeString, xoffset + 2, pos2, fg);
            cache.drawText(rt, xoffset + 2, pos1, elementWidth - 5);
            cache.drawSize(rt, xoffset + 2, pos2, elementWidth - 5);

          } else if (bottom - top > fontSize0) {
//            int cliplen = fg2.breakText(c.name, true, elementWidth - 4, null);
//            String clippedName = c.name.substring(0, cliplen);
//            canvas.drawText(clippedName, xoffset + 2, (top + bottom - ascent - descent) / 2, c.children == null ? fg2 : fg);
            DrawingCache cache = c.getDrawingCache();
            cache.drawText(rt, xoffset + 2, (top + bottom - ascent - descent) / 2, elementWidth - 5);
          }
      }

      child_clipTop -= csize;
      child_clipBottom -= csize;
      yoffset = bottom;
    }
  }


  // Copy pasted from paint() and changed to lower overhead on generic drawing code
  private static void paintSpecial(long parent_size, FileSystemEntry[] entries,
      Canvas canvas, float xoffset, float yoffset, float yscale,
      long clipLeft, long clipRight, long clipTop, long clipBottom,
      int screenHeight, int numSpecial) {

    // Deep one level in hierarchy:
    entries = entries[0].children;
    xoffset += elementWidth;
    clipLeft -= elementWidth;
    clipRight -= elementWidth;

    // Paint as paint():
    FileSystemEntry children[] = entries;
    int len = children.length;
    long child_clipTop = clipTop;
    long child_clipBottom = clipBottom;
    float child_xoffset = xoffset + elementWidth;

    // Fast skip ordinary entries, FIXME: make root node special node with extra
    // field to get rid of this
    for (int i = 0; i < len - numSpecial; i++) {
      FileSystemEntry c = children[i];
      long csize = c.encodedSize;
      parent_size -= csize;

      float top = yoffset;
      float bottom = top + csize * yscale;

      if (child_clipBottom < 0) {
        return;
      }
      child_clipTop -= csize;
      child_clipBottom -= csize;
      yoffset = bottom;
    }

    for (int i = len - numSpecial; i < len; i++) {
      FileSystemEntry c = children[i];
      long csize = c.encodedSize;
      parent_size -= csize;

      float top = yoffset;
      float bottom = top + csize * yscale;
      ///Log.d("DiskUsage", "child: child_clip_y0 = " + child_clip_y0);
      ///Log.d("DiskUsage", "child: child_clip_y1 = " + child_clip_y1);

      if (child_clipTop > csize) {

        child_clipTop -= csize;
        child_clipBottom -= csize;
        yoffset = bottom;
        continue;
      }

      if (child_clipBottom < 0) {
        ///Log.d("DiskUsage", "skipped rest starting from " + c.name);
        return;
      }

      if (clipLeft < elementWidth) {
          // FIXME
          float windowHeight0 = screenHeight;
          float fontSize0 = fontSize;
          float top0 = top;
          float bottom0 = bottom;

          canvas.drawRect(xoffset, top0, child_xoffset, bottom0, bg_emptySpace);
          canvas.drawRect(xoffset, top0, child_xoffset, bottom0, fg_rect);

          if (bottom - top > fontSize0 * 2) {
            float pos = (top + bottom) * 0.5f;
            if (pos < fontSize0) {
              if (bottom > 2 * fontSize0) {
                pos = fontSize0;
              } else {
                pos = bottom - fontSize0;
              }
            } else if (pos > windowHeight0 - fontSize0) {
              if (top < windowHeight0 - 2 * fontSize0) {
                pos = windowHeight0 - fontSize0;
              } else {
                pos = top + fontSize0;
              }
            }
            float pos1 = pos - descent;
            float pos2 = pos - ascent;

            DrawingCache cache = c.getDrawingCache();
            String sizeString = cache.getSizeString();
            int cliplen = fg2.breakText(c.name, true, elementWidth - 4, null);
            String clippedName = c.name.substring(0, cliplen);
            canvas.drawText(clippedName,  xoffset + 2, pos1, textPaintFolder);
            canvas.drawText(sizeString, xoffset + 2, pos2, textPaintFolder);
          } else if (bottom - top > fontSize0) {
            int cliplen = fg2.breakText(c.name, true, elementWidth - 4, null);
            String clippedName = c.name.substring(0, cliplen);
            canvas.drawText(clippedName, xoffset + 2,
                (top + bottom - ascent - descent) / 2,
                c.children == null ? textPaintFile : textPaintFolder);
          }
      }

      child_clipTop -= csize;
      child_clipBottom -= csize;
      yoffset = bottom;
    }
  }

  private static void paintGPU(long parent_size, FileSystemEntry[] entries,
      RenderingThread rt, float xoffset, float yoffset, float yscale,
      long clipLeft, long clipRight, long clipTop, long clipBottom,
      int screenHeight) {

    FileSystemEntry children[] = entries;
    int len = children.length;
    long child_clipLeft = clipLeft - elementWidth;
    long child_clipRight = clipRight - elementWidth;
    long child_clipTop = clipTop;
    long child_clipBottom = clipBottom;
    float child_xoffset = xoffset + elementWidth;

    for (int i = 0; i < len; i++) {
      FileSystemEntry c = children[i];
      long csize = c.encodedSize;
      parent_size -= csize;

      float top = yoffset;
      float bottom = top + csize * yscale;
      ///Log.d("DiskUsage", "child: child_clip_y0 = " + child_clip_y0);
      ///Log.d("DiskUsage", "child: child_clip_y1 = " + child_clip_y1);

      if (child_clipTop > csize) {

        child_clipTop -= csize;
        child_clipBottom -= csize;
        yoffset = bottom;
        continue;
      }

      if (child_clipBottom < 0) {
        ///Log.d("DiskUsage", "skipped rest starting from " + c.name);
        return;
      }

      FileSystemEntry[] cchildren = c.children;

      if (cchildren != null)
        FileSystemEntry.paintGPU(c.encodedSize, cchildren, rt,
            child_xoffset, yoffset, yscale,
            child_clipLeft, child_clipRight, child_clipTop, child_clipBottom, screenHeight);

      if (bottom - top < 4 && deletedEntry != c) {
        bottom += parent_size * yscale;
        rt.smallSquare.draw(xoffset, top, child_xoffset, bottom);
//        canvas.drawRect(xoffset, top, child_xoffset, bottom, fg_rect);
        return;
      }

      if (clipLeft < elementWidth) {
          // FIXME
          float windowHeight0 = screenHeight;
          float fontSize0 = fontSize;

          float top0 = top;
          float bottom0 = bottom;

          boolean isFile = c.children == null;
          RenderingThread.Square square = isFile? rt.fileSquare : rt.dirSquare;
          square.draw(xoffset, top0, child_xoffset, bottom0);

          if (bottom - top > fontSize0 * 2) {
            float pos = (top + bottom) * 0.5f;
            if (pos < fontSize0) {
              if (bottom > 2 * fontSize0) {
                pos = fontSize0;
              } else {
                pos = bottom - fontSize0;
              }
            } else if (pos > windowHeight0 - fontSize0) {
              if (top < windowHeight0 - 2 * fontSize0) {
                pos = windowHeight0 - fontSize0;
              } else {
                pos = top + fontSize0;
              }
            }
            float pos1 = pos - descent;
            float pos2 = pos - ascent;

            DrawingCache cache = c.getDrawingCache();
            // FIXME: text
            // FIXME: dir or file painted the same way
            cache.drawText(rt, xoffset + 2, pos1, elementWidth - 5);
            cache.drawSize(rt, xoffset + 2, pos2, elementWidth - 5);
          } else if (bottom - top > fontSize0) {
            DrawingCache cache = c.getDrawingCache();
            // FIXME: dir and file painted the same way
            cache.drawText(rt, xoffset + 2, (top + bottom - ascent - descent) / 2, elementWidth - 5);
          }
      }

      child_clipTop -= csize;
      child_clipBottom -= csize;
      yoffset = bottom;
    }
  }

  private static void paint(long parent_size, FileSystemEntry[] entries,
      Canvas canvas, float xoffset, float yoffset, float yscale,
      long clipLeft, long clipRight, long clipTop, long clipBottom,
      int screenHeight) {

    FileSystemEntry children[] = entries;
    int len = children.length;
    long child_clipLeft = clipLeft - elementWidth;
    long child_clipRight = clipRight - elementWidth;
    long child_clipTop = clipTop;
    long child_clipBottom = clipBottom;
    float child_xoffset = xoffset + elementWidth;

    for (int i = 0; i < len; i++) {
      FileSystemEntry c = children[i];
      long csize = c.encodedSize;
      parent_size -= csize;

      float top = yoffset;
      float bottom = top + csize * yscale;
      ///Log.d("DiskUsage", "child: child_clip_y0 = " + child_clip_y0);
      ///Log.d("DiskUsage", "child: child_clip_y1 = " + child_clip_y1);

      if (child_clipTop > csize) {

        child_clipTop -= csize;
        child_clipBottom -= csize;
        yoffset = bottom;
        continue;
      }

      if (child_clipBottom < 0) {
        ///Log.d("DiskUsage", "skipped rest starting from " + c.name);
        return;
      }

      FileSystemEntry[] cchildren = c.children;

      if (cchildren != null)
        FileSystemEntry.paint(c.encodedSize, cchildren, canvas,
            child_xoffset, yoffset, yscale,
            child_clipLeft, child_clipRight, child_clipTop, child_clipBottom, screenHeight);

      if (bottom - top < 4 && deletedEntry != c) {
        bottom += parent_size * yscale;
        canvas.drawRect(xoffset, top, child_xoffset, bottom, fill_bg);
        canvas.drawRect(xoffset, top, child_xoffset, bottom, fg_rect);
        return;
      }

      if (clipLeft < elementWidth) {
          // FIXME
          float windowHeight0 = screenHeight;
          float fontSize0 = fontSize;

          float top0 = top;
          float bottom0 = bottom;

//          float clipedtop0 = top0 <  0 ? 0 : top0;
//          float clipedbottom0 = bottom0 > 800 ? 800 : bottom0;
          canvas.drawRect(xoffset, top0, child_xoffset, bottom0, bg);
          canvas.drawRect(xoffset, top0, child_xoffset, bottom0, fg_rect);

          if (bottom - top > fontSize0 * 2) {
            float pos = (top + bottom) * 0.5f;
            if (pos < fontSize0) {
              if (bottom > 2 * fontSize0) {
                pos = fontSize0;
              } else {
                pos = bottom - fontSize0;
              }
            } else if (pos > windowHeight0 - fontSize0) {
              if (top < windowHeight0 - 2 * fontSize0) {
                pos = windowHeight0 - fontSize0;
              } else {
                pos = top + fontSize0;
              }
            }
            float pos1 = pos - descent;
            float pos2 = pos - ascent;

            DrawingCache cache = c.getDrawingCache();
            String sizeString = cache.getSizeString();
            int cliplen = fg2.breakText(c.name, true, elementWidth - 4, null);
            String clippedName = c.name.substring(0, cliplen);
            Paint paint = c.children == null ? textPaintFile : textPaintFolder;
            canvas.drawText(clippedName,  xoffset + 2, pos1, paint);
            canvas.drawText(sizeString, xoffset + 2, pos2, paint);
          } else if (bottom - top > fontSize0) {
            int cliplen = fg2.breakText(c.name, true, elementWidth - 4, null);
            String clippedName = c.name.substring(0, cliplen);
            Paint paint = c.children == null ? textPaintFile : textPaintFolder;
            canvas.drawText(clippedName, xoffset + 2, (top + bottom - ascent - descent) / 2, paint);
          }
      }

      child_clipTop -= csize;
      child_clipBottom -= csize;
      yoffset = bottom;
    }
  }

  public final void paintGPU(RenderingThread rt,
      Rect bounds, Cursor cursor, long viewTop,
      float viewDepth, float yscale, int screenHeight,
      int numSpecialEntries) {
    // scale conversion:
    // window_y = yscale * world_y
    // world_y  = window_y / yscale

    // offset conversion:
    // window_y = yscale * (world_y  - rootOffset)
    // world_y  = window_y / yscale + rootOffset

    //viewTop = 23 * 1024 * 1024;
    //viewDepth = 0.3f;

    int viewLeft = (int)(viewDepth * elementWidth);

    // screen clip area to world conversion:
    long clipTop = (long)(bounds.top / yscale) + viewTop;
    long clipBottom = (long)(bounds.bottom / yscale) + viewTop;
    int clipLeft = bounds.left + viewLeft;
    int clipRight = bounds.right + viewLeft;
    float xoffset = -viewLeft;
    float yoffset = -viewTop * yscale;

    // X coords:
    // xoffset - screen position of current object on the screen
    // clip_x0, clip_x1 - clip area in coords of current object
    // screen_clip_x0 = xoffset + clip_x0
    // screen_clip_x1 = xoffset + clip_x1

    // Y coords:
    // yoffset - screen position of current object on the screen
    // clip_y0, clip_y1 - clip area in world coords relative to current object
    // screen_clip_y0 = yscale * (clip_y0 - elementOffset)
    // screen_clip_y1 = yscale * (clip_y1 - elementOffset)

    paintGPU(encodedSize, children, rt, xoffset, yoffset, yscale, clipLeft, clipRight,
        clipTop, clipBottom, screenHeight);

    paintSpecialGPU(encodedSize, children, rt, xoffset, yoffset, yscale, clipLeft, clipRight,
        clipTop, clipBottom, screenHeight, numSpecialEntries);

    // paint position
    float cursorLeft = cursor.depth * elementWidth + xoffset;
    float cursorTop = (cursor.top - viewTop) * yscale;
    float cursorRight = cursorLeft + elementWidth;
    float cursorBottom = cursorTop + cursor.position.encodedSize * yscale;
    rt.cursorSquare.drawFrame(cursorLeft, cursorTop, cursorRight, cursorBottom);
  }

  public final void paint(Canvas canvas, Rect bounds, Cursor cursor, long viewTop,
      float viewDepth, float yscale, int screenHeight, int numSpecialEntries) {
    // scale conversion:
    // window_y = yscale * world_y
    // world_y  = window_y / yscale

    // offset conversion:
    // window_y = yscale * (world_y  - rootOffset)
    // world_y  = window_y / yscale + rootOffset

    //viewTop = 23 * 1024 * 1024;
    //viewDepth = 0.3f;

    int viewLeft = (int)(viewDepth * elementWidth);

    // screen clip area to world conversion:
    long clipTop = (long)(bounds.top / yscale) + viewTop;
    long clipBottom = (long)(bounds.bottom / yscale) + viewTop;
    int clipLeft = bounds.left + viewLeft;
    int clipRight = bounds.right + viewLeft;
    float xoffset = -viewLeft;
    float yoffset = -viewTop * yscale;

    // X coords:
    // xoffset - screen position of current object on the screen
    // clip_x0, clip_x1 - clip area in coords of current object
    // screen_clip_x0 = xoffset + clip_x0
    // screen_clip_x1 = xoffset + clip_x1

    // Y coords:
    // yoffset - screen position of current object on the screen
    // clip_y0, clip_y1 - clip area in world coords relative to current object
    // screen_clip_y0 = yscale * (clip_y0 - elementOffset)
    // screen_clip_y1 = yscale * (clip_y1 - elementOffset)

    paint(encodedSize, children, canvas, xoffset, yoffset, yscale, clipLeft, clipRight,
        clipTop, clipBottom, screenHeight);

    paintSpecial(encodedSize, children, canvas, xoffset, yoffset, yscale, clipLeft, clipRight,
        clipTop, clipBottom, screenHeight, numSpecialEntries);

    // paint position
    float cursorLeft = cursor.depth * elementWidth + xoffset;
    float cursorTop = (cursor.top - viewTop) * yscale;
    float cursorRight = cursorLeft + elementWidth;
    float cursorBottom = cursorTop + cursor.position.encodedSize * yscale;
    canvas.drawRect(cursorLeft, cursorTop, cursorRight, cursorBottom, cursor_fg);
  }

  /**
   * Calculate size string for specified file length in bytes.
   *
   * Currently used by delete activity preview file list loader.
   *
   * @param sz file size in bytes
   * @return formated size string
   */
  public static String calcSizeString(float sz) {
    if (sz < 1024 * 1024 * 10) {
      if (sz < 1024 * 1024) {
        if (sz < 1024) {
          if (sz < 0) sz = 0;
          return String.format(n_bytes, (int)sz);
        }
        return String.format(n_kilobytes, (int)(sz * (1f / 1024)));
      }
      return String.format(n_megabytes, sz * (1f / 1024 / 1024));
    }
    if (sz < 1024 * 1024 * 200) {
      return String.format(n_megabytes10, sz * (1f / 1024 / 1024));
    }
    return String.format(n_megabytes100, (int)(sz * (1f / 1024 / 1024)));
  }

  public final String sizeString() {
    return calcSizeStringFromEncoded(encodedSize);
  }

//  public final String pathFromRoot(FileSystemEntry root) {
//    String res = toTitleString();
//    if (parent == root) return res;
//    else return parent.relativePath(root) + "/" + res;
//  }

  public final String toTitleString() {
    String sizeString0 = sizeString();
    if (children != null && children.length != 0)
      return String.format(dir_name_size_num_dirs, name, sizeString0, children.length);
    else if (getSizeInBlocks() == 0) {
      return String.format(dir_empty, name);
    } else {
      return String.format(dir_name_size, name, sizeString0);
    }
  }

  public final String path2() {
    ArrayList<String> pathElements = new ArrayList<String>();
    FileSystemEntry current = this;
    while (current != null) {
      pathElements.add(current.name);
      current = current.parent;
    }
    pathElements.remove(pathElements.size() - 1);
    pathElements.remove(pathElements.size() - 1);
    StringBuilder path = new StringBuilder();
    String sep = "";
    for (int i = pathElements.size() - 1; i >= 0; i--) {
      path.append(sep);
      path.append(pathElements.get(i));
      sep = "/";
    }
    return path.toString();
  }

  public final String absolutePath() {
    if (this == null) {
      // FIXME: to prevent crash appeared on some users.
      return "";
    }
    if (this instanceof FileSystemRoot) {
      return ((FileSystemRoot)this).rootPath;
    }
    return parent.absolutePath() + "/" + name;
  }

  /**
   * Find depth of 'entry' in current element.
   * @param entry
   * @return 1 for depth equal 1 and so on
   */
  public final int depth(FileSystemEntry entry) {
    int d = 0;
    FileSystemEntry root = this;

    while(entry != root) {
      entry = entry.parent;
      d++;
    }
    return d;
  }

  /**
   * Find and return entry on specified depth and offset in this entry used as root.
   * @param maxDepth
   * @param offset
   * @return nearest entry to the specified conditions
   */
  public final FileSystemEntry findEntry(int maxDepth, long offset) {
    long currOffset = 0;
    FileSystemEntry entry = this;
    FileSystemEntry[] children0 = children;
    // Log.d("DiskUsage", "Starting entry search at " + entry.name);

    for (int depth = 0; depth < maxDepth; depth++) {
      int nchildren = children0.length;
      // Log.d("DiskUsage", "  Entry = " + entry.name);
      for (int c = 0; c < nchildren; c++) {
        FileSystemEntry e = children0[c];
        long size = e.encodedSize;
        if (currOffset + size < offset) {
          currOffset += size;
          continue;
        }

        // found entry
        entry = e;
        children0 = e.children;
        if (children0 == null) return entry;
        break;
      }

    }
    return entry;
  }

  /**
   * Returns offset in bytes (world coordinates) from start of this
   * object to the start of 'cursor' object.
   * @param cursor
   * @return offset in bytes
   */
  public final long getOffset(FileSystemEntry cursor) {
    long offset = 0;
    FileSystemEntry dir;
    FileSystemEntry root = this;

//    Log.d("diskusage", "getOffset()");

    while (cursor != root) {
//      Log.d("diskusage", "cursor = " + (cursor != null) + " root = " + (root != null));
//      Log.d("diskusage", "cursor = " + cursor.name + " root = " + root.name);
      dir = cursor.parent;
      FileSystemEntry[] children = dir.children;
      int len = children.length;

      for (int i = 0; i < len; i++) {
        FileSystemEntry e = children[i];
        if (e == cursor) break;
        offset += e.encodedSize;
      }
      cursor = dir;
    }
    return offset;
  }

  // FIXME: no resort needed
  public final void remove(int blockSize) {
    FileSystemEntry[] children0 = parent.children;
    int len = children0.length;
    for (int i = 0; i < len; i++) {
      if (children0[i] != this) continue;

      // executed only once:
      parent.children = new FileSystemEntry[len - 1];
      System.arraycopy(children0, 0, parent.children, 0, i);
      System.arraycopy(children0, i + 1, parent.children, i, len - i - 1);
      //java.util.Arrays.sort(parent.children, this);

      FileSystemEntry parent0 = parent;

      long blocks = getSizeInBlocks();

      while (parent0 != null) {
        parent0.setSizeInBlocks(parent0.getSizeInBlocks() - blocks, blockSize);
        parent0.clearDrawingCache();
//        java.util.Arrays.sort(parent0.children, this);
        parent0 = parent0.parent;
      }
      return;
    }
    // FIXME: the exception was thrown somehow
    // throw new RuntimeException("child is not found: " + this);
  }

  public final void insert(FileSystemEntry newEntry, int blockSize) {
    FileSystemEntry[] children0 = new FileSystemEntry[children.length + 1];
    System.arraycopy(children, 0, children0, 0, children.length);
    children0[children.length] = newEntry;
    children = children0;
    newEntry.parent = this;
    FileSystemEntry parent0 = this;
    long blocks = newEntry.getSizeInBlocks();

    while (parent0 != null) {
      java.util.Arrays.sort(children, COMPARE);
      parent0.setSizeInBlocks(parent0.getSizeInBlocks() + blocks, blockSize);
      parent0.clearDrawingCache();
      parent0 = parent0.parent;
    }
  }

  /**
   * Walks through the path and finds the specified entry, null otherwise.
   * @param exactMatch TODO
   */
  public FileSystemEntry getEntryByName(String path, boolean exactMatch) {
    Log.d("diskusage", "getEntryForName = " + path);
    String[] pathElements = path.split("/");
    FileSystemEntry entry = this;

    outer:
      for (int i = 0; i < pathElements.length; i++) {
        String name = pathElements[i];
        FileSystemEntry[] children = entry.children;
        if (children == null) {
          return null;
        }
        for (int j = 0; j < children.length; j++) {
          entry = children[j];
          if (name.equals(entry.name)) {
            continue outer;
          }
        }
        return null;
      }
    return entry;
  }

  public static final int padding = 4;

  public static void setupStrings(Context context) {
    if (n_bytes != null) return;
    n_bytes = context.getString(R.string.n_bytes);
    n_kilobytes = context.getString(R.string.n_kilobytes);
    n_megabytes = context.getString(R.string.n_megabytes);
    n_megabytes10 = context.getString(R.string.n_megabytes10);
    n_megabytes100 = context.getString(R.string.n_megabytes100);
    dir_name_size_num_dirs = context.getString(R.string.dir_name_size_num_dirs);
    dir_empty = context.getString(R.string.dir_empty);
    dir_name_size = context.getString(R.string.dir_name_size);
  }

  public static void updateFontsLegacy(Context context) {
    float textSize = context.getResources().getDisplayMetrics().scaledDensity
        * 12 + 0.5f;
    if (textSize < 10) textSize = 10;
    updateFonts(textSize);
  }

  public static void updateFonts(float textSize) {
    textPaintFile.setTextSize(textSize);
    textPaintFolder.setTextSize(textSize);
    ascent = textPaintFolder.ascent();
    descent = textPaintFolder.descent();
    fontSize = descent - ascent;
  }


//  public final void getAllChildren(List<String> out, FileSystemEntry deleteRoot) {
//    FileSystemEntry[] sortedChildren = new FileSystemEntry[children.length];
//    System.arraycopy(children, 0, sortedChildren, 0, children.length);
//    Arrays.sort(sortedChildren, alphaComparator);
//    for (int i = 0; i < children.length; i++) {
//      FileSystemEntry child = children[i];
//      if (child.children != null) child.getAllChildren(out, deleteRoot);
//      else out.add(child.pathFromRoot(deleteRoot));
//    }
//  }

  private void validate0() {
    if (parent != null) {
      parent.getIndexOf(this);
      validateRecursive();
      parent.validate0();
      return;
    }
    validateRecursive();
  }

  private void validateRecursive() {
    if (children == null) return;
    for (int i = 0; i < children.length; i++) {
      if (children[i].parent != this) throw new RuntimeException("corrupted: " + this.path2() + " <> " + children[i].name);
      children[i].validateRecursive();
    }
  }

  /**
   * Just files, no directories.
   * @return count
   */
  public int getNumFiles() {
    if (this instanceof FileSystemEntrySmall) {
      return ((FileSystemEntrySmall)this).numFiles;
    }

    if (children == null) return 1;

    int numFiles = 0;
    boolean hasFile = false;
    for (FileSystemEntry entry : children) {
      if (entry.children == null) hasFile = true;
      numFiles += entry.getNumFiles();
    }
    if (hasFile) numFiles++;
    return numFiles;
  }

}
