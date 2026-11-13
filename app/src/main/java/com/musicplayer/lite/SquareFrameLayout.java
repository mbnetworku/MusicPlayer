package com.musicplayer.lite;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/**
 * A frame that is as large a square as its parent allows. Everything inside it is as wide as the
 * square, and as tall too unless it has a height of its own. The cover and the moving bars over it
 * are therefore always exactly as wide as each other.
 */
public class SquareFrameLayout extends FrameLayout {

    public SquareFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        // Measure as a plain frame first, then make it the largest square that fits, and give that
        // square to every child.
        super.onMeasure(widthSpec, heightSpec);
        final int size = Math.min(getMeasuredWidth(), getMeasuredHeight());
        final int exact = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
        for (int i = 0; i < getChildCount(); i++) {
            final android.view.View child = getChildAt(i);
            final int fixedHeight = child.getLayoutParams().height;   // the bars have a height of their own
            child.measure(exact, fixedHeight > 0 ? MeasureSpec.makeMeasureSpec(fixedHeight, MeasureSpec.EXACTLY) : exact);
        }
        setMeasuredDimension(size, size);
    }
}
