package com.musicplayer.lite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Random;

/** Made-up sound in, bar levels out: no device needed. */
public class SpectrumAnalyzerTest {
    private static final int SIZE = 1024;              // the Visualizer's capture size: 512 bins
    private static final int RATE = 44_100_000;        // 44.1 kHz, as the Visualizer reports it (milli-hertz)
    private static final float BIN_HZ = 44_100f / SIZE;

    /** A frame with one pure tone at bin {@code bin}, {@code amplitude} loud (the real part only). */
    private static byte[] tone(int bin, int amplitude) {
        byte[] fft = new byte[SIZE];
        fft[2 * bin] = (byte) amplitude;
        return fft;
    }

    private static int loudest(float[] levels) {
        int best = 0;
        for (int i = 1; i < levels.length; i++) if (levels[i] > levels[best]) best = i;
        return best;
    }

    @Test
    public void silenceLeavesEveryBarAtZero() {
        SpectrumAnalyzer a = new SpectrumAnalyzer(48);
        for (int i = 0; i < 10; i++) a.feed(new byte[SIZE], RATE);
        for (float level : a.levels()) assertEquals(0f, level, 0f);
    }

    @Test
    public void aToneLightsTheBarOfItsOwnPitchAndLittleElse() {
        SpectrumAnalyzer a = new SpectrumAnalyzer(48);
        a.feed(tone(23, 100), RATE);                    // 23 x 43 Hz = about 990 Hz
        int expected = a.bandOf(23 * BIN_HZ, RATE);
        assertTrue("the bar of 990 Hz is lit", a.levels()[expected] > 0.95f);
        assertEquals(expected, loudest(a.levels()), 1);
        for (int i = 0; i < 48; i++) {
            if (Math.abs(i - expected) > 3) assertTrue("bar " + i + " is " + a.levels()[i], a.levels()[i] < 0.05f);
        }
    }

    @Test
    public void bassIsOnTheLeftAndTrebleOnTheRight() {
        SpectrumAnalyzer bass = new SpectrumAnalyzer(48);
        bass.feed(tone(3, 100), RATE);                  // about 130 Hz
        SpectrumAnalyzer treble = new SpectrumAnalyzer(48);
        treble.feed(tone(186, 100), RATE);              // about 8 kHz
        int low = loudest(bass.levels());
        int high = loudest(treble.levels());
        assertTrue("bass in the left quarter: " + low, low < 12);
        assertTrue("treble in the right quarter: " + high, high > 36);
    }

    @Test
    public void higherPitchesNeverGoToABarFurtherLeft() {
        SpectrumAnalyzer a = new SpectrumAnalyzer(48);
        int last = -1;
        for (float hz = 50; hz < 15_000; hz *= 1.2f) {
            int band = a.bandOf(hz, RATE);
            assertTrue(hz + " Hz in bar " + band + " after " + last, band >= last);
            last = band;
        }
        assertEquals("below the lowest sound shown", -1, a.bandOf(10f, RATE));
        assertEquals("above the highest", 47, a.bandOf(21_000f, RATE));
        assertEquals(0, a.bandOf(SpectrumAnalyzer.LOW_HZ + 1f, RATE));
    }

    @Test
    public void theSameToneLooksTheSameAtAnyVolume() {
        SpectrumAnalyzer loud = new SpectrumAnalyzer(48);
        loud.feed(tone(23, 120), RATE);
        SpectrumAnalyzer quiet = new SpectrumAnalyzer(48);
        quiet.feed(tone(23, 30), RATE);
        int band = loud.bandOf(23 * BIN_HZ, RATE);
        assertEquals(loud.levels()[band], quiet.levels()[band], 0.02f);
    }

    @Test
    public void aQuietPassageAfterALoudOneIsShorterButNotGone() {
        SpectrumAnalyzer a = new SpectrumAnalyzer(48);
        a.feed(tone(23, 100), RATE);
        int band = a.bandOf(23 * BIN_HZ, RATE);
        for (int i = 0; i < 30; i++) a.feed(tone(23, 25), RATE);
        float level = a.levels()[band];
        assertTrue("quieter than the loud part: " + level, level < 0.6f);
        assertTrue("but still there: " + level, level > 0.15f);
    }

    @Test
    public void levelsAlwaysStayBetweenZeroAndOne() {
        Random random = new Random(7);
        SpectrumAnalyzer a = new SpectrumAnalyzer(48);
        for (int frame = 0; frame < 200; frame++) {
            byte[] fft = new byte[SIZE];
            random.nextBytes(fft);
            a.feed(fft, frame % 2 == 0 ? RATE : 48_000_000);
            for (float level : a.levels()) assertTrue("level " + level, level >= 0f && level <= 1f);
        }
    }

    @Test
    public void barsFallBackStepByStepWhenTheSoundStops() {
        SpectrumAnalyzer a = new SpectrumAnalyzer(48);
        a.feed(tone(23, 100), RATE);
        int band = a.bandOf(23 * BIN_HZ, RATE);
        float level = a.levels()[band];
        assertEquals(1f, level, 0.01f);
        a.feed(new byte[SIZE], RATE);
        assertEquals(level * SpectrumAnalyzer.FALL, a.levels()[band], 0.001f);
        a.feed(new byte[SIZE], RATE);
        assertEquals(level * SpectrumAnalyzer.FALL * SpectrumAnalyzer.FALL, a.levels()[band], 0.001f);
        for (int i = 0; i < 40; i++) a.feed(new byte[SIZE], RATE);
        assertTrue("gone after a couple of seconds", a.levels()[band] < 0.01f);
    }

    @Test
    public void fallingWithoutAFrameDoesTheSame() {
        SpectrumAnalyzer a = new SpectrumAnalyzer(48);
        a.feed(tone(23, 100), RATE);
        int band = a.bandOf(23 * BIN_HZ, RATE);
        float before = a.levels()[band];
        a.fall();
        assertEquals(before * SpectrumAnalyzer.FALL, a.levels()[band], 0.001f);
    }

    @Test
    public void theSamplingRateDecidesWhichBarAPitchBelongsTo() {
        SpectrumAnalyzer fast = new SpectrumAnalyzer(48);
        fast.feed(tone(10, 100), 48_000_000);           // bin 10 at 48 kHz is about 469 Hz
        SpectrumAnalyzer slow = new SpectrumAnalyzer(48);
        slow.feed(tone(10, 100), 22_050_000);           // bin 10 at 22.05 kHz is about 215 Hz
        assertTrue(loudest(slow.levels()) < loudest(fast.levels()));
    }

    @Test
    public void lowestBarsNarrowerThanOneBinStillReadTheirPlace() {
        SpectrumAnalyzer a = new SpectrumAnalyzer(48);
        a.feed(tone(1, 100), RATE);                     // 43 Hz, right at the left end
        assertTrue("a bar near the left end is lit", a.levels()[0] + a.levels()[1] + a.levels()[2] > 0.5f);
        assertTrue("the right end is not", a.levels()[47] < 0.05f);
    }

    @Test
    public void silenceAfterLoudMusicDoesNotLightTheBars() {
        SpectrumAnalyzer a = new SpectrumAnalyzer(48);
        a.feed(tone(23, 120), RATE);
        for (int i = 0; i < 100; i++) a.feed(new byte[SIZE], RATE);
        for (float level : a.levels()) assertTrue(level < 0.001f);
    }

    @Test
    public void unusableFramesAreIgnoredWithoutFuss() {
        SpectrumAnalyzer a = new SpectrumAnalyzer(48);
        a.feed(tone(23, 100), RATE);
        int band = a.bandOf(23 * BIN_HZ, RATE);
        float before = a.levels()[band];
        a.feed(null, RATE);
        a.feed(new byte[2], RATE);
        a.feed(tone(23, 100), 0);
        a.feed(tone(23, 100), -5);
        assertTrue("each one only let the bars fall", a.levels()[band] < before);
    }

    @Test
    public void resetPutsEveryBarBack() {
        SpectrumAnalyzer a = new SpectrumAnalyzer(48);
        a.feed(tone(23, 100), RATE);
        a.reset();
        for (float level : a.levels()) assertEquals(0f, level, 0f);
    }

    @Test
    public void anyNumberOfBarsWorks() {
        for (int bands : new int[]{1, 2, 7, 48, 96}) {
            SpectrumAnalyzer a = new SpectrumAnalyzer(bands);
            a.feed(tone(23, 100), RATE);
            assertEquals(bands, a.levels().length);
            assertEquals(bands, a.bands());
        }
        assertEquals("never fewer than one", 1, new SpectrumAnalyzer(0).bands());
    }
}
