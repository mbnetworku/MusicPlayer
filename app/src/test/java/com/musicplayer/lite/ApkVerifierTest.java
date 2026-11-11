package com.musicplayer.lite;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** The checks that run on a downloaded file before Android is asked to install it. */
@RunWith(RobolectricTestRunner.class)
public class ApkVerifierTest {
    private static final String ME = "com.musicplayer.lite";

    private static Set<String> keys(String... names) {
        Set<String> s = new HashSet<>();
        Collections.addAll(s, names);
        return s;
    }

    private static int judge(String pkg, String version, Set<String> archiveKeys, Set<String> installedKeys) {
        return ApkVerifier.judge(ME, pkg, version, "1.1.0", archiveKeys, installedKeys);
    }

    @Test
    public void aNewerVersionSignedWithTheSameKeyIsAccepted() {
        assertEquals(0, judge(ME, "1.2.0", keys("A"), keys("A")));
    }

    @Test
    public void anotherAppIsRefused() {
        assertEquals(R.string.upd_err_wrong_app, judge("com.other.app", "9.0.0", keys("A"), keys("A")));
    }

    @Test
    public void aVersionThatIsNotNewerIsRefused() {
        assertEquals(R.string.upd_err_old, judge(ME, "1.1.0", keys("A"), keys("A")));
        assertEquals(R.string.upd_err_old, judge(ME, "1.0.9", keys("A"), keys("A")));
        assertEquals(R.string.upd_err_old, judge(ME, null, keys("A"), keys("A")));
    }

    @Test
    public void aDifferentSigningKeyIsRefusedWithAClearMessage() {
        assertEquals(R.string.upd_err_signature, judge(ME, "1.2.0", keys("B"), keys("A")));
    }

    @Test
    public void anyCertificateInCommonIsEnough() {
        // Key rotation: the new file proves it may use the old key as well as a new one.
        assertEquals(0, judge(ME, "1.2.0", keys("A", "B"), keys("A")));
        assertEquals(0, judge(ME, "1.2.0", keys("B"), keys("A", "B")));
    }

    @Test
    public void whenACertificateCannotBeReadAndroidDecides() {
        assertEquals(0, judge(ME, "1.2.0", keys(), keys("A")));
        assertEquals(0, judge(ME, "1.2.0", keys("A"), keys()));
    }

    @Test
    public void theOrderOfTheChecksPutsTheMostUsefulMessageFirst() {
        // Wrong app wins over everything, an old version wins over a key mismatch.
        assertEquals(R.string.upd_err_wrong_app, judge("com.other.app", "0.1", keys("B"), keys("A")));
        assertEquals(R.string.upd_err_old, judge(ME, "1.0.0", keys("B"), keys("A")));
    }

    @Test
    public void aFileThatIsNotAnAppIsInvalid() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        Context c = app;
        File junk = File.createTempFile("not-an-apk", ".apk");
        Files.write(junk.toPath(), "this is not an apk".getBytes("UTF-8"));
        assertEquals(R.string.upd_err_invalid, ApkVerifier.check(c, junk, "1.1.0"));
        assertEquals(R.string.upd_err_invalid, ApkVerifier.check(c, new File(junk.getParentFile(), "missing.apk"), "1.1.0"));
    }
}
