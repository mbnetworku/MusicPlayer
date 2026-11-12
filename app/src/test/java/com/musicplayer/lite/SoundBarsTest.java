package com.musicplayer.lite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/** When the app listens to its own sound, and when it leaves the bars to their own motion. */
@RunWith(RobolectricTestRunner.class)
public class SoundBarsTest {

    /** A pretend Visualizer: counts how often it was opened and closed, and lets the test send frames. */
    static final class FakeSource implements SoundBars.Source {
        int opened;
        int closed;
        int session;
        boolean refuse;
        SoundBars.Frames frames;

        @Override
        public SoundBars.Capture open(int audioSession, SoundBars.Frames f) {
            if (refuse) throw new UnsupportedOperationException("this device has no such effect");
            opened++;
            session = audioSession;
            frames = f;
            return () -> closed++;
        }

        /** A loud tone at about 990 Hz. */
        void tone() {
            byte[] fft = new byte[1024];
            fft[46] = 100;
            frames.onFft(fft, 44_100_000);
        }
    }

    private Application app;
    private FakeSource source;
    private final long[] now = {1000};
    private SoundBars bars;

    @Before
    public void setUp() {
        TestMedia.resetSingletons();
        app = RuntimeEnvironment.getApplication();
        source = new FakeSource();
        bars = new SoundBars(app, source, () -> now[0]);
        bars.setSession(11);
    }

    private void allow() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO);
    }

    @Test
    public void listensOnlyWhileSomeoneWantsTheSound() {
        allow();
        assertEquals("nobody wants it yet", 0, source.opened);
        bars.acquire();
        assertEquals(1, source.opened);
        assertEquals("it listens to the player's own session", 11, source.session);
        bars.acquire();
        assertEquals("one listener serves everybody", 1, source.opened);
        bars.release();
        assertEquals("still wanted by the other one", 0, source.closed);
        assertTrue(bars.listening());
        bars.release();
        assertEquals(1, source.closed);
        assertFalse(bars.listening());
    }

    @Test
    public void withoutThePermissionItNeverListens() {
        bars.acquire();
        assertEquals(0, source.opened);
        assertFalse(bars.listening());
        assertFalse(bars.following());

        allow();                                        // the user said yes in Android's question
        bars.refresh();
        assertEquals(1, source.opened);
        assertTrue(bars.listening());
    }

    @Test
    public void switchedOffInSettingsItDoesNotListenAndStopsWhenSwitchedOff() {
        allow();
        Prefs.get(app).setBarsFollow(false);
        bars.acquire();
        assertEquals(0, source.opened);

        Prefs.get(app).setBarsFollow(true);
        bars.refresh();
        assertEquals(1, source.opened);

        Prefs.get(app).setBarsFollow(false);
        bars.refresh();
        assertEquals("switching it off stops the listening at once", 1, source.closed);
        assertFalse(bars.listening());
    }

    @Test
    public void withNoAudioSessionThereIsNothingToListenTo() {
        allow();
        bars.setSession(0);
        bars.acquire();
        assertEquals(0, source.opened);
        bars.setSession(12);                            // the player now exists
        assertEquals(1, source.opened);
        assertEquals(12, source.session);
    }

    @Test
    public void aNewAudioSessionRestartsTheListener() {
        allow();
        bars.acquire();
        bars.setSession(13);
        assertEquals(1, source.closed);
        assertEquals(2, source.opened);
        assertEquals(13, source.session);
        bars.setSession(13);
        assertEquals("the same session again changes nothing", 2, source.opened);
    }

    @Test
    public void aDeviceThatRefusesIsNotAskedAgainUntilToldToLookAgain() {
        allow();
        source.refuse = true;
        bars.acquire();
        assertFalse(bars.listening());
        bars.release();
        bars.acquire();
        assertEquals("it did not retry", 0, source.opened);
        assertFalse(bars.following());

        source.refuse = false;
        bars.refresh();
        assertEquals(1, source.opened);
        assertTrue(bars.listening());
    }

    @Test
    public void theBarsFollowTheSoundOnlyWhileFramesKeepArriving() {
        allow();
        bars.acquire();
        assertTrue(bars.listening());
        assertFalse("no frame yet: the bars keep their own motion", bars.following());

        source.tone();
        assertTrue(bars.following());
        now[0] += 400;
        assertTrue(bars.following());
        now[0] += 200;                                  // 600 ms without a frame
        assertFalse("the sound stopped coming", bars.following());
        source.tone();
        assertTrue(bars.following());
    }

    @Test
    public void theLevelsRiseWithTheSoundAndResetWhenTheListeningStops() {
        allow();
        bars.acquire();
        source.tone();
        float sum = 0;
        for (float level : bars.levels()) sum += level;
        assertTrue("some bars are up: " + sum, sum > 0.5f);

        bars.release();
        for (float level : bars.levels()) assertEquals(0f, level, 0f);
    }

    @Test
    public void everyKindOfPermissionAnswerLeavesItConsistent() {
        allow();
        bars.acquire();
        source.tone();
        shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO);   // taken away in Android's settings
        bars.refresh();
        assertFalse(bars.listening());
        assertEquals(1, source.closed);
    }

    // ---- the levels for a row of any number of bars ----------------------------------------------------

    @Test
    public void asManyBarsAsLevelsTakeEachLevelAsItIs() {
        float[] levels = new float[48];
        for (int i = 0; i < 48; i++) levels[i] = i / 47f;
        for (int i = 0; i < 48; i++) assertEquals(levels[i], SoundBars.resample(levels, i, 48), 1e-6f);
    }

    @Test
    public void fewerBarsTakeTheAverageOfTheirShare() {
        float[] levels = new float[48];
        for (int i = 0; i < 48; i++) levels[i] = i < 12 ? 1f : 0f;     // only the first quarter is up
        assertEquals(1f, SoundBars.resample(levels, 0, 4), 1e-6f);
        assertEquals(0f, SoundBars.resample(levels, 1, 4), 1e-6f);
        assertEquals(0f, SoundBars.resample(levels, 3, 4), 1e-6f);
        float[] ramp = new float[48];
        for (int i = 0; i < 48; i++) ramp[i] = i;
        assertEquals("the average of 0..11", 5.5f, SoundBars.resample(ramp, 0, 4), 1e-5f);
        assertEquals(1f, SoundBars.resample(levels, 0, 1) * 4f, 1e-5f);   // one bar: the average of all
    }

    @Test
    public void moreBarsReadBetweenTwoLevels() {
        float[] levels = {0f, 1f};
        assertEquals(0f, SoundBars.resample(levels, 0, 5), 1e-6f);
        assertEquals(0.25f, SoundBars.resample(levels, 1, 5), 1e-6f);
        assertEquals(1f, SoundBars.resample(levels, 4, 5), 1e-6f);
    }

    @Test
    public void oddInputGivesZeroNotAnError() {
        assertEquals(0f, SoundBars.resample(new float[0], 0, 4), 0f);
        assertEquals(0f, SoundBars.resample(new float[]{1f}, 0, 0), 0f);
    }
}
