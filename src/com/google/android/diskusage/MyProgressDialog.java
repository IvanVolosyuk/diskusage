package com.google.android.diskusage;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

public class MyProgressDialog extends AlertDialog {
  private Context context;
  private TextView percentView;
  private TextView detailsView;
  private ProgressBar progressBar;
//  private SpannableString details;
  private String details;
  
  private long progress;
  private long max;
  private NumberFormat progressPercentFormat;

  protected MyProgressDialog(Context context) {
    super(context);
    this.context = context;
  }

  public void setMax(long max) {
    this.max = max;
  }

  public final String path(FileSystemEntry entry) {
    ArrayList<String> pathElements = new ArrayList<String>();
    FileSystemEntry current = entry;
    while (current != null) {
      pathElements.add(current.name);
      current = current.parent;
    }
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

  private String makePathString(FileSystemEntry entry) {
    String path = path(entry);
    char[] pathChars = path.toCharArray();
    char[] prevPathChars = this.prevPathChars;
    int len = Math.min(pathChars.length, prevPathChars.length);
    int diff;
    TextView detailsView = this.detailsView;
    Paint textPaint = detailsView.getPaint();
    
    for (diff = 0; diff < len; diff++) {
      if (pathChars[diff] == prevPathChars[diff]) continue;
      diff++;
      if (diff > len) diff = len;
      break;
    }
    float width = detailsView.getWidth() - textPaint.measureText("/.../");
    
    if (textPaint.measureText(path, 0, diff) < width) {
      this.prevPathChars = pathChars;
      return path;
    }
     
    int lastSep = path.lastIndexOf('/', diff);
    int firstSep = path.indexOf("/");
    
    if (lastSep == -1 || firstSep == -1) return path;
    
    float firstPart = textPaint.measureText(path, 0, firstSep);
    float lastPart = textPaint.measureText(path, lastSep + 1, diff);
    if (firstPart + lastPart > width) {
      // need to break first and last string
      do {
        if (firstPart > lastPart) {
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
      float newLastPart = textPaint.measureText(path, newLastSep + 1, diff);
      if (firstPart + newLastPart < width) {
        success = true;
        lastPart = newLastPart;
        lastSep = newLastSep;
      }
      
      int newFirstSep = path.indexOf('/', firstSep + 1);
      float newFirstPart = textPaint.measureText(path, 0, newFirstSep);
      if (newFirstPart + lastPart < width) {
        success = true;
        firstPart = newFirstPart;
        firstSep = newFirstSep;
      }
      
      if (!success) {
        this.prevPathChars = pathChars;
        return path.substring(0, firstSep) + "/.../" + path.substring(lastSep + 1);
      }
    }
  }

  public void onProgressChanged() {
    /* Update the number and percent */
    long progress = MyProgressDialog.this.progress;
    long max = MyProgressDialog.this.max;
    double percent = (double) progress / (double) max * basePercent + (1 - basePercent);
    progressBar.setProgress((int)(percent * 10000));
    detailsView.setText(details);  // progressNumber.setText(String.format(format, progress, max));
    SpannableString tmp = new SpannableString(progressPercentFormat.format(percent));
    tmp.setSpan(new StyleSpan(android.graphics.Typeface.BOLD),
        0, tmp.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    percentView.setText(tmp);
//    Log.d("diskusage", "details: " + details);
  }

  public void setProgress(long progress, FileSystemEntry entry) {
    this.progress = progress;
    this.details = makePathString(entry);
//    Log.d("diskusage", "makePath = " + this.details);
    onProgressChanged();
  }
  
  double basePercent = 1;
  
  public void switchToSecondary() {
    basePercent = 1 - (double) progress / (double) max;
  }

  public void setProgress(long progress, String details) {
    this.progress = progress;
    this.details = details;
    onProgressChanged();
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    LayoutInflater inflater = LayoutInflater.from(context);
    View view = inflater.inflate(R.layout.progress, null);
    progressBar = (ProgressBar) view.findViewById(R.id.progress);
    detailsView = (TextView) view.findViewById(R.id.progress_details);
    percentView = (TextView) view.findViewById(R.id.progress_percent);
    progressPercentFormat = NumberFormat.getPercentInstance();
    progressPercentFormat.setMaximumFractionDigits(0);
    setView(view);
    progressBar.setMax(10000);
    onProgressChanged();
    super.onCreate(savedInstanceState);
  }
}
