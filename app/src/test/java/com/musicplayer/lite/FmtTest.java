package com.musicplayer.lite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FmtTest {

    @Test
    public void timeShowsMinutesAndSeconds() {
        assertEquals("0:00", Fmt.time(0));
        assertEquals("0:09", Fmt.time(9_999));
        assertEquals("1:01", Fmt.time(61_000));
        assertEquals("59:59", Fmt.time(3_599_000));
    }

    @Test
    public void timeShowsHoursWhenNeeded() {
        assertEquals("1:00:00", Fmt.time(3_600_000));
        assertEquals("1:02:05", Fmt.time(3_725_000));
    }

    @Test
    public void negativeTimeIsZero() {
        assertEquals("0:00", Fmt.time(-5));
    }

    @Test
    public void decibelLabels() {
        assertEquals("0 dB", Fmt.db(0));
        assertEquals("+3 dB", Fmt.db(300));
        assertEquals("-1 dB", Fmt.db(-100));
        assertEquals("-1.5 dB", Fmt.db(-150));
        assertEquals("+1.5 dB", Fmt.db(150));
    }

    @Test
    public void frequencyLabels() {
        assertEquals("60 Hz", Fmt.freq(60_000));
        assertEquals("910 Hz", Fmt.freq(910_000));
        assertEquals("1 kHz", Fmt.freq(1_000_000));
        assertEquals("3.6 kHz", Fmt.freq(3_600_000));
        assertEquals("14 kHz", Fmt.freq(14_000_000));
    }

    @Test
    public void sectionLettersIgnoreAccentsAndCase() {
        assertEquals("E", Fmt.section("Éclair"));
        assertEquals("E", Fmt.section("eclair"));
        assertEquals("A", Fmt.section("abba"));
        assertEquals("#", Fmt.section("7 Rings"));
        assertEquals("#", Fmt.section("(untitled)"));
        assertEquals("#", Fmt.section(""));
        assertEquals("#", Fmt.section(null));
    }

    @Test
    public void containsIgnoreCase() {
        assertTrue(Fmt.containsIgnoreCase("Bohemian Rhapsody", "RHAP"));
        assertTrue(Fmt.containsIgnoreCase("abc", "abc"));
        assertTrue(Fmt.containsIgnoreCase("abc", ""));
        assertFalse(Fmt.containsIgnoreCase("abc", "abcd"));
        assertFalse(Fmt.containsIgnoreCase(null, "a"));
    }

    @Test
    public void clampKeepsValuesInRange() {
        assertEquals(5, Fmt.clamp(9, 0, 5));
        assertEquals(0, Fmt.clamp(-1, 0, 5));
        assertEquals(3, Fmt.clamp(3, 0, 5));
    }
}
