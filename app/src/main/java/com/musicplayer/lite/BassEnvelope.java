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
    /** Notes below this pitch count as bass, in Hz. Two filters in a row, so voices and guitars stay out. */
    static final float CUTOFF_HZ = 200f;
    /** The bass loudness that counts as fully heavy, at least (about what ordinary music has). */
    static final float MIN_REFERENCE = 0.08f;

    private final int sampleRate;
    private final int channels;
    private final int windowFrames;
    private final float alpha;
    private final float[] low;
    private final float[] lower;
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
        this.lower = new float[this.channels];
    }

    /** Sound as 16 bit numbers, the way the player's decoder gives it: the channels take turns. */
    void feed(short[] pcm, int length) {
        final int frames = length / channels;
        for (int f = 0; f < frames; f++) {
            double energy = 0;
            for (int c = 0; c < channels; c++) {
                final float x = pcm[f * channels + c] / 32768f;
                low[c] += alpha * (x - low[c]);             // keeps the low notes ...
                lower[c] += alpha * (low[c] - lower[c]);    // ... and a second time, so the mids are cut harder
                energy += lower[c] * lower[c];
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

    /** Bass lighter than this (0 to 1, compared with the heaviest moments of the song) gives no buzz. */
    static final float FLOOR = 0.12f;
    /** From this much bass up, the buzz is the strongest there is and has no gaps. */
    static final float HEAVY = 0.8f;
    /** The weakest and the strongest buzz, as Android amplitudes (1 to 255). */
    static final int WEAKEST = 30;
    static final int STRONGEST = 255;
    /** The strongest a buzz can be below {@link #HEAVY}: the last step up is for heavy bass only. */
    private static final int BELOW_HEAVY = 230;
    /** How much of its strength a steady bass keeps, when it is not a new beat. */
    private static final float STEADY = 0.55f;
    /** The buzz starts this early, because a phone's motor needs a moment to get going. */
    static final int LEAD_MS = 40;

    /**
     * How strong the buzz at window {@code i} should be, from 0 (none) to 255. It follows the bass:
     * a light bass gives a gentle buzz, a medium bass a firmer one, and heavy bass the full buzz.
     * A new beat (the bass has just risen) gets the full strength for its level; a bass that only
     * stays where it was gets a softer buzz, so that a steady bass line does not drone.
     */
    static int strengthAt(float[] env, int i) {
        if (env == null || i < 0 || i >= env.length) return 0;
        final float e = env[i];
        if (e < FLOOR) return 0;
        if (e >= HEAVY) return STRONGEST;
        final float level = (e - FLOOR) / (HEAVY - FLOOR);
        float strength = WEAKEST + (BELOW_HEAVY - WEAKEST) * level;
        float before = 0;
        int n = 0;
        for (int k = Math.max(0, i - 6); k < i; k++) {
            before += env[k];
            n++;
        }
        final float average = n == 0 ? 0f : before / n;
        final boolean beat = e > average * 1.2f + 0.03f;
        if (!beat) strength *= STEADY;
        return Math.round(Math.max(0f, Math.min(STRONGEST, strength)));
    }

    /**
     * How long one buzz lasts: short for a gentle one, and longer than a window for the strongest,
     * so that heavy bass runs on without a gap, "complete".
     */
    static int durationMs(int strength) {
        if (strength >= 230) return WINDOW_MS + 30;
        if (strength >= 150) return 55;
        if (strength >= 80) return 40;
        return 25;
    }
}
