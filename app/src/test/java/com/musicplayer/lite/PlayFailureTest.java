package com.musicplayer.lite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.media.MediaPlayer;
import android.os.Vibrator;
import android.view.View;
import android.widget.TextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowMediaPlayer;
import org.robolectric.shadows.ShadowToast;
import org.robolectric.shadows.util.DataSource;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Songs that do not start: the second way in, the reason that is shown, and no bass reading where nothing can buzz. */
@RunWith(RobolectricTestRunner.class)
public class PlayFailureTest {
    private Application app;
    private Prefs prefs;
    private Library library;

    @Before
    public void setUp() throws Exception {
        app = RuntimeEnvironment.getApplication();
        TestMedia.install(app);
        FakePlayback.register();
        Diag.reset();
        prefs = Prefs.get(app);
        library = Library.get(app);
        library.refresh();
        TestMedia.awaitLibrary(library);
    }

    @After
    public void tearDown() {
        Diag.reset();
    }

    private PlaybackService service() {
        PlaybackService s = Robolectric.buildService(PlaybackService.class).create().get();
        TestMedia.exposeService(app, s);
        return s;
    }

    /** Opening this song by its content address fails; by its file path it works. */
    private void breakTheContentAddress(Track t) {
        ShadowMediaPlayer.addException(DataSource.toDataSource(app, t.uri()), new IOException("no access"));
        ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(t.path()),
                new ShadowMediaPlayer.MediaInfo(t.durationMs, 0));
    }

    private static boolean flag(PlaybackService s, String name) throws Exception {
        Field f = PlaybackService.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.getBoolean(s);
    }

    private static MediaPlayer player(PlaybackService s) throws Exception {
        Field f = PlaybackService.class.getDeclaredField("player");
        f.setAccessible(true);
        return (MediaPlayer) f.get(s);
    }

    @Test
    public void aSongThatCannotBeOpenedByItsAddressPlaysByItsFilePath() throws Exception {
        Track t = library.tracks().get(0);
        breakTheContentAddress(t);
        PlaybackService s = service();
        List<Track> one = new ArrayList<>();
        one.add(t);
        s.playQueue(one, 0, false);
        TestMedia.idle();
        assertTrue("it plays", s.isPlaying());
        assertSame("the same song, not the next one", t, s.currentTrack());
        assertEquals("nothing counts as a failure", 0, Diag.get(Diag.PLAY_FAILED));
        assertNotNull(player(s));
    }

    @Test
    public void theWayThatWorkedGoesFirstFromThenOn() throws Exception {
        Track first = library.tracks().get(0);
        breakTheContentAddress(first);
        PlaybackService s = service();
        List<Track> one = new ArrayList<>();
        one.add(first);
        s.playQueue(one, 0, false);
        TestMedia.idle();
        assertTrue("the file path worked after the address failed, so it is preferred now", flag(s, "preferPath"));

        // The next song is registered both ways; it is opened by the way that worked, with no failure on the way.
        Track second = library.tracks().get(1);
        ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(second.path()),
                new ShadowMediaPlayer.MediaInfo(second.durationMs, 0));
        List<Track> two = new ArrayList<>();
        two.add(second);
        s.playQueue(two, 0, false);
        TestMedia.idle();
        assertTrue(s.isPlaying());
        assertEquals(0, Diag.get(Diag.PLAY_FAILED));
    }

    @Test
    public void whenBothWaysFailTheMessageSaysWhy() {
        for (Track t : library.tracks()) {
            ShadowMediaPlayer.addException(DataSource.toDataSource(app, t.uri()), new IOException("no access"));
            ShadowMediaPlayer.addException(DataSource.toDataSource(t.path()), new IOException("no access"));
        }
        PlaybackService s = service();
        s.playQueue(new ArrayList<>(library.tracks()), 0, false);
        TestMedia.idle();
        assertFalse("it gave up", s.isPlaying());
        assertTrue(Diag.get(Diag.PLAY_FAILED) >= 1);
        String message = ShadowToast.getTextOfLatestToast();
        assertNotNull(message);
        assertTrue(message, message.contains("Can't play this file"));
        assertTrue("it says why: " + message, message.contains("could not be opened"));
        assertTrue(Diag.lastPlayProblem().contains("could not be opened"));
    }

    @Test
    public void anErrorFromThePlayerIsExplained() throws Exception {
        PlaybackService s = service();
        s.playQueue(new ArrayList<>(library.tracks()), 0, false);
        TestMedia.idle();
        assertTrue(s.isPlaying());
        shadowOf(player(s)).invokeErrorListener(MediaPlayer.MEDIA_ERROR_UNKNOWN, MediaPlayer.MEDIA_ERROR_IO);
        TestMedia.idle();
        assertTrue(Diag.lastPlayProblem(), Diag.lastPlayProblem().contains("could not be read"));
    }

    @Test
    public void anUnsupportedFormatIsExplained() throws Exception {
        PlaybackService s = service();
        s.playQueue(new ArrayList<>(library.tracks()), 0, false);
        TestMedia.idle();
        shadowOf(player(s)).invokeErrorListener(MediaPlayer.MEDIA_ERROR_UNKNOWN, MediaPlayer.MEDIA_ERROR_UNSUPPORTED);
        TestMedia.idle();
        assertTrue(Diag.lastPlayProblem(), Diag.lastPlayProblem().contains("not supported"));
    }

    @Test
    public void settingsShowsTheLastSongThatDidNotPlay() {
        Diag.playFailed("Some song: the file could not be read");
        SettingsActivity a = Robolectric.buildActivity(SettingsActivity.class).create().start().resume().get();
        TextView t = a.findViewById(R.id.diag_text);
        assertEquals(View.VISIBLE, t.getVisibility());
        assertTrue(t.getText().toString(), t.getText().toString().contains("Some song: the file could not be read"));
        assertTrue(t.getText().toString().contains("1 songs could not be played"));
    }

    @Test
    public void aDeviceWithoutAVibratorNeverReadsTheSongFiles() throws Exception {
        prefs.setVibratePlaying(true);
        Vibrator v = app.getSystemService(Vibrator.class);
        shadowOf(v).setHasVibrator(false);
        BeatBuzz buzz = new BeatBuzz(app, () -> 0);
        buzz.setTrack(library.tracks().get(0));
        Field worker = BeatBuzz.class.getDeclaredField("worker");
        worker.setAccessible(true);
        assertNull("no background reader was even started", worker.get(buzz));
        buzz.stop();

        shadowOf(v).setHasVibrator(true);
        BeatBuzz withVibrator = new BeatBuzz(app, () -> 0);
        withVibrator.setTrack(library.tracks().get(1));
        assertNotNull("a phone that can buzz does read the song", worker.get(withVibrator));
        withVibrator.stop();
        prefs.setVibratePlaying(false);
    }
}
