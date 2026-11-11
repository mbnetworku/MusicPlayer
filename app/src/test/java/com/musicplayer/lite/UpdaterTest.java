package com.musicplayer.lite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/** The whole update flow against a local server that pretends to be GitHub. */
@RunWith(RobolectricTestRunner.class)
public class UpdaterTest {
    private static final String REPO = "owner/app";

    private Application app;
    private TestServer server;
    private File dir;
    private final AtomicInteger commits = new AtomicInteger();
    private byte[] apkBytes;
    private String apkSha;

    @Before
    public void setUp() throws Exception {
        app = RuntimeEnvironment.getApplication();
        TestMedia.resetSingletons();
        server = new TestServer();
        dir = Files.createTempDirectory("updates").toFile();
        apkBytes = new byte[300 * 1024];
        new Random(1).nextBytes(apkBytes);
        apkSha = ReleaseApi.hex(MessageDigest.getInstance("SHA-256").digest(apkBytes));
    }

    @After
    public void tearDown() {
        server.close();
    }

    private String releaseJson(String tag, String digest, boolean sumFile) {
        String apk = server.base() + "/download/app.apk";
        String sum = sumFile
                ? "{\"name\":\"app.apk.sha256\",\"size\":70,\"browser_download_url\":\"" + server.base()
                + "/download/app.apk.sha256\",\"digest\":null}," : "";
        return "{\"tag_name\":\"" + tag + "\",\"html_url\":\"https://example.org/r\",\"body\":\"## Notes\","
                + "\"assets\":[" + sum + "{\"name\":\"app.apk\",\"size\":" + apkBytes.length
                + ",\"browser_download_url\":\"" + apk + "\",\"digest\":" + digest + "}]}";
    }

    private Updater updater(String current, boolean allowInsecure, int verifierResult) {
        Updater.Config config = new Updater.Config(server.base(), REPO, allowInsecure, dir, current,
                (c, f, v) -> verifierResult, (c, f) -> commits.incrementAndGet());
        Updater u = new Updater(app, config);
        Updater.replaceForTests(u);
        return u;
    }

    private static void waitFor(Updater u, Updater.State wanted) throws InterruptedException {
        long end = System.currentTimeMillis() + 10_000;
        while (u.state() != wanted && System.currentTimeMillis() < end) {
            ShadowLooper.idleMainLooper();
            Thread.sleep(10);
        }
        ShadowLooper.idleMainLooper();
        assertEquals(wanted, u.state());
    }

    private void publish(String tag, String digest, boolean sumFile) {
        server.text("/repos/" + REPO + "/releases/latest", 200, releaseJson(tag, digest, sumFile));
        server.bytes("/download/app.apk", apkBytes);
    }

    @Test
    public void findsANewerRelease() throws Exception {
        publish("v1.2.0", "\"sha256:" + apkSha + "\"", false);
        Updater u = updater("1.1.0", true, 0);
        assertEquals(Updater.State.IDLE, u.state());
        u.check(false);
        assertEquals(Updater.State.CHECKING, u.state());
        waitFor(u, Updater.State.AVAILABLE);
        assertEquals("1.2.0", u.newerRelease().version);
        assertTrue(u.lastCheck() > 0);
    }

    @Test
    public void saysYouAreUpToDate() throws Exception {
        publish("v1.1.0", "null", false);
        Updater u = updater("1.1.0", true, 0);
        u.check(false);
        waitFor(u, Updater.State.UP_TO_DATE);
        assertNull(u.newerRelease());
    }

    @Test
    public void noReleaseYetIsNotAnError() throws Exception {
        server.text("/repos/" + REPO + "/releases/latest", 404, "{\"message\":\"Not Found\"}");
        Updater u = updater("1.1.0", true, 0);
        u.check(false);
        waitFor(u, Updater.State.NONE_PUBLISHED);
    }

    @Test
    public void rateLimitGivesAClearMessage() throws Exception {
        server.text("/repos/" + REPO + "/releases/latest", 403, "{\"message\":\"rate limit\"}");
        Updater u = updater("1.1.0", true, 0);
        u.check(false);
        waitFor(u, Updater.State.ERROR);
        assertEquals(R.string.upd_err_limit, u.errorRes());
    }

    @Test
    public void unreachableServerGivesANetworkMessage() throws Exception {
        Updater u = updater("1.1.0", true, 0);
        server.close();
        u.check(false);
        waitFor(u, Updater.State.ERROR);
        assertEquals(R.string.upd_err_network, u.errorRes());
    }

    @Test
    public void anAutomaticCheckStaysSilentWhenItFails() throws Exception {
        server.text("/repos/" + REPO + "/releases/latest", 500, "boom");
        Updater u = updater("1.1.0", true, 0);
        u.check(true);
        waitFor(u, Updater.State.IDLE);
    }

    @Test
    public void downloadsVerifiesAndGetsReady() throws Exception {
        publish("v1.2.0", "\"sha256:" + apkSha + "\"", false);
        Updater u = updater("1.1.0", true, 0);
        u.check(false);
        waitFor(u, Updater.State.AVAILABLE);
        u.download();
        assertEquals(Updater.State.DOWNLOADING, u.state());
        waitFor(u, Updater.State.READY);
        File saved = new File(dir, "MusicPlayer-1.2.0.apk");
        assertTrue(saved.isFile());
        assertEquals(apkBytes.length, saved.length());
        assertFalse("no half-finished file is left", new File(dir, "download.part").exists());
    }

    @Test
    public void usesTheChecksumFileWhenGithubGivesNoDigest() throws Exception {
        publish("v1.2.0", "null", true);
        server.text("/download/app.apk.sha256", 200, apkSha + "  app.apk\n");
        Updater u = updater("1.1.0", true, 0);
        u.check(false);
        waitFor(u, Updater.State.AVAILABLE);
        u.download();
        waitFor(u, Updater.State.READY);
    }

    @Test
    public void aWrongChecksumDeletesTheFile() throws Exception {
        publish("v1.2.0", "\"sha256:" + "0".repeat(64) + "\"", false);
        Updater u = updater("1.1.0", true, 0);
        u.check(false);
        waitFor(u, Updater.State.AVAILABLE);
        u.download();
        waitFor(u, Updater.State.ERROR);
        assertEquals(R.string.upd_err_checksum, u.errorRes());
        File[] left = dir.listFiles();
        assertEquals("nothing left behind", 0, left == null ? 0 : left.length);
    }

    @Test
    public void aFileTheVerifierRejectsIsDeleted() throws Exception {
        publish("v1.2.0", "\"sha256:" + apkSha + "\"", false);
        Updater u = updater("1.1.0", true, R.string.upd_err_signature);
        u.check(false);
        waitFor(u, Updater.State.AVAILABLE);
        u.download();
        waitFor(u, Updater.State.ERROR);
        assertEquals(R.string.upd_err_signature, u.errorRes());
        assertFalse(new File(dir, "MusicPlayer-1.2.0.apk").exists());
    }

    @Test
    public void productionRefusesPlainHttp() throws Exception {
        publish("v1.2.0", "null", false);
        Updater u = updater("1.1.0", false, 0); // allowInsecure = false, like the real app
        u.check(false);
        waitFor(u, Updater.State.ERROR);
        assertEquals(R.string.upd_err_insecure, u.errorRes());
    }

    @Test
    public void aDownloadCanBeCancelled() throws Exception {
        publish("v1.2.0", "null", false);
        server.slowBytes("/download/app.apk", apkBytes, 80);
        Updater u = updater("1.1.0", true, 0);
        u.check(false);
        waitFor(u, Updater.State.AVAILABLE);
        u.download();
        long end = System.currentTimeMillis() + 5_000;
        while (u.percent() == 0 && System.currentTimeMillis() < end) {
            ShadowLooper.idleMainLooper();
            Thread.sleep(10);
        }
        assertTrue("download started", u.percent() > 0);
        u.cancelDownload();
        waitFor(u, Updater.State.AVAILABLE);
        File[] left = dir.listFiles();
        assertEquals(0, left == null ? 0 : left.length);
    }

    @Test
    public void aNewerReleaseIsRememberedOffline() throws Exception {
        publish("v1.2.0", "null", false);
        Updater first = updater("1.1.0", true, 0);
        first.check(false);
        waitFor(first, Updater.State.AVAILABLE);

        server.close();
        Updater second = updater("1.1.0", true, 0);
        assertEquals("shown again straight away, with no network", Updater.State.AVAILABLE, second.state());
        assertEquals("1.2.0", second.newerRelease().version);
    }

    @Test
    public void onceInstalledTheRememberedReleaseIsNoLongerNewer() throws Exception {
        publish("v1.2.0", "null", false);
        Updater first = updater("1.1.0", true, 0);
        first.check(false);
        waitFor(first, Updater.State.AVAILABLE);
        Updater upgraded = updater("1.2.0", true, 0);
        assertNull(upgraded.newerRelease());
        assertEquals(Updater.State.IDLE, upgraded.state());
    }

    @Test
    public void automaticChecksAreThrottledAndCanBeSwitchedOff() throws Exception {
        publish("v1.1.0", "null", false);
        Prefs prefs = Prefs.get(app);
        Updater u = updater("1.1.0", true, 0);

        prefs.setAutoUpdateCheck(false);
        u.autoCheckIfDue();
        assertEquals("switched off: no request", 0, server.requests.get());

        prefs.setAutoUpdateCheck(true);
        u.autoCheckIfDue();
        waitFor(u, Updater.State.UP_TO_DATE);
        int afterFirst = server.requests.get();
        assertEquals(1, afterFirst);

        u.autoCheckIfDue(); // checked a moment ago: not due again for a day
        ShadowLooper.idleMainLooper();
        assertEquals(afterFirst, server.requests.get());
    }

    @Test
    public void installNeedsPermissionBeforeItCommits() throws Exception {
        publish("v1.2.0", "\"sha256:" + apkSha + "\"", false);
        Updater u = updater("1.1.0", true, 0);
        u.check(false);
        waitFor(u, Updater.State.AVAILABLE);
        u.download();
        waitFor(u, Updater.State.READY);

        android.app.Activity a = org.robolectric.Robolectric.buildActivity(SettingsActivity.class).create().get();
        org.robolectric.Shadows.shadowOf(a.getPackageManager()).setCanRequestPackageInstalls(false);
        u.install(a);
        assertEquals(Updater.State.NEEDS_PERMISSION, u.state());
        assertEquals(0, commits.get());

        org.robolectric.Shadows.shadowOf(a.getPackageManager()).setCanRequestPackageInstalls(true);
        u.install(a);
        long end = System.currentTimeMillis() + 5_000;
        while (commits.get() == 0 && System.currentTimeMillis() < end) {
            ShadowLooper.idleMainLooper();
            Thread.sleep(10);
        }
        assertEquals(1, commits.get());
        assertEquals(Updater.State.INSTALLING, u.state());

        // Android says the user declined: back to ready. Then success clears everything.
        u.onInstallResult(android.content.pm.PackageInstaller.STATUS_FAILURE_ABORTED, "declined");
        assertEquals(Updater.State.READY, u.state());
        u.onInstallResult(android.content.pm.PackageInstaller.STATUS_FAILURE_INCOMPATIBLE, "conflict");
        assertEquals(R.string.upd_err_signature, u.errorRes());
    }
}
