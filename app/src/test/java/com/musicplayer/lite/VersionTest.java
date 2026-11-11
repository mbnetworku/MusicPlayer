package com.musicplayer.lite;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VersionTest {

    @Test
    public void readsVersionsWithOrWithoutAV() {
        assertArrayEquals(new int[]{1, 2, 3}, Release.parseVersion("1.2.3"));
        assertArrayEquals(new int[]{1, 2, 3}, Release.parseVersion("v1.2.3"));
        assertArrayEquals(new int[]{1, 2, 0}, Release.parseVersion("1.2"));
        assertArrayEquals(new int[]{2, 0, 0}, Release.parseVersion("V2"));
    }

    @Test
    public void ignoresSuffixesAndGarbage() {
        assertArrayEquals(new int[]{1, 2, 0}, Release.parseVersion("1.2.0-beta.1"));
        assertArrayEquals(new int[]{0, 0, 0}, Release.parseVersion(""));
        assertArrayEquals(new int[]{0, 0, 0}, Release.parseVersion(null));
        assertArrayEquals(new int[]{0, 0, 0}, Release.parseVersion("latest"));
    }

    @Test
    public void comparesNumbersNotText() {
        assertTrue(Release.isNewer("1.10.0", "1.9.9"));
        assertTrue(Release.isNewer("2.0.0", "1.99.99"));
        assertTrue(Release.isNewer("v1.0.1", "1.0.0"));
        assertFalse(Release.isNewer("1.0.0", "1.0.0"));
        assertFalse(Release.isNewer("1.0", "1.0.0"));
        assertFalse(Release.isNewer("1.0.0", "1.0.1"));
        assertEquals(0, Release.compare("v1.2.3", "1.2.3"));
    }

    @Test
    public void formatsDownloadSizes() {
        assertEquals("512 B", Fmt.size(512));
        assertEquals("93 KB", Fmt.size(95_257));
        assertEquals("2.1 MB", Fmt.size(2_202_009));
    }
}
