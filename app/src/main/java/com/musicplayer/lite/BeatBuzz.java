package com.musicplayer.lite;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.VibrationEffect;
import android.os.Vibrator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;

/**
 * The phone buzzes with the music, if the user switched it on in Settings: a buzz on the strong low
 * notes of the song (the drums and the bass), stronger for heavier music and none for quiet parts.
 * The bass is measured from the song's own file (see {@link EnvelopeDecoder}), not from the
 * microphone. Used from the main thread.
 */
final class BeatBuzz {
    private static final long STEP_MS = BassEnvelope.WINDOW_MS;
    /** The envelopes of the last few songs, so that a song that comes back needs no new reading. */
    private static final int KEEP = 6;

    private final Context context;
    private final IntSupplier positionMs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::step;
    private final Map<Long, float[]> cache = new LinkedHashMap<Long, float[]>(8, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Entry<Long, float[]> eldest) {
            return size() > KEEP;
        }
    };
    private ExecutorService worker;
    private AtomicBoolean cancel = new AtomicBoolean();

    private boolean playing;
    private boolean ticking;
    private long trackId = -1;
    private float[] envelope;
    private int buzzes;
    private int lastStrength;

    BeatBuzz(Context context, IntSupplier positionMs) {
        this.context = context;
        this.positionMs = positionMs;
    }

    /** The song that is current now. The bass of a new song is measured in the background. */
    void setTrack(Track track) {
        final long id = track == null ? -1 : track.id;
        if (id == trackId) return;
        trackId = id;
        envelope = null;
        cancel.set(true);
        cancel = new AtomicBoolean();
        if (track == null || !Prefs.get(context).vibratePlaying()) return;
        final float[] known = cache.get(id);
        if (known != null) {
            envelope = known;
            return;
        }
        final AtomicBoolean mine = cancel;
        if (worker == null) {
            worker = Executors.newSingleThreadExecutor(r -> {
                final Thread t = new Thread(() -> {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                    r.run();
                }, "bass-reader");
                t.setDaemon(true);
                return t;
            });
        }
        worker.execute(() -> {
            final float[] result = EnvelopeDecoder.decode(context, track, mine);
            handler.post(() -> {
                if (mine.get() || result == null) return;
                cache.put(id, result);
                if (id == trackId) {
                    envelope = result;
                    startTicking();
                }
            });
        });
    }

    /** Tell it whether music is playing now. */
    void setPlaying(boolean playing) {
        this.playing = playing;
        if (playing) startTicking();
        else stopTicking();
    }

    void stop() {
        stopTicking();
        cancel.set(true);
        if (worker != null) {
            worker.shutdownNow();
            worker = null;
        }
    }

    /** How many buzzes were made (for the tests). */
    int buzzes() {
        return buzzes;
    }

    /** For the tests: use this envelope for the current song. */
    void useEnvelope(float[] values) {
        envelope = values;
        startTicking();
    }

    private void startTicking() {
        if (ticking || !playing || envelope == null || !Prefs.get(context).vibratePlaying()) return;
        ticking = true;
        handler.postDelayed(tick, STEP_MS);
    }

    private void stopTicking() {
        ticking = false;
        handler.removeCallbacks(tick);
    }

    private void step() {
        if (!ticking) return;
        if (!playing || envelope == null || !Prefs.get(context).vibratePlaying()) {
            stopTicking();
            return;
        }
        final int strength = BassEnvelope.strengthAt(envelope, positionMs.getAsInt() / (int) STEP_MS);
        if (strength > 0) buzz(strength);
        handler.postDelayed(tick, STEP_MS);
    }

    private void buzz(int strength) {
        buzzes++;
        lastStrength = strength;
        final Vibrator v = context.getSystemService(Vibrator.class);
        if (v == null || !v.hasVibrator()) return;
        final int amplitude = v.hasAmplitudeControl() ? strength : VibrationEffect.DEFAULT_AMPLITUDE;
        v.vibrate(VibrationEffect.createOneShot(strength > 180 ? 55 : 35, amplitude));
    }

    int lastStrength() {
        return lastStrength;
    }
}
