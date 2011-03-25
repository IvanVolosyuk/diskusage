package com.google.android.diskusage;

import android.content.Context;
import android.graphics.Paint;
import android.text.TextPaint;
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
