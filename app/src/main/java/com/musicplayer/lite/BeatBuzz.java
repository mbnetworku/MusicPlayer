package com.musicplayer.lite;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * A soft pulse, about twice a second, while music plays, if the user switched it on in Settings.
 * It is a steady pulse, not matched to the beat of the song: matching it would need to read the
 * sound, which the app deliberately does not do. Used from the main thread.
 */
final class BeatBuzz {
    static final long PERIOD_MS = 500;
    private static final long PULSE_MS = 28;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::pulse;
    private boolean playing;
    private boolean ticking;
    private int pulses;

    BeatBuzz(Context context) {
        this.context = context;
    }

    /** Tell it whether music is playing now. */
    void setPlaying(boolean playing) {
        this.playing = playing;
        if (playing && !ticking && Prefs.get(context).vibratePlaying()) {
            ticking = true;
            handler.postDelayed(tick, PERIOD_MS);
        } else if (!playing) {
            stop();
        }
    }

    void stop() {
        ticking = false;
        handler.removeCallbacks(tick);
    }

    /** How many pulses were made (for the tests). */
    int pulses() {
        return pulses;
    }

    private void pulse() {
        if (!ticking) return;
        if (!playing || !Prefs.get(context).vibratePlaying()) {
            stop();
            return;
        }
        final Vibrator v = context.getSystemService(Vibrator.class);
        if (v != null && v.hasVibrator()) {
            v.vibrate(VibrationEffect.createOneShot(PULSE_MS, 70));
        }
        pulses++;
        handler.postDelayed(tick, PERIOD_MS);
    }
}
