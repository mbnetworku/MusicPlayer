package com.musicplayer.lite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The bass measurement and the buzz strength, with made-up sound. */
public class BassEnvelopeTest {
    private static final int RATE = 44100;

    /** {@code seconds} of a sine wave with this pitch and loudness (0 to 1), mono. */
    private static short[] tone(double hz, double loudness, double seconds) {
        final short[] out = new short[(int) (RATE * seconds)];
        for (int i = 0; i < out.length; i++) {
            out[i] = (short) (Math.sin(2 * Math.PI * hz * i / RATE) * loudness * 32767);
        }
        return out;
    }

    private static float mean(float[] v, int from, int to) {
        float sum = 0;
        for (int i = from; i < to; i++) sum += v[i];
        return sum / (to - from);
    }

    @Test
    public void deepBassCountsAndHighNotesDoNot() {
        BassEnvelope bass = new BassEnvelope(RATE, 1);
        short[] low = tone(60, 0.8, 2);
        bass.feed(low, low.length);
        float[] a = bass.finish();
        assertEquals("twenty windows for each second", 40, a.length, 1);
        assertTrue("a deep note is strong: " + mean(a, 5, 35), mean(a, 5, 35) > 0.9f);

        BassEnvelope treble = new BassEnvelope(RATE, 1);
        short[] high = tone(5000, 0.8, 2);
        treble.feed(high, high.length);
        float[] unscaled = treble.finish();
        // Only a silent song is all zeros: a high note has almost no bass, so it is scaled up from nothing.
        // What matters is the buzz made from it: none.
        int buzzes = 0;
        for (int i = 0; i < unscaled.length; i++) if (BassEnvelope.strengthAt(unscaled, i) > 0) buzzes++;
        assertEquals("a high whine does not buzz the phone", 0, buzzes);
    }

    @Test
    public void heavyPartsAreStrongerThanQuietParts() {
        BassEnvelope e = new BassEnvelope(RATE, 1);
        short[] quiet = tone(60, 0.05, 1);
        short[] loud = tone(60, 0.9, 1);
        e.feed(quiet, quiet.length);
        e.feed(loud, loud.length);
        float[] env = e.finish();
        float quietPart = mean(env, 2, 18);
        float loudPart = mean(env, 22, 38);
        assertTrue("loud " + loudPart + " against quiet " + quietPart, loudPart > quietPart * 5);
        assertEquals("quiet parts give no buzz", 0, BassEnvelope.strengthAt(env, 10));
        assertTrue("heavy parts give a strong buzz", BassEnvelope.strengthAt(env, 30) > 200);
    }

    @Test
    public void aSilentSongGivesNothing() {
        BassEnvelope e = new BassEnvelope(RATE, 2);
        short[] silence = new short[RATE * 2];
        e.feed(silence, silence.length);
        float[] env = e.finish();
        for (float v : env) assertEquals(0f, v, 0f);
        assertEquals(0, BassEnvelope.strengthAt(env, 5));
    }

    @Test
    public void aDrumHitBuzzesAndTheQuietAroundItDoesNot() {
        float[] env = new float[40];
        java.util.Arrays.fill(env, 0.1f);
        env[20] = 0.9f;      // a kick drum
        env[21] = 0.7f;
        assertEquals(0, BassEnvelope.strengthAt(env, 10));
        assertTrue("the hit buzzes", BassEnvelope.strengthAt(env, 20) > 100);
        assertTrue("outside the song nothing buzzes", BassEnvelope.strengthAt(env, 400) == 0);
    }

    /** A song that is quiet, then has one moment of bass at this level (a new beat). */
    private static float[] beatAt(float level) {
        float[] env = new float[40];
        java.util.Arrays.fill(env, 0.02f);
        env[20] = level;
        return env;
    }

    @Test
    public void lightBassGivesAGentleBuzz() {
        int s = BassEnvelope.strengthAt(beatAt(0.2f), 20);
        assertTrue("a light bass is felt: " + s, s >= BassEnvelope.WEAKEST);
        assertTrue("but it is gentle: " + s, s < 90);
    }

    @Test
    public void mediumBassGivesAFirmerBuzz() {
        int s = BassEnvelope.strengthAt(beatAt(0.5f), 20);
        assertTrue("firmer than gentle: " + s, s > 100);
        assertTrue("not yet the full buzz: " + s, s < 200);
    }

    @Test
    public void heavyBassGivesTheFullBuzzWithoutGaps() {
        for (float level : new float[]{0.8f, 0.9f, 1f}) {
            int s = BassEnvelope.strengthAt(beatAt(level), 20);
            assertEquals("heavy bass is the strongest buzz", BassEnvelope.STRONGEST, s);
            assertTrue("and it lasts longer than a window, so the next buzz joins it",
                    BassEnvelope.durationMs(s) > BassEnvelope.WINDOW_MS);
        }
        float[] steadyHeavy = new float[40];
        java.util.Arrays.fill(steadyHeavy, 0.95f);
        for (int i = 0; i < steadyHeavy.length; i++) {
            assertEquals("every window of a heavy section buzzes fully", BassEnvelope.STRONGEST,
                    BassEnvelope.strengthAt(steadyHeavy, i));
        }
    }

    @Test
    public void aHeavierBassNeverBuzzesWeaker() {
        int last = 0;
        for (int step = 0; step <= 100; step++) {
            float level = step / 100f;
            int s = BassEnvelope.strengthAt(beatAt(level), 20);
            assertTrue("level " + level + " gave " + s + " after " + last, s >= last);
            last = s;
        }
    }

    @Test
    public void aGentleBuzzIsShorterThanAFullOne() {
        assertTrue(BassEnvelope.durationMs(40) < BassEnvelope.durationMs(120));
        assertTrue(BassEnvelope.durationMs(120) < BassEnvelope.durationMs(200));
        assertTrue(BassEnvelope.durationMs(200) < BassEnvelope.durationMs(255));
    }

    @Test
    public void aSteadyBassIsSofterThanANewBeatAtTheSameLevel() {
        float[] steady = new float[40];
        java.util.Arrays.fill(steady, 0.5f);
        int steadyStrength = BassEnvelope.strengthAt(steady, 20);
        int beatStrength = BassEnvelope.strengthAt(beatAt(0.5f), 20);
        assertTrue("steady " + steadyStrength + " against beat " + beatStrength, steadyStrength < beatStrength);
        assertTrue("a steady bass still gives some buzz", steadyStrength > 0);
    }

    @Test
    public void theBuzzStartsABitBeforeTheSound() {
        assertTrue(BassEnvelope.LEAD_MS > 0 && BassEnvelope.LEAD_MS < BassEnvelope.WINDOW_MS * 2);
    }
}
