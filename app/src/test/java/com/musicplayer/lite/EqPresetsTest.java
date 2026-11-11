package com.musicplayer.lite;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EqPresetsTest {
    /** The five bands most Android devices report (milliHertz). */
    private static final int[] FIVE = {60_000, 230_000, 910_000, 3_600_000, 14_000_000};
    /** A typical ten-band layout. */
    private static final int[] TEN = {31_000, 62_000, 125_000, 250_000, 500_000,
            1_000_000, 2_000_000, 4_000_000, 8_000_000, 16_000_000};

    @Test
    public void flatIsAllZero() {
        assertArrayEquals(new int[5], EqPresets.levelsFor(0, FIVE, -1500, 1500));
        assertArrayEquals(new int[10], EqPresets.levelsFor(0, TEN, -1500, 1500));
    }

    @Test
    public void bassBoostOnFiveBandsMatchesTheReferenceCurve() {
        assertArrayEquals(new int[]{600, 400, 100, 0, 0}, EqPresets.levelsFor(1, FIVE, -1500, 1500));
    }

    @Test
    public void levelsAreClampedToTheDeviceRange() {
        int[] rock = EqPresets.levelsFor(4, FIVE, -300, 300);
        for (int v : rock) assertTrue(v >= -300 && v <= 300);
        assertEquals(300, rock[0]);
        assertEquals(300, rock[4]);
    }

    @Test
    public void tenBandsInterpolateSmoothlyAndStayWholeDecibels() {
        int[] bass = EqPresets.levelsFor(1, TEN, -1500, 1500);
        assertEquals(10, bass.length);
        assertEquals("below the lowest reference the first value holds", 600, bass[0]);
        assertEquals("above the highest reference the last value holds", 0, bass[9]);
        for (int i = 1; i < bass.length; i++) {
            assertTrue("bass boost never rises with frequency", bass[i] <= bass[i - 1]);
        }
        for (int v : bass) assertEquals(0, v % 100);
    }

    @Test
    public void carPresetLiftsBassAndTrebleOverTheMiddle() {
        int[] car = EqPresets.levelsFor(10, FIVE, -1500, 1500);
        assertTrue(car[0] > car[2]);
        assertTrue(car[4] > car[2]);
    }

    @Test
    public void everyPresetHasAName() {
        assertEquals(11, EqPresets.COUNT);
        for (int i = 0; i < EqPresets.COUNT; i++) {
            assertEquals(FIVE.length, EqPresets.levelsFor(i, FIVE, -1500, 1500).length);
        }
    }

    @Test
    public void unknownPresetGivesFlatLevels() {
        assertArrayEquals(new int[5], EqPresets.levelsFor(-1, FIVE, -1500, 1500));
        assertArrayEquals(new int[5], EqPresets.levelsFor(99, FIVE, -1500, 1500));
    }
}
