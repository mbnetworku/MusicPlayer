package com.musicplayer.lite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Runs under Robolectric because it needs the real org.json, not the empty stub. */
@RunWith(RobolectricTestRunner.class)
public class ReleaseParseTest {

    private static final String APK = "https://github.com/o/r/releases/download/v1.2.0/MusicPlayer-1.2.0.apk";

    private static String release(String extraAsset, String digest, String body) {
        return "{\"tag_name\":\"v1.2.0\",\"html_url\":\"https://github.com/o/r/releases/tag/v1.2.0\","
                + "\"body\":" + body + ",\"draft\":false,\"prerelease\":false,\"assets\":["
                + extraAsset
                + "{\"name\":\"MusicPlayer-1.2.0.apk\",\"size\":95257,\"browser_download_url\":\"" + APK + "\","
                + "\"digest\":" + digest + "}]}";
    }

    @Test
    public void readsTheFileAndItsChecksum() throws Exception {
        Release r = Release.parse(release("", "\"sha256:ABCDEF0123\"", "\"## New\\n- thing\""));
        assertEquals("1.2.0", r.version);
        assertEquals("MusicPlayer-1.2.0.apk", r.apkName);
        assertEquals(APK, r.apkUrl);
        assertEquals(95257L, r.apkSize);
        assertEquals("abcdef0123", r.sha256);
        assertEquals("https://github.com/o/r/releases/tag/v1.2.0", r.pageUrl);
        assertEquals("## New\n- thing", r.notes);
    }

    @Test
    public void jsonNullIsMissingNotTheWordNull() throws Exception {
        Release r = Release.parse(release("", "null", "null"));
        assertNull(r.sha256);
        assertEquals("", r.notes);
    }

    @Test
    public void findsASeparateChecksumFile() throws Exception {
        String sumAsset = "{\"name\":\"MusicPlayer-1.2.0.apk.sha256\",\"size\":90,"
                + "\"browser_download_url\":\"" + APK + ".sha256\",\"digest\":null},";
        Release r = Release.parse(release(sumAsset, "null", "\"\""));
        assertEquals(APK + ".sha256", r.sha256Url);
        assertEquals("the .apk is chosen, not the checksum file", "MusicPlayer-1.2.0.apk", r.apkName);
    }

    @Test
    public void aReleaseWithoutAnAppFileIsRefused() throws Exception {
        String json = "{\"tag_name\":\"v1.2.0\",\"assets\":[{\"name\":\"notes.txt\","
                + "\"browser_download_url\":\"https://x/notes.txt\"}]}";
        try {
            Release.parse(json);
            fail("expected NO_FILE");
        } catch (Release.ParseException e) {
            assertEquals(Release.ParseException.NO_FILE, e.kind);
        }
    }

    @Test
    public void aReleaseWithoutATagIsRefused() throws Exception {
        try {
            Release.parse("{\"assets\":[]}");
            fail("expected BAD_RESPONSE");
        } catch (Release.ParseException e) {
            assertEquals(Release.ParseException.BAD_RESPONSE, e.kind);
        }
    }

    @Test(expected = JSONException.class)
    public void notJsonIsRefused() throws Exception {
        Release.parse("<html>rate limited</html>");
    }
}
