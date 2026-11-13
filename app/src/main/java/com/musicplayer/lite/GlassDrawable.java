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
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int glowColor;
    private final RectF rect = new RectF();
    private final RectF rectGlow = new RectF();
    private int alpha = 255;

    /** Glass over a cover or a picture: mostly see-through. */
    private static final int STYLE_GLASS = 0;
    /** Glass over a plain white or black page: needs a fill of its own to be seen. */
    private static final int STYLE_PLAIN = 1;
    /** The card of a dialog, over dimmed content: nearly opaque. */
    private static final int STYLE_DIALOG = 2;

    private final int style;

    /**
     * @param accent red-tinted glass for the main action
     * @param strong brighter glass, used for the selected segment of a control
     */
    static GlassDrawable create(Context c, int shape, boolean accent, boolean strong) {
        return new GlassDrawable(c.getResources().getDisplayMetrics().density,
                Appearance.isNight(c), shape, accent, strong,
                Appearance.glassPlain(c) ? STYLE_PLAIN : STYLE_GLASS);
    }

    /** The card of a glass dialog or action sheet. */
    static GlassDrawable dialog(Context c) {
        return new GlassDrawable(c.getResources().getDisplayMetrics().density,
                Appearance.isNight(c), CARD, false, false, STYLE_DIALOG);
    }

    private GlassDrawable(float density, boolean night, int shape, boolean accent, boolean strong, int style) {
        this.density = density;
        this.night = night;
        this.shape = shape;
        this.accent = accent;
        this.strong = strong;
        this.style = style;
        cardRadius = (style == STYLE_DIALOG ? 28 : 26) * density;
        stroke = 1.2f * density;

        if (accent) {
            fillColor = 0xF2E5202B;
            highlight = 0x4DFFFFFF;
            shade = 0x26000000;
            rimBright = 0xA6FFFFFF;
            rimFaint = 0x33FFFFFF;
            hairline = 0;
        } else if (style == STYLE_DIALOG) {
            if (night) {
                fillColor = 0xF51C1C1E;
                highlight = 0x1AFFFFFF;
                shade = 0x33000000;
                rimBright = 0x4DFFFFFF;
                rimFaint = 0x0FFFFFFF;
                hairline = 0;
            } else {
                fillColor = 0xF5FFFFFF;
                highlight = 0xCCFFFFFF;
                shade = 0x14000000;
                rimBright = 0xFFFFFFFF;
                rimFaint = 0x80FFFFFF;
                hairline = 0x1F000000;
            }
        } else if (style == STYLE_PLAIN) {
            if (night) {
                fillColor = strong ? 0xFF3A3A3C : 0xE61C1C1E;
                highlight = 0x14FFFFFF;
                shade = 0x33000000;
                rimBright = 0x40FFFFFF;
                rimFaint = 0x0DFFFFFF;
                hairline = 0;
            } else {
                fillColor = strong ? 0xFFFFFFFF : 0xE6F2F2F7;
                highlight = 0x99FFFFFF;
                shade = 0x0F000000;
                rimBright = 0xFFFFFFFF;
                rimFaint = 0x66FFFFFF;
                hairline = strong ? 0x24000000 : 0x14000000;
            }
        } else if (night) {
            fillColor = strong ? 0x30FFFFFF : 0x0AFFFFFF;
            highlight = strong ? 0x2EFFFFFF : 0x1FFFFFFF;
            shade = 0x24000000;
            rimBright = 0x6BFFFFFF;
            rimFaint = 0x0DFFFFFF;
            hairline = 0;
        } else {
            fillColor = strong ? 0xB3FFFFFF : 0x66FFFFFF;
            highlight = 0x99FFFFFF;
            shade = 0x1A7A8CA0;
            rimBright = 0xF2FFFFFF;
            rimFaint = 0x59FFFFFF;
            hairline = 0x0F000000;
        }
        glowColor = style == STYLE_GLASS ? (night ? 0x22FFFFFF : 0x66FFFFFF) : 0;
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeWidth(3.2f * density);
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
        // Light catches two opposite edges, top left and bottom right, like a thick pane of glass.
        int rimEnd = (((rimBright >>> 24) * 3 / 4) << 24) | transparent(rimBright);
        rim.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom,
                new int[]{rimBright, rimFaint, rimFaint, rimEnd}, new float[]{0f, 0.32f, 0.68f, 1f},
                Shader.TileMode.CLAMP));
        // A soft inner glow just inside the rim, strongest at the top.
        glow.setShader(new LinearGradient(0, b.top, 0, b.top + b.height() * 0.35f,
                glowColor, transparent(glowColor), Shader.TileMode.CLAMP));
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
        if (glowColor != 0) {
            rectGlow.set(rect);
            rectGlow.inset(2.2f * density, 2.2f * density);
            float rg = Math.max(0f, r - 2.2f * density);
            canvas.drawRoundRect(rectGlow, rg, rg, glow);
        }
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
        final int sty = style;
        return new ConstantState() {
            @Override
            public Drawable newDrawable() {
                return new GlassDrawable(d, n, s, a, st, sty);
            }

            @Override
            public int getChangingConfigurations() {
                return 0;
            }
        };
    }
}
