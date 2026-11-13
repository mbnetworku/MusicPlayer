package com.musicplayer.lite;

import java.util.Arrays;

/**
 * How heavy the bass of a song is, moment by moment. The song's sound is read from its own file
 * (never from the microphone), the low notes are kept, and the loudness of those low notes is
 * measured for every 50 milliseconds. The result goes from 0 (nothing) to 1 (the heaviest
 * moment of that song). Plain arithmetic: no Android classes, so it is tested with made-up sound.
 */
final class BassEnvelope {
    /** One value of the envelope covers this much of the song. */
    static final int WINDOW_MS = 50;
    /** Notes below this pitch count as bass, in Hz. */
    static final float CUTOFF_HZ = 150f;
    /** The bass loudness that counts as fully heavy, at least (about what ordinary music has). */
    static final float MIN_REFERENCE = 0.08f;

    private final int sampleRate;
    private final int channels;
    private final int windowFrames;
    private final float alpha;
    private final float[] low;
    private float[] values = new float[256];
    private int count;
    private double sum;
    private int framesInWindow;

    BassEnvelope(int sampleRate, int channels) {
        this.sampleRate = Math.max(8000, sampleRate);
        this.channels = Math.max(1, channels);
        this.windowFrames = Math.max(1, this.sampleRate * WINDOW_MS / 1000);
        this.alpha = (float) (1.0 - Math.exp(-2.0 * Math.PI * CUTOFF_HZ / this.sampleRate));
        this.low = new float[this.channels];
    }

    /** Sound as 16 bit numbers, the way the player's decoder gives it: the channels take turns. */
    void feed(short[] pcm, int length) {
        final int frames = length / channels;
        for (int f = 0; f < frames; f++) {
            double energy = 0;
            for (int c = 0; c < channels; c++) {
                final float x = pcm[f * channels + c] / 32768f;
                low[c] += alpha * (x - low[c]);   // keeps only the low notes
                energy += low[c] * low[c];
            }
            sum += energy / channels;
            if (++framesInWindow >= windowFrames) closeWindow();
        }
    }

    private void closeWindow() {
        if (count == values.length) values = Arrays.copyOf(values, count * 2);
        values[count++] = (float) Math.sqrt(sum / framesInWindow);
        sum = 0;
        framesInWindow = 0;
    }

    /** The finished envelope, scaled so that the heavy moments of this song are near 1. Empty for no sound. */
    float[] finish() {
        if (framesInWindow > windowFrames / 2) closeWindow();
        final float[] out = Arrays.copyOf(values, count);
        if (count == 0) return out;
        final float[] sorted = out.clone();
        Arrays.sort(sorted);
        // The heavy moments of the song are near 1, but never from less than a real bass: a song with
        // hardly any low notes stays weak instead of being made to feel heavy.
        final float reference = Math.max(MIN_REFERENCE, sorted[Math.min(count - 1, (int) (count * 0.95f))]);
        for (int i = 0; i < count; i++) out[i] = Math.min(1f, out[i] / reference);
        return out;
    }

    /**
     * How strong the buzz at window {@code i} should be, from 0 (none) to 255. A buzz is made when
     * the bass is strong and has just risen, like a drum beat, and it is stronger for heavier bass.
     * Heavy sections buzz on every window.
     */
    static int strengthAt(float[] env, int i) {
        if (env == null || i < 0 || i >= env.length) return 0;
        final float e = env[i];
        if (e < 0.22f) return 0;
        float before = 0;
        int n = 0;
        for (int k = Math.max(0, i - 6); k < i; k++) {
            before += env[k];
            n++;
        }
        final float average = n == 0 ? 0f : before / n;
        final boolean hit = e > average * 1.25f + 0.05f;
        final boolean heavy = e > 0.85f;
        if (!hit && !heavy) return 0;
        return Math.round(Math.min(255f, 40f + 215f * e * e));
    }
}
