package com.musicplayer.lite;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/**
 * The equalizer response as a smooth curve. Points sit over the centres of the
 * band sliders below, so the two line up.
 */
public class EqCurveView extends View {
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint zero = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint area = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path curve = new Path();
    private final Path shade = new Path();
    private final float pad;
    private final int accent;
    private final int dim;

    private int[] levels = new int[0];
    private float[] xs = new float[2];
    private float[] ys = new float[2];
    /** The curve shows +/- this many millibels; levels beyond it flatten against the edge. */
    private int span = 1000;
    private boolean active = true;

    public EqCurveView(Context context, AttributeSet attrs) {
        super(context, attrs);
        final float d = getResources().getDisplayMetrics().density;
        pad = 10 * d;
        accent = context.getColor(R.color.accent);
        dim = context.getColor(R.color.icon_dim);

        grid.setColor(context.getColor(R.color.divider));
        grid.setStrokeWidth(1 * d);
        zero.setColor(context.getColor(R.color.outline));
        zero.setStrokeWidth(1.5f * d);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2.5f * d);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        area.setStyle(Paint.Style.FILL);
    }

    void setData(int[] levelsMb, int minMb, int maxMb) {
        levels = levelsMb.clone();
        if (xs.length != levels.length + 2) {
            xs = new float[levels.length + 2];
            ys = new float[levels.length + 2];
        }
        span = Math.max(100, Math.min(1000, Math.max(Math.abs(minMb), Math.abs(maxMb))));
        invalidate();
    }

    void setActive(boolean on) {
        active = on;
        invalidate();
    }

    private float yFor(int mb) {
        float t = (Fmt.clamp(mb, -span, span) + span) / (2f * span);
        return getHeight() - pad - t * (getHeight() - 2 * pad);
    }

    @Override
    protected void onDraw(Canvas c) {
        final float w = getWidth();
        final float top = yFor(span);
        final float bottom = yFor(-span);
        c.drawLine(0, top, w, top, grid);
        c.drawLine(0, bottom, w, bottom, grid);
        final float y0 = yFor(0);
        c.drawLine(0, y0, w, y0, zero);

        final int n = levels.length;
        if (n == 0) return;

        // Points over the band centres, plus flat extensions to both edges.
        final float colW = w / n;
        for (int i = 0; i < n; i++) {
            xs[i + 1] = colW * (i + 0.5f);
            ys[i + 1] = yFor(levels[i]);
        }
        xs[0] = 0;
        ys[0] = ys[1];
        xs[n + 1] = w;
        ys[n + 1] = ys[n];

        curve.reset();
        curve.moveTo(xs[0], ys[0]);
        final int last = n + 1;
        for (int i = 0; i < last; i++) {
            final int p0 = Math.max(i - 1, 0);
            final int p3 = Math.min(i + 2, last);
            final float c1x = xs[i] + (xs[i + 1] - xs[p0]) / 6f;
            final float c1y = ys[i] + (ys[i + 1] - ys[p0]) / 6f;
            final float c2x = xs[i + 1] - (xs[p3] - xs[i]) / 6f;
            final float c2y = ys[i + 1] - (ys[p3] - ys[i]) / 6f;
            curve.cubicTo(c1x, c1y, c2x, c2y, xs[i + 1], ys[i + 1]);
        }

        final int color = active ? accent : dim;
        shade.set(curve);
        shade.lineTo(w, y0);
        shade.lineTo(0, y0);
        shade.close();
        area.setColor(color);
        area.setAlpha(active ? 48 : 28);
        c.drawPath(shade, area);

        line.setColor(color);
        c.drawPath(curve, line);
    }
}
