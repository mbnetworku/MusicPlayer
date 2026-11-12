package com.musicplayer.lite;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.SystemClock;

import java.util.function.LongSupplier;

/**
 * The sound the player is making right now, as bar levels, for the moving bars. It listens to the
 * player's own audio session (never the microphone) through Android's Visualizer effect, which
 * Android only allows with the permission it calls RECORD_AUDIO. Without that permission, or on a
 * device that refuses, the bars simply keep their own motion.
 *
 * <p>Every bar view that is on screen and playing {@link #acquire}s this; the listening starts with
 * the first one and stops with the last, so a paused or hidden player costs nothing.
 * Used from the main thread only.
 */
final class SoundBars {
    /** How many levels there are, from deep bass to treble. */
    static final int BANDS = 48;
    /** The bars follow the sound while its last frame is newer than this. */
    static final long FRESH_MS = 500;

    /** What the sound arrives through. */
    interface Frames {
        void onFft(byte[] fft, int rateMilliHz);
    }

    /** A running listener. */
    interface Capture {
        void close();
    }

    /** Something that can listen to an audio session. {@link VisualizerSource} is the real one. */
    interface Source {
        /** Starts listening. Throws a RuntimeException when the device refuses. */
        Capture open(int audioSession, Frames frames);
    }

    // Holds the application context only, so it cannot leak an Activity.
    @SuppressLint("StaticFieldLeak")
    private static SoundBars instance;

    static synchronized SoundBars get(Context c) {
        if (instance == null) {
            instance = new SoundBars(c.getApplicationContext() != null ? c.getApplicationContext() : c,
                    new VisualizerSource(), SystemClock::uptimeMillis);
        }
        return instance;
    }

    /** Whether the permission is there and the user has not switched the feature off. */
    static boolean allowed(Context c) {
        return Prefs.get(c).barsFollow() && hasPermission(c);
    }

    static boolean hasPermission(Context c) {
        return Permissions.hasRecord(c);
    }

    private final Context app;
    private final Source source;
    private final LongSupplier clock;
    private final SpectrumAnalyzer analyzer = new SpectrumAnalyzer(BANDS);
    private final Frames frames = this::onFrame;

    private int session;
    private int users;
    private Capture capture;
    private long lastFrame = -FRESH_MS;
    /** The device said no for this session; asking again would only fail again. */
    private boolean refused;

    SoundBars(Context app, Source source, LongSupplier clock) {
        this.app = app;
        this.source = source;
        this.clock = clock;
    }

    /** The player's audio session (0 when there is none). Called by the playback service. */
    void setSession(int id) {
        if (id == session) return;
        session = id;
        refused = false;
        stopCapture();
        startIfWanted();
    }

    /** A bar view is on screen and playing: it wants the sound. */
    void acquire() {
        users++;
        startIfWanted();
    }

    void release() {
        if (users > 0) users--;
        if (users == 0) stopCapture();
    }

    /** The permission or the setting changed: look again. */
    void refresh() {
        refused = false;
        if (capture != null && !allowed(app)) stopCapture();
        startIfWanted();
    }

    /** True while the bars can follow the sound: listening, and a frame came in a moment ago. */
    boolean following() {
        return capture != null && clock.getAsLong() - lastFrame < FRESH_MS;
    }

    /** True while the sound is being listened to (even if no frame has come yet). */
    boolean listening() {
        return capture != null;
    }

    /** The levels, 0 to 1, bass first. The live array: read it, do not change it. */
    float[] levels() {
        return analyzer.levels();
    }

    /**
     * The level for bar {@code i} of {@code n}. With fewer bars than levels each bar takes the
     * average of its share of them; with more it reads between two.
     */
    float level(int i, int n) {
        return resample(analyzer.levels(), i, n);
    }

    static float resample(float[] levels, int i, int n) {
        final int b = levels.length;
        if (n <= 0 || b == 0) return 0f;
        if (n >= b) {
            final float position = n == 1 ? 0f : (b - 1) * (i / (float) (n - 1));
            final int low = (int) Math.floor(position);
            final int high = Math.min(b - 1, low + 1);
            return levels[low] + (levels[high] - levels[low]) * (position - low);
        }
        final int from = (int) Math.floor(i * (double) b / n);
        final int to = Math.max(from + 1, (int) Math.floor((i + 1) * (double) b / n));
        float sum = 0f;
        for (int k = from; k < to && k < b; k++) sum += levels[k];
        return sum / (to - from);
    }

    private void onFrame(byte[] fft, int rateMilliHz) {
        analyzer.feed(fft, rateMilliHz);
        lastFrame = clock.getAsLong();
    }

    /** For the tests: use this one instead of the real one. */
    static synchronized void replaceForTests(SoundBars replacement) {
        instance = replacement;
    }

    private void startIfWanted() {
        if (users == 0 || capture != null || refused || session <= 0 || !allowed(app)) return;
        try {
            capture = source.open(session, frames);
            lastFrame = -FRESH_MS;
        } catch (RuntimeException e) {
            // A device that cannot do this (many head units cannot): the bars keep their own motion.
            refused = true;
        }
    }

    private void stopCapture() {
        if (capture == null) return;
        try {
            capture.close();
        } catch (RuntimeException ignored) {
            // Already gone.
        }
        capture = null;
        analyzer.reset();
    }
}
