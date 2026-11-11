package com.musicplayer.lite;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;

/** An ImageView clipped to the rounded corners of its background (works from Android 5). */
public class RoundedImageView extends ImageView {

    public RoundedImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        setClipToOutline(true);
    }
}
