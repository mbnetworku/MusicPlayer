package com.musicplayer.lite;

import android.annotation.SuppressLint;
import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The picture behind the glass panels when the user chose their own picture or the device
 * wallpaper. The picture is copied (and scaled down) into the app's private storage once, so the
 * app never needs to keep access to the user's gallery or wallpaper.
 */
@SuppressLint("ExifInterface")   // the platform class reads a stream from Android 7, which is enough here
final class BackgroundStore {
    /** What {@link #saveWallpaper} managed to read. */
    static final int FAILED = 0;
    static final int PICTURE = 1;
    static final int COLORS = 2;

    interface Callback {
        void onBitmap(Bitmap bitmap);
    }

    /** Which of the two saved backgrounds: the user's own picture, or the device wallpaper. */
    static final int KIND_PICTURE = 0;
    static final int KIND_WALLPAPER = 1;

    private static final String[] FILES = {"glass_picture.jpg", "glass_wallpaper.jpg"};
    /** Longest side of the stored picture, in pixels. More would only slow the blur down. */
    private static final int MAX_SIDE = 1280;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final Bitmap[] CACHE = new Bitmap[2];
    private static final long[] CACHE_STAMP = new long[2];

    private BackgroundStore() {}

    static File file(Context c, int kind) {
        return new File(c.getFilesDir(), FILES[kind]);
    }

    static boolean has(Context c, int kind) {
        return file(c, kind).isFile();
    }

    /** The saved background that goes with a Settings choice (Appearance.BG_PICTURE or BG_WALLPAPER). */
    static int kindFor(int glassBackground) {
        return glassBackground == Appearance.BG_WALLPAPER ? KIND_WALLPAPER : KIND_PICTURE;
    }

    // ---- saving ------------------------------------------------------------------------------

    /** Copies a picture the user picked. Run on a background thread. Returns false if it cannot be read. */
    static boolean savePicture(Context c, Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                if (in == null) return false;
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sampleSize(Math.max(bounds.outWidth, bounds.outHeight), MAX_SIDE);
            Bitmap decoded;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                if (in == null) return false;
                decoded = BitmapFactory.decodeStream(in, null, opts);
            }
            if (decoded == null) return false;
            int degrees = 0;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                if (in != null) degrees = exifDegrees(new ExifInterface(in));
            } catch (IOException | RuntimeException ignored) {
                // No usable orientation tag: show the picture as it is.
            }
            return write(c, KIND_PICTURE, fitAndRotate(decoded, degrees));
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /**
     * Takes the device wallpaper. Android lets an app read the wallpaper picture only with some
     * permissions (and not at all on the newest versions); without that, the wallpaper's three main
     * colours are always available, so a soft gradient of them is saved instead.
     */
    @SuppressLint({"MissingPermission", "NewApi"})   // the SecurityException below is the answer when it is missing
    static int saveWallpaper(Context c) {
        WallpaperManager wm = WallpaperManager.getInstance(c);
        try {
            Drawable d = wm.getDrawable();
            if (d != null) {
                Bitmap b = fromDrawable(d);
                if (b != null && write(c, KIND_WALLPAPER, b)) return PICTURE;
            }
        } catch (RuntimeException ignored) {
            // The picture is off limits; the colours below are not.
        }
        if (Build.VERSION.SDK_INT >= 27) {
            try {
                android.app.WallpaperColors colors = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
                if (colors != null) {
                    int primary = colors.getPrimaryColor().toArgb();
                    int secondary = colors.getSecondaryColor() != null ? colors.getSecondaryColor().toArgb() : primary;
                    int tertiary = colors.getTertiaryColor() != null ? colors.getTertiaryColor().toArgb() : secondary;
                    if (write(c, KIND_WALLPAPER, gradient(primary, secondary, tertiary))) return COLORS;
                }
            } catch (RuntimeException ignored) {
                // No wallpaper information on this device.
            }
        }
        return FAILED;
    }

    private static Bitmap fromDrawable(Drawable d) {
        int w = d.getIntrinsicWidth();
        int h = d.getIntrinsicHeight();
        if (w <= 0 || h <= 0) return null;
        float scale = Math.min(1f, MAX_SIDE / (float) Math.max(w, h));
        int tw = Math.max(1, Math.round(w * scale));
        int th = Math.max(1, Math.round(h * scale));
        Bitmap b = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(b);
        d.setBounds(0, 0, tw, th);
        d.draw(canvas);
        return b;
    }

    /** A tall soft gradient from the wallpaper's colours, with a glow in the middle. */
    static Bitmap gradient(int primary, int secondary, int tertiary) {
        final int w = 360;
        final int h = 720;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, h, new int[]{primary, secondary, tertiary},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, p);
        p.setShader(new RadialGradient(w * 0.75f, h * 0.3f, h * 0.45f, (secondary & 0x00FFFFFF) | 0x66000000,
                secondary & 0x00FFFFFF, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, p);
        return b;
    }

    private static boolean write(Context c, int kind, Bitmap bitmap) {
        File f = file(c, kind);
        try (FileOutputStream out = new FileOutputStream(f)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)) return false;
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
            return false;
        }
        synchronized (BackgroundStore.class) {
            CACHE[kind] = null;
        }
        return true;
    }

    // ---- loading -----------------------------------------------------------------------------

    /** Loads the saved picture on a background thread and calls back on the main thread (null if none). */
    static void load(final Context c, final int kind, final Callback callback) {
        final Context app = c.getApplicationContext();
        IO.execute(() -> {
            final Bitmap b = loadNow(app, kind);
            MAIN.post(() -> callback.onBitmap(b));
        });
    }

    static synchronized Bitmap loadNow(Context c, int kind) {
        File f = file(c, kind);
        if (!f.isFile()) return null;
        if (CACHE[kind] != null && CACHE_STAMP[kind] == f.lastModified()) return CACHE[kind];
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 2;   // the backdrop is blurred anyway
        Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
        CACHE[kind] = b;
        CACHE_STAMP[kind] = f.lastModified();
        return b;
    }

    // ---- helpers -----------------------------------------------------------------------------

    static int sampleSize(int longest, int target) {
        int s = 1;
        while (longest / (s * 2) >= target) s *= 2;
        return s;
    }

    private static int exifDegrees(ExifInterface exif) {
        switch (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return 90;
            case ExifInterface.ORIENTATION_ROTATE_180:
                return 180;
            case ExifInterface.ORIENTATION_ROTATE_270:
                return 270;
            default:
                return 0;
        }
    }

    private static Bitmap fitAndRotate(Bitmap src, int degrees) {
        float scale = Math.min(1f, MAX_SIDE / (float) Math.max(src.getWidth(), src.getHeight()));
        if (degrees == 0 && scale == 1f) return src;
        Matrix m = new Matrix();
        if (scale != 1f) m.postScale(scale, scale);
        if (degrees != 0) m.postRotate(degrees);
        return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
    }
}
