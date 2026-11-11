package com.musicplayer.lite;

/**
 * Equalizer presets. Each preset is a gain curve at five reference frequencies;
 * it is interpolated (on a log-frequency axis) onto whatever bands the device
 * equalizer offers, so presets work on 5-band and 10-band hardware alike.
 */
final class EqPresets {
    private EqPresets() {}

    static final int[] NAMES = {
            R.string.preset_flat,
            R.string.preset_bass,
            R.string.preset_treble,
            R.string.preset_vocal,
            R.string.preset_rock,
            R.string.preset_pop,
            R.string.preset_jazz,
            R.string.preset_classical,
            R.string.preset_dance,
            R.string.preset_hiphop,
            R.string.preset_car,
    };

    static final int COUNT = NAMES.length;

    private static final double[] REF_HZ = {60, 230, 910, 3600, 14000};

    /** dB at 60 Hz, 230 Hz, 910 Hz, 3.6 kHz and 14 kHz. */
    private static final float[][] GAINS = {
            {0, 0, 0, 0, 0},     // flat
            {6, 4, 1, 0, 0},     // bass boost
            {0, 0, 0, 4, 6},     // treble boost
            {-2, 0, 3, 4, 1},    // vocal
            {5, 3, -1, 3, 5},    // rock
            {-1, 2, 4, 2, -1},   // pop
            {3, 2, -1, 1, 3},    // jazz
            {3, 2, -1, 2, 3},    // classical
            {6, 3, 0, 2, 4},     // dance
            {5, 4, 0, 1, 3},     // hip-hop
            {5, 2, -1, 2, 5},    // car: lifts bass and treble over road noise
    };

    /**
     * @param centerMilliHz band centres as reported by the platform equalizer
     * @return one level in millibels per band, whole-dB steps, clamped to the range
     */
    static int[] levelsFor(int preset, int[] centerMilliHz, int minMb, int maxMb) {
        int[] out = new int[centerMilliHz.length];
        if (preset < 0 || preset >= GAINS.length) return out;
        for (int b = 0; b < out.length; b++) {
            double hz = Math.max(20.0, centerMilliHz[b] / 1000.0);
            long db = Math.round(interpolate(hz, GAINS[preset]));
            out[b] = Fmt.clamp((int) (db * 100), minMb, maxMb);
        }
        return out;
    }

    private static double interpolate(double hz, float[] g) {
        if (hz <= REF_HZ[0]) return g[0];
        int last = REF_HZ.length - 1;
        if (hz >= REF_HZ[last]) return g[last];
        for (int i = 0; i < last; i++) {
            if (hz <= REF_HZ[i + 1]) {
                double t = (Math.log(hz) - Math.log(REF_HZ[i])) / (Math.log(REF_HZ[i + 1]) - Math.log(REF_HZ[i]));
                return g[i] + t * (g[i + 1] - g[i]);
            }
        }
        return 0;
    }
}
