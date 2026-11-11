package com.musicplayer.lite;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * A frosted-glass panel: a translucent fill, a bright highlight along the top, a soft shade
 * along the bottom and a gradient rim that is brightest at the top left. It is drawn over the
 * blurred backdrop, so the frosting comes from what is behind it.
 */
final class GlassDrawable extends Drawable {
    static final int CARD = 0;
    static final int PILL = 1;

    private final float density;
    private final boolean night;
    private final int shape;
    private final boolean accent;
    private final boolean strong;

    private final float cardRadius;
    private final float stroke;
    private final int fillColor;
    private final int highlight;
    private final int shade;
    private final int rimBright;
    private final int rimFaint;
    private final int hairline;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint top = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bottom = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hair = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int alpha = 255;

    /**
     * @param accent amber-tinted glass for the main action
     * @param strong brighter glass, used for the selected segment of a control
     */
    static GlassDrawable create(Context c, int shape, boolean accent, boolean strong) {
        return new GlassDrawable(c.getResources().getDisplayMetrics().density,
                Appearance.isNight(c), shape, accent, strong);
    }

    private GlassDrawable(float density, boolean night, int shape, boolean accent, boolean strong) {
        this.density = density;
        this.night = night;
        this.shape = shape;
        this.accent = accent;
        this.strong = strong;
        cardRadius = 24 * density;
        stroke = 1.2f * density;

        if (accent) {
            fillColor = 0xE6FFB020;
            highlight = 0x59FFFFFF;
            shade = 0x1A000000;
            rimBright = 0x99FFFFFF;
            rimFaint = 0x26FFFFFF;
            hairline = 0;
        } else if (night) {
            fillColor = strong ? 0x33FFFFFF : 0x0FFFFFFF;
            highlight = strong ? 0x2EFFFFFF : 0x1AFFFFFF;
            shade = 0x24000000;
            rimBright = 0x6BFFFFFF;
            rimFaint = 0x0DFFFFFF;
            hairline = 0;
        } else {
            fillColor = strong ? 0xB3FFFFFF : 0x80FFFFFF;
            highlight = 0x8CFFFFFF;
            shade = 0x1A7A8CA0;
            rimBright = 0xF2FFFFFF;
            rimFaint = 0x59FFFFFF;
            hairline = 0x0F000000;
        }
        fill.setColor(fillColor);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(stroke);
        hair.setStyle(Paint.Style.STROKE);
        hair.setStrokeWidth(density);
        hair.setColor(hairline);
    }

    private static int transparent(int color) {
        return color & 0x00FFFFFF;
    }

    @Override
    protected void onBoundsChange(Rect b) {
        if (b.isEmpty()) return;
        top.setShader(new LinearGradient(0, b.top, 0, b.top + b.height() * 0.55f,
                highlight, transparent(highlight), Shader.TileMode.CLAMP));
        bottom.setShader(new LinearGradient(0, b.bottom, 0, b.bottom - b.height() * 0.35f,
                shade, transparent(shade), Shader.TileMode.CLAMP));
        int rimEnd = (((rimBright >>> 24) / 2) << 24) | transparent(rimBright);
        rim.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom,
                new int[]{rimBright, rimFaint, rimEnd}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
    }

    private float radiusFor(Rect b) {
        float half = Math.min(b.width(), b.height()) / 2f;
        return shape == PILL ? half : Math.min(cardRadius, half);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        int saved = -1;
        if (alpha < 255) saved = canvas.saveLayerAlpha(new RectF(b), alpha);
        rect.set(b);
        rect.inset(stroke / 2f, stroke / 2f);
        float r = Math.max(0f, radiusFor(b) - stroke / 2f);
        canvas.drawRoundRect(rect, r, r, fill);
        canvas.drawRoundRect(rect, r, r, top);
        canvas.drawRoundRect(rect, r, r, bottom);
        canvas.drawRoundRect(rect, r, r, rim);
        if (hairline != 0) canvas.drawRoundRect(rect, r, r, hair);
        if (saved >= 0) canvas.restoreToCount(saved);
    }

    @Override
    public void getOutline(Outline outline) {
        Rect b = getBounds();
        outline.setRoundRect(b, radiusFor(b));
    }

    @Override
    public void setAlpha(int a) {
        alpha = a;
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        // The glass colours are fixed; tinting is not supported.
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public ConstantState getConstantState() {
        final float d = density;
        final boolean n = night;
        final int s = shape;
        final boolean a = accent;
        final boolean st = strong;
        return new ConstantState() {
            @Override
            public Drawable newDrawable() {
                return new GlassDrawable(d, n, s, a, st);
            }

            @Override
            public int getChangingConfigurations() {
                return 0;
            }
        };
    }
}
