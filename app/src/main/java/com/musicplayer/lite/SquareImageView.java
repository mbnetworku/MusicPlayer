package com.musicplayer.lite;

import android.content.Context;
import android.util.AttributeSet;

/** An ImageView that is as large a square as its parent allows. */
public class SquareImageView extends RoundedImageView {

    public SquareImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        final int w = MeasureSpec.getSize(widthSpec);
        final int h = MeasureSpec.getSize(heightSpec);
        final boolean wFree = MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED;
        final boolean hFree = MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED;
        final int size;
        if (wFree && hFree) size = 0;
        else if (wFree) size = h;
        else if (hFree) size = w;
        else size = Math.min(w, h);
        setMeasuredDimension(size, size);
    }
}
