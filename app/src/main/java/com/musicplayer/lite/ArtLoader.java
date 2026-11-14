package com.musicplayer.lite;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.Size;
import android.widget.ImageView;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.BooleanSupplier;
import java.util.concurrent.TimeUnit;

/**
 * Album art for lists, the player and the notification. Small memory cache, two
 * worker threads, newest request first so fast scrolling stays responsive.
 */
final class ArtLoader {

    interface Callback {
        /** Called on the main thread; {@code art} is null when the track has none. */
        void onArt(Bitmap art);
    }

    private static final int[][] PALETTE = {
            {0xFF3A2A12, 0xFF6B4A12},
            {0xFF12323A, 0xFF1F5A66},
            {0xFF2B2A4A, 0xFF4B4A86},
            {0xFF3A1F24, 0xFF7A3340},
            {0xFF1F3A26, 0xFF2F6B43},
            {0xFF2C3440, 0xFF4A5A70},
            {0xFF40301C, 0xFF8A6A2C},
            {0xFF38202F, 0xFF703F5E},
    };

    /** Most cover requests that may wait at once. A fling through a long list makes more; the oldest go. */
    private static final int MAX_WAITING = 96;
    /** Most "no cover found" answers remembered between two reloads of the song list. */
    private static final int MAX_MISSING = 4096;
    /** A cover picture inside a song file larger than this is not loaded (older Android reads it whole). */
    private static final int MAX_EMBEDDED_BYTES = 12 * 1024 * 1024;

    /**
     * Runs the most recently submitted task first, and keeps at most {@link #MAX_WAITING} waiting:
     * when it is full the oldest request (the row scrolled away longest ago) is dropped.
     */
    private static final class LifoQueue extends LinkedBlockingDeque<Runnable> {
        @Override
        public boolean offer(Runnable r) {
            while (size() >= MAX_WAITING) {
                if (pollLast() == null) break;
                Diag.count(Diag.ART_SKIPPED);
            }
            return offerFirst(r);
        }
    }

    // Holds the application context only, so it cannot leak an Activity.
    @SuppressLint("StaticFieldLeak")
    private static ArtLoader instance;

    static synchronized ArtLoader get(Context c) {
        if (instance == null) instance = new ArtLoader(c.getApplicationContext());
        return instance;
    }

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> cache;
    private final Set<String> missing = Collections.newSetFromMap(Collections.synchronizedMap(
            new java.util.LinkedHashMap<String, Boolean>(64, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > MAX_MISSING;
                }
            }));
    private final ThreadPoolExecutor pool;
    /** The cover of the playing song has its own lane, so a fast scroll can never delay or drop it. */
    private final ThreadPoolExecutor nowPlayingPool;
    private final Drawable.ConstantState[] placeholders = new Drawable.ConstantState[PALETTE.length];

    private ArtLoader(Context app) {
        this.app = app;
        int kb = (int) (Runtime.getRuntime().maxMemory() / 1024 / 12);
        cache = new LruCache<String, Bitmap>(Math.max(4096, kb)) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };
        pool = new ThreadPoolExecutor(2, 2, 20, TimeUnit.SECONDS, new LifoQueue());
        pool.allowCoreThreadTimeOut(true);
        nowPlayingPool = new ThreadPoolExecutor(1, 1, 20, TimeUnit.SECONDS, new LinkedBlockingDeque<Runnable>());
        nowPlayingPool.allowCoreThreadTimeOut(true);
    }

    void forgetMisses() {
        missing.clear();
    }

    /** Shows a placeholder at once, then the real art when it has been decoded. */
    void load(final Track t, final ImageView view, final int px) {
        final String key = t.artKey() + "@" + px;
        view.setTag(R.id.tag_art, key);
        Bitmap hit = cache.get(key);
        if (hit != null) {
            view.setImageBitmap(hit);
            return;
        }
        view.setImageDrawable(placeholder(t.artKey()));
        if (missing.contains(key)) return;
        pool.execute(() -> {
            if (!key.equals(view.getTag(R.id.tag_art))) return;
            final Bitmap b = decode(t, px);
            if (b == null) {
                missing.add(key);
                return;
            }
            cache.put(key, b);
            main.post(() -> {
                if (key.equals(view.getTag(R.id.tag_art))) view.setImageBitmap(b);
            });
        });
    }

    void loadAsync(final Track t, final int px, final Callback cb) {
        loadAsync(t, px, cb, null);
    }

    /** As above; {@code wanted} says whether the answer is still needed once it is this request's turn. */
    void loadAsync(final Track t, final int px, final Callback cb, final BooleanSupplier wanted) {
        final String key = t.artKey() + "@" + px;
        Bitmap hit = cache.get(key);
        if (hit != null) {
            cb.onArt(hit);
            return;
        }
        if (missing.contains(key)) {
            cb.onArt(null);
            return;
        }
        nowPlayingPool.execute(() -> {
            if (wanted != null && !wanted.getAsBoolean()) return;   // the song changed while this waited
            final Bitmap b = decode(t, px);
            if (b != null) cache.put(key, b);
            else missing.add(key);
            main.post(() -> cb.onArt(b));
        });
    }

    /** The two colours a cover without artwork is drawn with; also tints the glass backdrop. */
    static int[] paletteFor(String key) {
        return PALETTE[(key.hashCode() & 0x7fffffff) % PALETTE.length];
    }

    Drawable placeholder(String key) {
        int i = (key.hashCode() & 0x7fffffff) % PALETTE.length;
        Drawable.ConstantState cs = placeholders[i];
        if (cs != null) return cs.newDrawable();
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, PALETTE[i]);
        Drawable note = app.getDrawable(R.drawable.ic_music_note).mutate();
        note.setTint(0x73FFFFFF);
        LayerDrawable layers = new LayerDrawable(new Drawable[]{g, new InsetDrawable(note, 0.3f)});
        placeholders[i] = layers.getConstantState();
        return layers;
    }

    private Bitmap decode(Track t, int px) {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                return app.getContentResolver().loadThumbnail(t.uri(), new Size(px, px), null);
            } catch (Exception e) {
                return null;
            }
        }
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            if (t.dir.isEmpty()) r.setDataSource(app, t.uri());
            else r.setDataSource(t.path());
            byte[] pic = r.getEmbeddedPicture();   // there is no way to read it in pieces: it comes whole
            if (pic == null) return null;
            if (pic.length > MAX_EMBEDDED_BYTES) {
                Diag.count(Diag.ART_TOO_BIG);
                return null;
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(pic, 0, pic.length, o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= px && o.outHeight / (sample * 2) >= px) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(pic, 0, pic.length, o);
        } catch (Exception | OutOfMemoryError e) {
            if (e instanceof OutOfMemoryError) Diag.count(Diag.ART_TOO_BIG);
            return null;
        } finally {
            try {
                r.release();
            } catch (Exception ignored) {
                // nothing left to free
            }
        }
    }
}
