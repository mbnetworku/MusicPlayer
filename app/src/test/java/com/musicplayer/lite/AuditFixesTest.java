package com.musicplayer.lite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Application;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowMediaPlayer;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** The limits and deadlines added after the queue and overload audit (QUEUE-BACKPRESSURE.md). */
@RunWith(RobolectricTestRunner.class)
public class AuditFixesTest {
    private Application app;
    private Prefs prefs;

    @Before
    public void setUp() throws Exception {
        app = RuntimeEnvironment.getApplication();
        TestMedia.install(app);
        FakePlayback.register();
        prefs = Prefs.get(app);
        Library library = Library.get(app);
        library.refresh();
        TestMedia.awaitLibrary(library);
    }

    @After
    public void tearDown() {
        Diag.reset();
    }

    private static void waitFor(java.util.function.BooleanSupplier done) throws InterruptedException {
        for (int i = 0; i < 200 && !done.getAsBoolean(); i++) {
            TestMedia.idle();
            Thread.sleep(10);
        }
    }

    private Track song(long id, int durationMs) {
        return new Track(id, "Song " + id, "Artist", "Album", 1, durationMs, 1, "/music", "song" + id + ".mp3");
    }

    // ---- QBP-01: the cover queue is bounded and drops the oldest ---------------------------------------

    @Test
    public void theCoverQueueKeepsTheNewestAndDropsTheOldest() throws Exception {
        Class<?> queueClass = Class.forName("com.musicplayer.lite.ArtLoader$LifoQueue");
        Constructor<?> c = queueClass.getDeclaredConstructor();
        c.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.concurrent.LinkedBlockingDeque<Runnable> queue =
                (java.util.concurrent.LinkedBlockingDeque<Runnable>) c.newInstance();
        final List<Integer> ran = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            final int n = i;
            queue.offer(() -> ran.add(n));
        }
        assertEquals("no more than 96 wait", 96, queue.size());
        assertEquals("the oldest were dropped and counted", 104, Diag.get(Diag.ART_SKIPPED));
        queue.pollFirst().run();
        queue.pollLast().run();
        assertEquals("the newest runs first", Integer.valueOf(199), ran.get(0));
        assertEquals("the oldest that is left runs last", Integer.valueOf(104), ran.get(1));
    }

    // ---- P-04: the list of covers that were not found is bounded --------------------------------------

    @Test
    public void theListOfMissingCoversIsBounded() throws Exception {
        ArtLoader loader = ArtLoader.get(app);
        Field f = ArtLoader.class.getDeclaredField("missing");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<String> missing = (Set<String>) f.get(loader);
        for (int i = 0; i < 6000; i++) missing.add("key" + i);
        assertTrue("bounded: " + missing.size(), missing.size() <= 4096);
        assertTrue("the newest are kept", missing.contains("key5999"));
        assertFalse("the oldest are forgotten", missing.contains("key0"));
    }

    // ---- QBP-02: a cover request that is no longer wanted is not decoded -------------------------------

    @Test
    public void aCoverRequestThatIsNoLongerWantedIsSkipped() throws Exception {
        final AtomicInteger answers = new AtomicInteger();
        Track t = song(9001, 1000);
        ArtLoader.get(app).loadAsync(t, 96, b -> answers.incrementAndGet(), () -> false);
        for (int i = 0; i < 30; i++) {
            TestMedia.idle();
            Thread.sleep(10);
        }
        assertEquals("nobody wanted it: no answer", 0, answers.get());

        Track u = song(9002, 1000);
        ArtLoader.get(app).loadAsync(u, 96, b -> answers.incrementAndGet(), () -> true);
        waitFor(() -> answers.get() > 0);
        assertEquals("a wanted request is answered", 1, answers.get());
    }

    // ---- QBP-03: a cancelled bass reading never opens the file; a very long song is skipped ------------

    @Test
    public void aCancelledBassReadingDoesNotOpenTheFile() {
        float[] result = EnvelopeDecoder.decode(app, song(1, 1000), new AtomicBoolean(true));
        assertNull(result);
    }

    @Test
    public void aVeryLongSongGetsNoBassReading() {
        prefs.setVibratePlaying(true);
        BeatBuzz buzz = new BeatBuzz(app, () -> 0);
        buzz.setTrack(song(5, (int) (EnvelopeDecoder.MAX_DURATION_MS + 60_000)));
        assertEquals(1, Diag.get(Diag.BASS_TOO_LONG));
        buzz.setTrack(song(6, 3 * 60 * 1000));
        assertEquals("an ordinary song is not counted", 1, Diag.get(Diag.BASS_TOO_LONG));
        buzz.stop();
        prefs.setVibratePlaying(false);
    }

    // ---- QBP-04: a song that never starts is given up after the deadline -------------------------------

    @Test
    public void aSongThatNeverStartsIsGivenUp() throws Exception {
        Track slow = Library.get(app).tracks().get(0);
        Uri uri = slow.uri();
        // The player takes far longer than the deadline to get ready.
        ShadowMediaPlayer.addMediaInfo(
                org.robolectric.shadows.util.DataSource.toDataSource(app, uri),
                new ShadowMediaPlayer.MediaInfo(60_000, PlaybackService.PREPARE_TIMEOUT_MS * 4));
        PlaybackService service = Robolectric.buildService(PlaybackService.class).create().get();
        TestMedia.exposeService(app, service);
        List<Track> one = new ArrayList<>();
        one.add(slow);
        service.playQueue(one, 0, false);
        TestMedia.idle();
        assertEquals(0, Diag.get(Diag.PREPARE_TIMEOUT));
        ShadowLooper.idleMainLooper(PlaybackService.PREPARE_TIMEOUT_MS + 1000, TimeUnit.MILLISECONDS);
        assertEquals("the deadline passed: it counts as an error", 1, Diag.get(Diag.PREPARE_TIMEOUT));
        // The player tries the next song (here the same one again). When that is too slow too, it gives up
        // and does not keep waiting for ever.
        ShadowLooper.idleMainLooper(PlaybackService.PREPARE_TIMEOUT_MS + 1000, TimeUnit.MILLISECONDS);
        assertEquals(2, Diag.get(Diag.PREPARE_TIMEOUT));
        assertFalse("and it stops trying", service.isPlaying());
    }

    @Test
    public void aSongThatStartsInTimeNeverTriggersTheDeadline() throws Exception {
        PlaybackService service = Robolectric.buildService(PlaybackService.class).create().get();
        TestMedia.exposeService(app, service);
        service.playQueue(new ArrayList<>(Library.get(app).tracks()), 0, false);
        TestMedia.idle();
        ShadowLooper.idleMainLooper(PlaybackService.PREPARE_TIMEOUT_MS + 1000, TimeUnit.MILLISECONDS);
        assertEquals(0, Diag.get(Diag.PREPARE_TIMEOUT));
    }

    // ---- QBP-05: queue changes are written once, and never lost -----------------------------------------

    @Test
    public void queueChangesAreWrittenOnceAMomentLater() {
        PlaybackService service = Robolectric.buildService(PlaybackService.class).create().get();
        TestMedia.exposeService(app, service);
        service.playQueue(new ArrayList<>(Library.get(app).tracks()), 0, false);
        service.enqueue(new ArrayList<>(Library.get(app).tracks()));
        assertEquals("nothing is written for every change", 0, prefs.queueIds().length);
        ShadowLooper.idleMainLooper(PlaybackService.QUEUE_SAVE_DELAY_MS + 50, TimeUnit.MILLISECONDS);
        assertEquals("one write holds the whole queue", Library.get(app).tracks().size() * 2, prefs.queueIds().length);
    }

    @Test
    public void aWaitingQueueChangeIsWrittenWhenTheServiceEnds() {
        ServiceController<PlaybackService> sc = Robolectric.buildService(PlaybackService.class).create();
        PlaybackService service = sc.get();
        TestMedia.exposeService(app, service);
        service.playQueue(new ArrayList<>(Library.get(app).tracks()), 0, false);
        assertEquals(0, prefs.queueIds().length);
        sc.destroy();
        assertEquals("not lost", Library.get(app).tracks().size(), prefs.queueIds().length);
    }

    // ---- QBP-06: the search waits for a pause in the typing ------------------------------------------------

    @Test
    public void theSearchRunsWhenTheTypingPauses() {
        ActivityController<LibraryActivity> c = Robolectric.buildActivity(LibraryActivity.class).create().start().resume().visible();
        TestMedia.idle();
        LibraryActivity a = c.get();
        ListView list = a.findViewById(R.id.list);
        int all = list.getAdapter().getCount();
        a.findViewById(R.id.btn_search).performClick();
        EditText input = a.findViewById(R.id.search_input);
        input.setText("g");
        input.setText("gl");
        input.setText("glass");
        assertEquals("while typing, the list is not rebuilt for every letter", all, list.getAdapter().getCount());
        ShadowLooper.idleMainLooper(LibraryActivity.SEARCH_PAUSE_MS + 20, TimeUnit.MILLISECONDS);
        assertEquals(1, list.getAdapter().getCount());
        a.findViewById(R.id.search_close).performClick();
        assertEquals("leaving the search is instant", all, list.getAdapter().getCount());
        c.pause().stop().destroy();
    }

    // ---- QBP-07: the scan is reported once, and the time limit exists ------------------------------------

    @Test
    public void aScanIsReportedOnlyOnce() throws Exception {
        java.lang.reflect.Method finish = StorageScanner.class.getDeclaredMethod("finish",
                AtomicBoolean.class, android.os.Handler.class, StorageScanner.Callback.class, int.class);
        finish.setAccessible(true);
        final AtomicInteger told = new AtomicInteger();
        final AtomicBoolean done = new AtomicBoolean();
        android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        StorageScanner.Callback cb = requested -> told.incrementAndGet();
        finish.invoke(null, done, main, cb, 3);
        finish.invoke(null, done, main, cb, 3);   // the timeout comes after the real answer
        TestMedia.idle();
        assertEquals(1, told.get());
        assertTrue("there is a time limit", StorageScanner.SCAN_TIMEOUT_MS > 0 && StorageScanner.SCAN_TIMEOUT_MS <= 300_000);
    }

    // ---- QBP-08: a download stops at the announced size ----------------------------------------------------

    @Test
    public void aDownloadLargerThanAnnouncedIsStopped() throws Exception {
        try (TestServer server = new TestServer()) {
            server.bytes("/big.apk", new byte[200_000]);
            File dir = Files.createTempDirectory("dl").toFile();
            File part = new File(dir, "download.part");
            try {
                ReleaseApi.download(server.base() + "/big.apk", part, 1000, null, new AtomicBoolean(),
                        "test", true);
                fail("it should have stopped");
            } catch (ReleaseApi.ApiException e) {
                assertEquals(ReleaseApi.ApiException.CHECKSUM, e.kind);
            }
            assertEquals(1, Diag.get(Diag.DOWNLOAD_STOPPED));
            assertTrue("it did not write the whole file", part.length() < 200_000);
        }
    }

    @Test
    public void aDownloadOfTheAnnouncedSizeStillWorks() throws Exception {
        try (TestServer server = new TestServer()) {
            server.bytes("/ok.apk", new byte[50_000]);
            File dir = Files.createTempDirectory("dl2").toFile();
            File part = new File(dir, "download.part");
            String sha = ReleaseApi.download(server.base() + "/ok.apk", part, 50_000, null, new AtomicBoolean(),
                    "test", true);
            assertEquals(64, sha.length());
            assertEquals(50_000, part.length());
            assertEquals(0, Diag.get(Diag.DOWNLOAD_STOPPED));
        }
        assertTrue(ReleaseApi.MAX_APK_BYTES > 10L * 1024 * 1024 && ReleaseApi.MAX_DOWNLOAD_MS >= 60_000);
    }

    // ---- QBP-09: the counters, the log and the lines in Settings -------------------------------------------

    @Test
    public void countsAreLoggedOnTheFirstAndEveryTenthTime() {
        ShadowLog.clear();
        for (int i = 0; i < 12; i++) Diag.count(Diag.SCAN_STOPPED);
        assertEquals(12, Diag.get(Diag.SCAN_STOPPED));
        int logged = 0;
        for (ShadowLog.LogItem item : ShadowLog.getLogs()) if ("MusicPlayer".equals(item.tag)) logged++;
        assertEquals("the 1st and the 10th", 2, logged);
    }

    @Test
    public void settingsShowsWhatHappenedInTheBackground() {
        Diag.reset();
        SettingsActivity quiet = Robolectric.buildActivity(SettingsActivity.class).create().start().resume().get();
        assertEquals("nothing to tell", View.GONE, quiet.findViewById(R.id.diag_text).getVisibility());

        Diag.count(Diag.PREPARE_TIMEOUT);
        Diag.reloaded(42, 7);
        SettingsActivity a = Robolectric.buildActivity(SettingsActivity.class).create().start().resume().get();
        TextView t = a.findViewById(R.id.diag_text);
        assertEquals(View.VISIBLE, t.getVisibility());
        String text = t.getText().toString();
        assertTrue(text, text.contains("1 songs took too long to start"));
        assertTrue(text, text.contains("42 ms for 7 songs"));
    }

    @Test
    public void theNewSettingsShowsThemToo() {
        prefs.setDesign(Design.NEW);
        Diag.count(Diag.ART_SKIPPED);
        SettingsActivity a = Robolectric.buildActivity(SettingsActivity.class).create().start().resume().get();
        assertEquals(View.VISIBLE, a.findViewById(R.id.diag_text).getVisibility());
    }

    @Test
    public void aReloadIsTimed() throws Exception {
        Diag.reset();
        Library library = Library.get(app);
        library.refresh();
        TestMedia.awaitLibrary(library);
        waitFor(() -> Diag.lastReloadMs() >= 0);
        assertTrue(Diag.lastReloadMs() >= 0);
    }

    // ---- QBP-10: the widget is not drawn again for the same state -------------------------------------------

    @Test
    public void theWidgetIsNotRedrawnForTheSameState() throws Exception {
        PlayerWidget.forgetPublished();
        Track t = song(77, 1000);
        Bitmap a = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
        Field f = PlayerWidget.class.getDeclaredField("lastArt");
        f.setAccessible(true);
        PlayerWidget.publish(app, t, true, a);
        Object scaled = f.get(null);
        assertNotNull(scaled);
        PlayerWidget.publish(app, t, true, a);
        assertSame("the same state: the cover is not scaled again", scaled, f.get(null));
        PlayerWidget.publish(app, t, false, a);
        assertSame("a new play state does not need a new cover", scaled, f.get(null));
        Bitmap b = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888);
        PlayerWidget.publish(app, t, false, b);
        assertNotSame("a new cover is scaled", scaled, f.get(null));
    }

    // ---- QBP-12: continuous changes still lead to a reload ---------------------------------------------------

    @Test
    public void aNeverEndingStreamOfChangesStillReloadsTheList() throws Exception {
        final Library library = Library.get(app);
        final AtomicInteger reloads = new AtomicInteger();
        library.addListener(reloads::incrementAndGet);
        Uri uri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        java.util.Collection<android.database.ContentObserver> observers = org.robolectric.Shadows.shadowOf(app.getContentResolver())
                .getContentObservers(uri);
        assertFalse(observers.isEmpty());
        // A change every second for 15 seconds: the quiet time of 1.5 s is never reached.
        for (int i = 0; i < 15; i++) {
            for (android.database.ContentObserver o : observers) o.dispatchChange(false, uri);
            ShadowLooper.idleMainLooper(1000, TimeUnit.MILLISECONDS);
            TestMedia.idle();
        }
        waitFor(() -> reloads.get() > 0);
        assertTrue("it reloaded although the changes never stopped", reloads.get() >= 1);
    }

    @Test
    public void aQuietPeriodStillReloadsAfterTheShortWait() throws Exception {
        final Library library = Library.get(app);
        final AtomicInteger reloads = new AtomicInteger();
        library.addListener(reloads::incrementAndGet);
        Uri uri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        for (android.database.ContentObserver o : org.robolectric.Shadows.shadowOf(app.getContentResolver())
                .getContentObservers(uri)) {
            o.dispatchChange(false, uri);
        }
        ShadowLooper.idleMainLooper(1600, TimeUnit.MILLISECONDS);
        waitFor(() -> reloads.get() > 0);
        assertEquals(1, reloads.get());
    }
}
