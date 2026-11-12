package com.musicplayer.lite;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * The window background of the liquid-glass look. It can show:
 * <ul>
 *   <li>the current cover art, shrunk to a few pixels (which blurs it), blown back up and
 *       toned for legibility; with nothing playing, a soft aurora;</li>
 *   <li>a picture the user chose, or the device wallpaper, softened the same way but cropped
 *       to the screen instead of stretched;</li>
 *   <li>a plain white or black page.</li>
 * </ul>
 * When what it shows changes, the new backdrop fades in.
 */
final class BackdropDrawable extends Drawable {
    private static final int SIZE = 24;
    /** The short side of a softened picture, in pixels. */
    private static final int PICTURE_SIDE = 72;
    private static final int FADE_MS = 500;

    /** The brightest the backdrop gets at night, so light text stays readable on top of it. */
    static final float NIGHT_MAX_LUMA = 0.26f;
    /** The darkest the backdrop gets by day, so dark text stays readable on top of it. */
    static final float DAY_MIN_LUMA = 0.80f;

    /** One thing the backdrop can show. */
    private static final class Layer {
        /** Null for the aurora and for the plain page. */
        final Bitmap bitmap;
        /** Pictures are cropped to the window; covers are stretched. */
        final boolean crop;
        final boolean plain;

        Layer(Bitmap bitmap, boolean crop, boolean plain) {
            this.bitmap = bitmap;
            this.crop = crop;
            this.plain = plain;
        }
    }

    private final boolean night;
    private final int plainColor;
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scrimPaint = new Paint();
    private final RectF dst = new RectF();
    private final Rect src = new Rect();

    private Layer base = new Layer(null, false, false);
    private Layer incoming;
    private float fade;
    private ValueAnimator animator;

    private Shader auroraBase;
    private final Shader[] glows = new Shader[3];
    private final float[][] glowAt = {{0.86f, 0.10f}, {0.08f, 0.86f}, {0.22f, 0.34f}};

    BackdropDrawable(Context c) {
        night = Appearance.isNight(c);
        plainColor = c.getColor(R.color.bg);
        ColorMatrix vivid = new ColorMatrix();
        vivid.setSaturation(1.5f);
        bitmapPaint.setColorFilter(new ColorMatrixColorFilter(vivid));
    }

    /**
     * Shows {@code art} (or, without art, colours derived from {@code fallbackKey}).
     * An empty key with no art shows the aurora.
     */
    void setArt(Bitmap art, String fallbackKey, boolean animate) {
        Bitmap next;
        if (art != null) {
            next = tone(blur(art), night);
        } else if (fallbackKey != null && !fallbackKey.isEmpty()) {
            next = tone(blur(fromColors(ArtLoader.paletteFor(fallbackKey))), night);
        } else {
            next = null;
        }
        show(new Layer(next, false, false), animate);
    }

    /** Shows a picture (the user's own, or the wallpaper), softened and cropped to the window. */
    void setPicture(Bitmap picture, boolean animate) {
        if (picture == null) {
            setPlain(animate);
            return;
        }
        show(new Layer(tone(soften(picture), night), true, false), animate);
    }

    /** A plain page in the theme's background colour: white, or black at night. */
    void setPlain(boolean animate) {
        show(new Layer(null, false, true), animate);
    }

    private void show(Layer layer, boolean animate) {
        if (animator != null) animator.cancel();
        if (!animate) {
            base = layer;
            incoming = null;
            fade = 0f;
            invalidateSelf();
            return;
        }
        if (incoming != null) base = incoming;
        incoming = layer;
        fade = 0f;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(FADE_MS);
        animator.addUpdateListener(a -> {
            fade = (float) a.getAnimatedValue();
            if (fade >= 1f && incoming != null) {
                base = incoming;
                incoming = null;
            }
            invalidateSelf();
        });
        animator.start();
    }

    /** The blurred, toned art on show, or null while the aurora or a plain page is. */
    Bitmap shown() {
        return (incoming != null ? incoming : base).bitmap;
    }

    // ---- drawing ---------------------------------------------------------------------

    @Override
    protected void onBoundsChange(Rect b) {
        if (b.isEmpty()) return;
        final float w = b.width();
        final float h = b.height();
        auroraBase = night
                ? new LinearGradient(0, 0, 0, h, 0xFF121A2E, 0xFF0A0E14, Shader.TileMode.CLAMP)
                : new LinearGradient(0, 0, 0, h, 0xFFEAF2FF, 0xFFFFF3E8, Shader.TileMode.CLAMP);
        int[] colors = night
                ? new int[]{0x38FFB020, 0x2E2BB5A7, 0x296A5BFF}
                : new int[]{0x40FFB020, 0x385AA9FF, 0x2EB08CFF};
        for (int i = 0; i < 3; i++) {
            float radius = Math.max(w, h) * (i == 1 ? 0.75f : 0.6f);
            glows[i] = new RadialGradient(w * glowAt[i][0], h * glowAt[i][1], radius,
                    colors[i], colors[i] & 0x00FFFFFF, Shader.TileMode.CLAMP);
        }
        scrimPaint.setShader(night
                ? new LinearGradient(0, 0, 0, h, 0x4D000000, 0x80000000, Shader.TileMode.CLAMP)
                : new LinearGradient(0, 0, 0, h, 0x66FFFFFF, 0x99FFFFFF, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        drawLayer(canvas, b, base, 255);
        if (incoming != null) drawLayer(canvas, b, incoming, Math.round(255 * fade));
    }

    private void drawLayer(Canvas canvas, Rect b, Layer layer, int alpha) {
        if (alpha <= 0) return;
        if (layer.plain) {
            fillPaint.setShader(null);
            fillPaint.setColor(plainColor);
            fillPaint.setAlpha(alpha);
            canvas.drawRect(b, fillPaint);
            return;
        }
        if (layer.bitmap == null) {
            fillPaint.setAlpha(alpha);
            fillPaint.setShader(auroraBase);
            canvas.drawRect(b, fillPaint);
            for (Shader glow : glows) {
                fillPaint.setShader(glow);
                canvas.drawRect(b, fillPaint);
            }
            return;
        }
        // A little larger than the window, so the blurred edge never shows.
        float growX = b.width() * 0.08f;
        float growY = b.height() * 0.08f;
        dst.set(b.left - growX, b.top - growY, b.right + growX, b.bottom + growY);
        bitmapPaint.setAlpha(alpha);
        if (layer.crop) {
            cropTo(layer.bitmap.getWidth(), layer.bitmap.getHeight(), dst.width(), dst.height(), src);
            canvas.drawBitmap(layer.bitmap, src, dst, bitmapPaint);
        } else {
            canvas.drawBitmap(layer.bitmap, null, dst, bitmapPaint);
        }
        scrimPaint.setAlpha(alpha);
        canvas.drawRect(b, scrimPaint);
    }

    /** The part of a {@code bw} x {@code bh} bitmap that fills a {@code dw} x {@code dh} area without stretching. */
    static void cropTo(int bw, int bh, float dw, float dh, Rect out) {
        final float scale = Math.max(dw / bw, dh / bh);
        final int w = Math.min(bw, Math.round(dw / scale));
        final int h = Math.min(bh, Math.round(dh / scale));
        final int left = (bw - w) / 2;
        final int top = (bh - h) / 2;
        out.set(left, top, left + w, top + h);
    }

    @Override
    public void setAlpha(int alpha) {
        // The backdrop is always opaque.
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        // Not supported.
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }

    // ---- blur ---------------------------------------------------------------------------

    private static Bitmap fromColors(int[] pair) {
        int mid = blend(pair[0], pair[1]);
        Bitmap bmp = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        bmp.setPixel(0, 0, pair[1]);
        bmp.setPixel(1, 0, mid);
        bmp.setPixel(0, 1, mid);
        bmp.setPixel(1, 1, pair[0]);
        return bmp;
    }

    private static int blend(int a, int b) {
        return Color.argb(255, (Color.red(a) + Color.red(b)) / 2,
                (Color.green(a) + Color.green(b)) / 2, (Color.blue(a) + Color.blue(b)) / 2);
    }

    /** Shrinks to SIZE x SIZE and softens with a few box-blur passes. Cheap enough for the UI thread. */
    static Bitmap blur(Bitmap src) {
        return blur(src, SIZE, SIZE);
    }

    /** A picture softened to a few dozen pixels on its short side, keeping its proportions. */
    static Bitmap soften(Bitmap src) {
        final int sw = src.getWidth();
        final int sh = src.getHeight();
        final float scale = PICTURE_SIDE / (float) Math.min(sw, sh);
        return blur(src, Math.max(1, Math.round(sw * scale)), Math.max(1, Math.round(sh * scale)));
    }

    private static Bitmap blur(Bitmap src, int w, int h) {
        Bitmap small = Bitmap.createScaledBitmap(src, w, h, true);
        int[] px = new int[w * h];
        small.getPixels(px, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 3; pass++) {
            boxBlur(px, w, h, true);
            boxBlur(px, w, h, false);
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
    }

    /**
     * Keeps a blurred cover inside a brightness range where the text on top of it is readable,
     * whatever the cover looks like: bright covers are darkened at night and dark covers are
     * lightened by day. The hue stays, so the backdrop still takes its colour from the cover.
     */
    static Bitmap tone(Bitmap blurred, boolean night) {
        final int w = blurred.getWidth();
        final int h = blurred.getHeight();
        int[] px = new int[w * h];
        blurred.getPixels(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < px.length; i++) px[i] = toneColor(px[i], night);
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
    }

    static int toneColor(int c, boolean night) {
        float r = Color.red(c) / 255f;
        float g = Color.green(c) / 255f;
        float b = Color.blue(c) / 255f;
        final float luma = 0.2126f * r + 0.7152f * g + 0.0722f * b;
        if (night && luma > NIGHT_MAX_LUMA) {
            final float k = NIGHT_MAX_LUMA / luma;          // scale towards black
            r *= k;
            g *= k;
            b *= k;
        } else if (!night && luma < DAY_MIN_LUMA) {
            final float k = (DAY_MIN_LUMA - luma) / (1f - luma);   // mix towards white
            r += (1f - r) * k;
            g += (1f - g) * k;
            b += (1f - b) * k;
        }
        return Color.rgb(Math.round(r * 255f), Math.round(g * 255f), Math.round(b * 255f));
    }

    private static void boxBlur(int[] px, int w, int h, boolean horizontal) {
        final int radius = 2;
        final int window = radius * 2 + 1;
        final int lines = horizontal ? h : w;
        final int length = horizontal ? w : h;
        int[] line = new int[length];
        for (int row = 0; row < lines; row++) {
            for (int i = 0; i < length; i++) {
                line[i] = px[horizontal ? row * w + i : i * w + row];
            }
            for (int i = 0; i < length; i++) {
                int a = 0, r = 0, g = 0, b = 0;
                for (int k = -radius; k <= radius; k++) {
                    int c = line[Math.min(length - 1, Math.max(0, i + k))];
                    a += c >>> 24;
                    r += (c >> 16) & 0xFF;
                    g += (c >> 8) & 0xFF;
                    b += c & 0xFF;
                }
                px[horizontal ? row * w + i : i * w + row] =
                        ((a / window) << 24) | ((r / window) << 16) | ((g / window) << 8) | (b / window);
            }
        }
    }
}
