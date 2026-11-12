package com.musicplayer.lite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.HashSet;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
public class AppearanceTest {
    private Application app;
    private Prefs prefs;

    @Before
    public void setUp() {
        TestMedia.resetSingletons();
        app = RuntimeEnvironment.getApplication();
        prefs = Prefs.get(app);
    }

    @Test
    public void darkUsesTheDarkPalette() {
        prefs.setThemeMode(Appearance.DARK);
        Context c = Appearance.wrap(app);
        assertTrue(Appearance.isNight(c));
        assertEquals(0xFF000000, c.getColor(R.color.bg));
        assertEquals(0xFFFFFFFF, c.getColor(R.color.text_primary));
        assertEquals("red text stays bright on black", 0xFFFF453A, c.getColor(R.color.accent_text));
    }

    @Test
    public void lightUsesTheLightPalette() {
        prefs.setThemeMode(Appearance.LIGHT);
        Context c = Appearance.wrap(app);
        assertFalse(Appearance.isNight(c));
        assertEquals(0xFFFFFFFF, c.getColor(R.color.bg));
        assertEquals(0xFF000000, c.getColor(R.color.text_primary));
        assertEquals("red text turns darker on white so it stays readable", 0xFFD70015, c.getColor(R.color.accent_text));
        assertEquals("button and progress fills keep the brighter red", 0xFFE5202B, c.getColor(R.color.accent));
    }

    @Test
    public void lightAndDarkTextHaveReadableContrast() {
        prefs.setThemeMode(Appearance.LIGHT);
        Context light = Appearance.wrap(app);
        prefs.setThemeMode(Appearance.DARK);
        Context dark = Appearance.wrap(app);
        int[] tokens = {R.color.text_primary, R.color.text_secondary, R.color.text_tertiary, R.color.accent_text};
        for (int token : tokens) {
            assertTrue("light " + token, contrast(light.getColor(token), light.getColor(R.color.bg)) >= 4.5);
            assertTrue("dark " + token, contrast(dark.getColor(token), dark.getColor(R.color.bg)) >= 4.5);
        }
    }

    @Test
    public void followingTheDeviceLeavesTheSystemModeAlone() {
        prefs.setThemeMode(Appearance.FOLLOW_DEVICE);
        RuntimeEnvironment.setQualifiers("+night");
        assertSame(app, Appearance.wrap(app));
        assertTrue(Appearance.isNight(Appearance.wrap(app)));
        RuntimeEnvironment.setQualifiers("+notnight");
        assertFalse(Appearance.isNight(Appearance.wrap(app)));
    }

    @Test
    public void forcingAModeIgnoresTheDevice() {
        RuntimeEnvironment.setQualifiers("+night");
        prefs.setThemeMode(Appearance.LIGHT);
        assertFalse("device is dark but the app was set to light", Appearance.isNight(Appearance.wrap(app)));
        RuntimeEnvironment.setQualifiers("+notnight");
        prefs.setThemeMode(Appearance.DARK);
        assertTrue(Appearance.isNight(Appearance.wrap(app)));
    }

    @Test
    public void everyLookHasItsOwnSignature() {
        Set<Integer> seen = new HashSet<>();
        for (int mode : new int[]{Appearance.FOLLOW_DEVICE, Appearance.LIGHT, Appearance.DARK}) {
            for (boolean glass : new boolean[]{false, true}) {
                prefs.setThemeMode(mode);
                prefs.setGlass(glass);
                assertTrue(seen.add(Appearance.signature(app)));
            }
        }
        assertEquals(6, seen.size());
        assertNotEquals(0, Appearance.signature(app));
    }

    // WCAG contrast ratio between two opaque colours.
    private static double contrast(int a, int b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(int c) {
        double r = channel((c >> 16) & 0xFF);
        double g = channel((c >> 8) & 0xFF);
        double b = channel(c & 0xFF);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(int v) {
        double s = v / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }
}
