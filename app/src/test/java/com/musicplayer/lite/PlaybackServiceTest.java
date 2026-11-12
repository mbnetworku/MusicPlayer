package com.musicplayer.lite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.media.AudioFocusRequest;
import android.media.AudioManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class PlaybackServiceTest {
    private PlaybackService service;
    private List<Track> tracks;
    private int notifications;

    @Before
    public void setUp() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        TestMedia.install(app);
        FakePlayback.register();
        Library library = Library.get(app);
        library.refresh();
        TestMedia.awaitLibrary(library);
        tracks = new ArrayList<>(library.tracks());
        service = Robolectric.buildService(PlaybackService.class).create().get();
        service.addListener(() -> notifications++);
    }

    @Test
    public void playsTheQueueAndMovesThroughIt() {
        service.playQueue(tracks, 1, false);
        TestMedia.idle();
        assertTrue(service.isPlaying());
        assertEquals(tracks.get(1), service.currentTrack());

        service.next(true);
        TestMedia.idle();
        assertEquals(tracks.get(2), service.currentTrack());
        assertTrue(service.isPlaying());

        service.previous();
        TestMedia.idle();
        assertEquals(tracks.get(1), service.currentTrack());
    }

    @Test
    public void pauseAndResume() {
        service.playQueue(tracks, 0, false);
        TestMedia.idle();
        assertTrue(service.isPlaying());
        service.pause();
        assertFalse(service.isPlaying());
        service.play();
        TestMedia.idle();
        assertTrue(service.isPlaying());
        service.togglePlayPause();
        assertFalse(service.isPlaying());
    }

    @Test
    public void shuffleAndRepeatChangeTheQueueModes() {
        service.playQueue(tracks, 2, false);
        TestMedia.idle();
        Track playing = service.currentTrack();
        service.setShuffle(true);
        assertTrue(service.queue().isShuffle());
        assertEquals("shuffling keeps the current song", playing, service.currentTrack());
        service.cycleRepeat();
        assertEquals(PlayQueue.REPEAT_ALL, service.queue().repeat());
    }

    @Test
    public void queueEditing() {
        service.playQueue(tracks, 0, false);
        TestMedia.idle();
        int before = service.queue().size();
        List<Track> extra = new ArrayList<>();
        extra.add(tracks.get(4));
        service.enqueue(extra);
        assertEquals(before + 1, service.queue().size());
        service.removeFromQueue(before);
        assertEquals(before, service.queue().size());
        service.skipToQueueItem(3);
        TestMedia.idle();
        assertEquals(tracks.get(3), service.currentTrack());
    }

    @Test
    public void listenersAreToldAboutChanges() {
        int start = notifications;
        service.playQueue(tracks, 0, false);
        TestMedia.idle();
        assertTrue(notifications > start);
    }

    @Test
    public void exposesEqualizerControlsWithoutCrashing() {
        EffectsController fx = service.effects();
        assertNotNull(fx);
        // The fake audio stack may not provide an equalizer; the controller must cope either way.
        fx.setEnabled(true);
        fx.setBass(500);
        fx.setLoudness(300);
        fx.reset();
        assertEquals(0, fx.bass());
        assertTrue(service.audioSession() >= 0);
    }

    @Test
    public void emptyServiceHasNothingToPlay() {
        TestMedia.resetSingletons();
        service = Robolectric.buildService(PlaybackService.class).create().get();
        TestMedia.idle();
        assertNull(service.currentTrack());
        assertFalse(service.isPlaying());
        service.play();
        service.next(true);
        service.previous();
        assertNull(service.currentTrack());
    }

    // ---- the sound being refused or delayed ---------------------------------------------------------

    private AudioManager audio() {
        return RuntimeEnvironment.getApplication().getSystemService(AudioManager.class);
    }

    @Test
    public void playsEvenWhenAndroidRefusesTheSound() {
        // Some car stereos refuse every request for the sound, even when nothing else is playing.
        shadowOf(audio()).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED);
        service.playQueue(tracks, 0, false);
        TestMedia.idle();
        assertTrue("the user pressed play, so the song plays", service.isPlaying());
    }

    @Test
    public void whenAPhoneCallHasTheSoundItWaitsAndPlaysWhenAndroidGivesItBack() throws Exception {
        shadowOf(audio()).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_DELAYED);
        service.playQueue(tracks, 0, false);
        TestMedia.idle();
        assertFalse("not while the call has the sound", service.isPlaying());
        assertNotNull("but the song is chosen and waiting", service.currentTrack());

        java.lang.reflect.Field f = PlaybackService.class.getDeclaredField("focusListener");
        f.setAccessible(true);
        ((AudioManager.OnAudioFocusChangeListener) f.get(service)).onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN);
        TestMedia.idle();
        assertTrue("the call ended: the song starts by itself", service.isPlaying());
    }

    @Test
    public void theRequestForTheSoundAcceptsToWait() {
        service.playQueue(tracks, 0, false);
        TestMedia.idle();
        AudioFocusRequest request = shadowOf(audio()).getLastAudioFocusRequest().audioFocusRequest;
        assertNotNull(request);
        assertTrue(request.acceptsDelayedFocusGain());
    }
}
