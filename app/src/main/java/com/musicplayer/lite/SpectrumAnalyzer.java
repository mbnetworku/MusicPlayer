package com.musicplayer.lite;

/**
 * Turns the frames of Android's Visualizer (an FFT of the sound the player is making) into a row of
 * bar levels, from deep bass on the left to treble on the right. Plain arithmetic: no Android
 * classes, so it can be tested with made-up sound.
 *
 * <p>The levels go from 0 to 1. They are scaled against the loudest recent sound, so they look the
 * same at any volume, and a bar that was high falls back gradually instead of flickering.
 */
final class SpectrumAnalyzer {
    /** The lowest and the highest sound the bars show, in Hz. */
    static final float LOW_HZ = 40f;
    static final float HIGH_HZ = 16000f;
    /** What a bar keeps of its height from one frame to the next when the sound drops. */
    static final float FALL = 0.82f;
    /** Anything this faint is noise, not music (the frames are 8 bit). */
    private static final float GATE = 3f;
    /** The loudest recent sound is never taken to be quieter than this, so silence does not light the bars. */
    private static final float PEAK_FLOOR = 14f;
    /** How slowly the "loudest recent sound" is forgotten, per frame. */
    private static final float PEAK_FALL = 0.995f;
    /** The treble end gets this much more gain than the bass end (music has much less energy up there). */
    private static final float TILT = 0.8f;

    private final int bands;
    private final float[] levels;
    private final float[] edges;
    private final float[] raw;
    private float[] mags = new float[0];
    private float peak = PEAK_FLOOR;
    private int edgesRate;

    SpectrumAnalyzer(int bands) {
        this.bands = Math.max(1, bands);
        this.levels = new float[this.bands];
        this.raw = new float[this.bands];
        this.edges = new float[this.bands + 1];
    }

    int bands() {
        return bands;
    }

    /** The current levels, 0 to 1. This is the live array: read it, do not keep or change it. */
    float[] levels() {
        return levels;
    }

    void reset() {
        java.util.Arrays.fill(levels, 0f);
        peak = PEAK_FLOOR;
    }

    /** No new sound: every bar falls a step. */
    void fall() {
        for (int i = 0; i < bands; i++) levels[i] *= FALL;
    }

    /**
     * One frame from the Visualizer: {@code fft[0]} is the real part of the lowest bin, {@code fft[1]}
     * the real part of the highest, then a real and an imaginary part for each bin in between.
     * {@code rateMilliHz} is the sampling rate in thousandths of a hertz, as the Visualizer reports it.
     */
    void feed(byte[] fft, int rateMilliHz) {
        if (fft == null || fft.length < 8 || rateMilliHz <= 0) {
            fall();
            return;
        }
        final int n = fft.length / 2;
        if (mags.length != n) mags = new float[n];
        mags[0] = Math.abs(fft[0]);
        for (int k = 1; k < n; k++) {
            final float re = fft[2 * k];
            final float im = fft[2 * k + 1];
            mags[k] = (float) Math.sqrt(re * re + im * im);
        }
        final float rateHz = rateMilliHz / 1000f;
        final float binHz = rateHz / fft.length;
        buildEdges(rateMilliHz);

        float loudest = 0f;
        for (int j = 0; j < bands; j++) {
            final float lo = Math.max(1f, edges[j] / binHz);
            final float hi = Math.max(lo, edges[j + 1] / binHz);
            float v;
            if (hi - lo < 1f) {
                v = between(mags, (lo + hi) / 2f);          // narrower than one bin: read between two
            } else {
                v = 0f;                                      // wider: the strongest bin inside
                final int last = Math.min(n - 1, (int) Math.floor(hi));
                for (int k = (int) Math.ceil(lo); k <= last; k++) v = Math.max(v, mags[k]);
            }
            raw[j] = v;
            loudest = Math.max(loudest, v);
        }
        peak = Math.max(Math.max(loudest, PEAK_FLOOR), peak * PEAK_FALL);

        for (int j = 0; j < bands; j++) {
            float x = Math.max(0f, raw[j] - GATE) / Math.max(1f, peak - GATE);
            x *= 1f + TILT * (bands == 1 ? 0f : j / (float) (bands - 1));
            final float level = (float) Math.pow(Math.min(1f, x), 0.75);
            levels[j] = Math.max(level, levels[j] * FALL);
        }
    }

    /** The band whose range holds {@code hz} (-1 below the lowest, the last band above the highest). */
    int bandOf(float hz, int rateMilliHz) {
        buildEdges(rateMilliHz);
        if (hz < edges[0]) return -1;
        for (int j = 0; j < bands; j++) {
            if (hz < edges[j + 1]) return j;
        }
        return bands - 1;
    }

    private void buildEdges(int rateMilliHz) {
        if (edgesRate == rateMilliHz) return;
        edgesRate = rateMilliHz;
        final float top = Math.min(HIGH_HZ, rateMilliHz / 1000f * 0.45f);
        final double ratio = Math.pow(top / LOW_HZ, 1.0 / bands);
        for (int j = 0; j <= bands; j++) edges[j] = (float) (LOW_HZ * Math.pow(ratio, j));
    }

    private static float between(float[] mags, float position) {
        final int i = Math.min(mags.length - 1, (int) position);
        final int next = Math.min(mags.length - 1, i + 1);
        final float f = position - i;
        return mags[i] * (1f - f) + mags[next] * f;
    }
}
