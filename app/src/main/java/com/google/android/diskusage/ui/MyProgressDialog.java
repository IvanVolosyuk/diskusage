/*
 * DiskUsage - displays sdcard usage on android.
 * Copyright (C) 2008-2011 Ivan Volosyuk
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

package com.google.android.diskusage.ui;

import java.text.NumberFormat;
import java.util.ArrayList;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Paint;
import android.os.Bundle;
import androidx.annotation.NonNull;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import com.google.android.diskusage.databinding.ProgressBinding;
import com.google.android.diskusage.filesystem.entity.FileSystemEntry;

public class MyProgressDialog extends AlertDialog {
  private final Context context;
  private ProgressBinding binding;
  private CharSequence details;

  private long progress;
  private long max;
  private NumberFormat progressPercentFormat;

  public MyProgressDialog(Context context) {
    super(context);
    this.context = context;
  }

  public void setMax(long max) {
    this.max = max;
  }
  private int depth = 0;
  private boolean warned = false;

  @NonNull
  private String path(FileSystemEntry entry) {
    ArrayList<String> pathElements = new ArrayList<>();
    FileSystemEntry current = entry;
    while (current != null) {
      pathElements.add(current.name);
      current = current.parent;
    }

    depth = pathElements.size();
    if (pathElements.size() < 2) return "";
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

  char[] prevPathChars = new char[0];

  private String makePathString(@NonNull String path) {
//    Log.d("diskusage", "path = " + path);
    char[] pathChars = path.toCharArray();
    char[] prevPathChars = this.prevPathChars;
    int len = Math.min(pathChars.length, prevPathChars.length);
    int diff;
    Paint textPaint = binding.progressDetails.getPaint();

    for (diff = 0; diff < len; diff++) {
      if (pathChars[diff] == prevPathChars[diff]) continue;
      break;
    }

    float winWidth = binding.progressDetails.getWidth();
    float extraTextWidth = textPaint.measureText("/.../G");
    float width = winWidth - extraTextWidth;
    if (width < extraTextWidth) return path;

    int firstSep = -2;
    int lastSep = -2;

    try {
      if (textPaint.measureText(path, 0, diff) < width) {
        this.prevPathChars = pathChars;
        return path;
      }

      lastSep = path.lastIndexOf('/', diff);
      firstSep = path.indexOf("/");

      if (lastSep == -1 || firstSep == -1) return path;

      float firstPart = textPaint.measureText(path, 0, firstSep);
      float lastPart = textPaint.measureText(path, lastSep, diff);
      if (firstPart + lastPart > width) {
        // need to break first and last string
        do {
          if (firstPart > lastPart * 3) {
            firstSep /= 2;
            firstPart = textPaint.measureText(path, 0, firstSep);
          } else {
            lastSep = (lastSep + diff) / 2;
            lastPart = textPaint.measureText(path, lastSep, diff);
          }
        } while (firstPart + lastPart > width);

        this.prevPathChars = pathChars;
        return path.substring(0, firstSep) + "..." + path.substring(lastSep);
      }

      while (true) {
        boolean success = false;

        int newLastSep = path.lastIndexOf('/', lastSep - 1);
        if (newLastSep != -1 && newLastSep >= firstSep) {
          float newLastPart = textPaint.measureText(path, newLastSep, diff);
          if (firstPart + newLastPart < width) {
            success = true;
            lastPart = newLastPart;
            lastSep = newLastSep;
          }
        }

        int newFirstSep = path.indexOf('/', firstSep + 1);
        if (newFirstSep != -1 && newFirstSep <= lastSep) {
          float newFirstPart = textPaint.measureText(path, 0, newFirstSep);
          if (newFirstPart + lastPart < width) {
            success = true;
            firstPart = newFirstPart;
            firstSep = newFirstSep;
          }
        }

        if (!success) {
          this.prevPathChars = pathChars;
          if (firstSep >= lastSep) {
            return path;
          }
          return path.substring(0, firstSep) + "/.../" + path.substring(lastSep + 1);
        }
      }
    } catch (RuntimeException e) {
      throw new RuntimeException(
          "path = " + path + "[" + firstSep + ":" + lastSep + "]" +
          " win =" + winWidth + " extra=" + extraTextWidth + " diff=" + diff,
          e);
    }
  }

  public void onProgressChanged() {
    /* Update the number and percent */
    double percent = (double) progress / (double) max * basePercent + (1 - basePercent);
    binding.progress.setProgress((int)(percent * 10000));
    binding.progressDetails.setText(details);  // progressNumber.setText(String.format(format, progress, max));
    SpannableString tmp = new SpannableString(progressPercentFormat.format(percent));
    tmp.setSpan(new StyleSpan(android.graphics.Typeface.BOLD),
        0, tmp.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    binding.progressPercent.setText(tmp);
//    Log.d("diskusage", "details: " + details);
//    Log.d("diskusage", "depth = " + depth);
    if (depth > 40 && !warned) {
      warned = true;
      setMessage("Cyclic dirs? Broken filesystem?");
    }
  }

  public void setProgress(long progress, FileSystemEntry entry) {
    this.progress = progress;
    this.details = makePathString(path(entry));
//    Log.d("diskusage", "makePath = " + this.details);
    onProgressChanged();
  }

  double basePercent = 1;

  public void switchToSecondary() {
    basePercent = 1 - (double) progress / (double) max;
  }

  public void setProgress(long progress, CharSequence details) {
    this.progress = progress;
    this.details = details;
    onProgressChanged();
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    binding = ProgressBinding.inflate(LayoutInflater.from(context));
    progressPercentFormat = NumberFormat.getPercentInstance();
    progressPercentFormat.setMaximumFractionDigits(0);
    binding.progress.setMax(10000);
    setView(binding.getRoot());
    onProgressChanged();
    super.onCreate(savedInstanceState);
  }
}
