package com.musicplayer.lite;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * A vertical slider for one equalizer band. The centre line is 0 dB; the bar is
 * drawn from there to the thumb, so boosts and cuts read at a glance.
 */
public class EqSliderView extends View {

    interface Listener {
        void onLevelChanged(EqSliderView slider, int millibels);

        void onDragEnd(EqSliderView slider);
    }

    private static final int STEP = 100; // whole dB

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float thumbRadius;
    private final float inset;
    private final float tickHalf;
    private final int accent;
    private final int dim;

    private int min = -1500;
    private int max = 1500;
    private int level;
    private Listener listener;

    public EqSliderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        final float d = getResources().getDisplayMetrics().density;
        thumbRadius = 11 * d;
        inset = thumbRadius + 2 * d;
        tickHalf = 9 * d;
        accent = context.getColor(R.color.accent);
        dim = context.getColor(R.color.icon_dim);

        track.setColor(context.getColor(R.color.eq_track));
        track.setStrokeWidth(6 * d);
        track.setStrokeCap(Paint.Cap.ROUND);
        fill.setStrokeWidth(6 * d);
        fill.setStrokeCap(Paint.Cap.ROUND);
        tick.setColor(context.getColor(R.color.text_tertiary));
        tick.setStrokeWidth(1.5f * d);
    }

    void setListener(Listener l) {
        listener = l;
    }

    void setRange(int minMb, int maxMb) {
        min = minMb;
        max = Math.max(maxMb, minMb + 1);
        level = Fmt.clamp(level, min, max);
        invalidate();
    }

    void setLevel(int millibels) {
        level = Fmt.clamp(millibels, min, max);
        invalidate();
    }

    int getLevel() {
        return level;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        invalidate();
    }

    private float top() {
        return getPaddingTop() + inset;
    }

    private float bottom() {
        return getHeight() - getPaddingBottom() - inset;
    }

    private float yFor(int mb) {
        float t = (mb - min) / (float) (max - min);
        return bottom() - t * (bottom() - top());
    }

    @Override
    protected void onDraw(Canvas c) {
        final float cx = getWidth() / 2f;
        final float y0 = yFor(0);
        final float y = yFor(level);
        final boolean on = isEnabled();

        c.drawLine(cx, top(), cx, bottom(), track);
        fill.setColor(on ? accent : dim);
        c.drawLine(cx, y0, cx, y, fill);
        c.drawLine(cx - tickHalf, y0, cx + tickHalf, y0, tick);
        thumb.setColor(on ? accent : dim);
        c.drawCircle(cx, y, thumbRadius, thumb);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled()) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                update(e.getY());
                return true;
            case MotionEvent.ACTION_MOVE:
                update(e.getY());
                return true;
            case MotionEvent.ACTION_UP:
                update(e.getY());
                performClick();
                if (listener != null) listener.onDragEnd(this);
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (listener != null) listener.onDragEnd(this);
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void update(float y) {
        float span = bottom() - top();
        if (span <= 0) return;
        float t = (bottom() - y) / span;
        int raw = Math.round(min + t * (max - min));
        int snapped = Fmt.clamp(Math.round(raw / (float) STEP) * STEP, min, max);
        if (snapped != level) {
            level = snapped;
            invalidate();
            if (listener != null) listener.onLevelChanged(this, level);
        }
    }
}
