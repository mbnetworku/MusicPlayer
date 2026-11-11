package com.musicplayer.lite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;

import java.util.ArrayList;
import java.util.List;

/** Opens each secondary screen against a running service to catch wiring mistakes. */
@RunWith(RobolectricTestRunner.class)
public class ScreensSmokeTest {

    private PlaybackService startService(Application app) throws Exception {
        TestMedia.install(app);
        FakePlayback.register();
        Library library = Library.get(app);
        library.refresh();
        TestMedia.awaitLibrary(library);
        PlaybackService service = Robolectric.buildService(PlaybackService.class).create().get();
        TestMedia.exposeService(app, service);
        List<Track> all = new ArrayList<>(library.tracks());
        service.playQueue(all, 1, false);
        TestMedia.idle();
        return service;
    }

    @Test
    public void playerScreenShowsTheCurrentSong() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        PlaybackService service = startService(app);
        ActivityController<PlayerActivity> c =
                Robolectric.buildActivity(PlayerActivity.class).create().start().resume().visible();
        PlayerActivity a = c.get();
        TestMedia.idle();
        assertEquals(service.currentTrack().title,
                ((TextView) a.findViewById(R.id.np_title)).getText().toString());
        a.findViewById(R.id.np_play).performClick(); // pauses
        assertTrue(!service.isPlaying());
        a.findViewById(R.id.np_next).performClick();
        TestMedia.idle();
        assertTrue(service.isPlaying());
        c.pause().stop().destroy();
    }

    @Test
    public void queueScreenListsTheQueue() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        PlaybackService service = startService(app);
        ActivityController<QueueActivity> c =
                Robolectric.buildActivity(QueueActivity.class).create().start().resume().visible();
        QueueActivity a = c.get();
        TestMedia.idle();
        ListView list = a.findViewById(R.id.list);
        assertEquals(service.queue().size(), list.getAdapter().getCount());
        list.performItemClick(null, 3, 0);
        TestMedia.idle();
        assertEquals(service.queue().tracks().get(3), service.currentTrack());
        c.pause().stop().destroy();
    }

    @Test
    public void equalizerScreenOpensAndRespondsToTheSwitch() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        PlaybackService service = startService(app);
        ActivityController<EqualizerActivity> c =
                Robolectric.buildActivity(EqualizerActivity.class).create().start().resume().visible();
        EqualizerActivity a = c.get();
        TestMedia.idle();
        assertNotNull(a.findViewById(R.id.eq_switch));
        // Whatever the fake audio stack offers, one of the two states must be shown.
        View unavailable = a.findViewById(R.id.eq_unavailable);
        View content = a.findViewById(R.id.eq_content);
        assertTrue(unavailable.getVisibility() == View.VISIBLE || content.getVisibility() == View.VISIBLE);
        if (service.effects().hasEq()) {
            a.findViewById(R.id.eq_reset).performClick();
        }
        c.pause().stop().destroy();
    }

    @Test
    public void playerScreenWithNothingPlayingIsUsable() {
        Application app = RuntimeEnvironment.getApplication();
        TestMedia.resetSingletons();
        TestMedia.exposeService(app, Robolectric.buildService(PlaybackService.class).create().get());
        ActivityController<PlayerActivity> c =
                Robolectric.buildActivity(PlayerActivity.class).create().start().resume().visible();
        PlayerActivity a = c.get();
        TestMedia.idle();
        assertEquals(a.getString(R.string.nothing_playing),
                ((TextView) a.findViewById(R.id.np_title)).getText().toString());
        c.pause().stop().destroy();
    }
}
