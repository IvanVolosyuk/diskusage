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

package com.google.android.diskusage;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.TextView;

/**
 * TextView with message that fits the screen.
 * @author vol
 *
 */
public class MyTextView extends TextView {
  public MyTextView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }
  
  float originalTextFont = 0;
  
  public void scale() {
    int size = getWidth();
    if (originalTextFont == 0) {
      originalTextFont = getTextSize();
    }
    setTextSize(TypedValue.COMPLEX_UNIT_PX, originalTextFont);
    float textSize = getPaint().measureText(getText().toString());
    
    if (textSize > size) {
      setTextSize(TypedValue.COMPLEX_UNIT_PX,
          originalTextFont / textSize * size);
    }
  }
  
  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    scale();
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }
}
