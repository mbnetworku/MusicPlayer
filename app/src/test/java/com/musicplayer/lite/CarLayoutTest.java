package com.musicplayer.lite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * The car head unit and tablet landscape layouts are separate files from the phone ones, so they
 * need their own look: the player next to the list, with the heart and the moving bars.
 */
@RunWith(RobolectricTestRunner.class)
@Config(qualifiers = "w900dp-h500dp-land")
public class CarLayoutTest {
    private Application app;
    private PlaybackService service;

    @Before
    public void setUp() throws Exception {
        app = RuntimeEnvironment.getApplication();
        TestMedia.install(app);
        FakePlayback.register();
        Library library = Library.get(app);
        library.refresh();
        TestMedia.awaitLibrary(library);
        service = Robolectric.buildService(PlaybackService.class).create().get();
        TestMedia.exposeService(app, service);
        service.playQueue(library.tracks(), 0, false);
        TestMedia.idle();
    }

    private ActivityController<LibraryActivity> openLibrary() {
        ActivityController<LibraryActivity> c =
                Robolectric.buildActivity(LibraryActivity.class).create().start().resume().visible();
        TestMedia.idle();
        return c;
    }

    @Test
    public void thePlayerSitsNextToTheListWithTheHeartAndTheBars() {
        ActivityController<LibraryActivity> c = openLibrary();
        LibraryActivity a = c.get();
        assertNull("the car layout has no mini player: the player is always on screen", a.findViewById(R.id.mini));
        assertNotNull(a.findViewById(R.id.np));

        assertEquals(View.VISIBLE, a.findViewById(R.id.np_fav).getVisibility());
        EqBarsView over = a.findViewById(R.id.np_bars);
        assertEquals(View.VISIBLE, over.getVisibility());
        assertTrue(over.isPlaying());
        assertTrue("moving on a car screen too", over.isRunning());
        assertTrue(((EqBarsView) a.findViewById(R.id.btn_eq)).isPlaying());
        c.pause().stop().destroy();
    }

    @Test
    public void theBarsOverTheCoverAreExactlyAsWideAsTheCover() {
        ActivityController<LibraryActivity> c = openLibrary();
        LibraryActivity a = c.get();
        // A landscape panel is wider than the square cover that fits it: the bars must not make the
        // cover's box wider than the cover.
        View content = a.findViewById(android.R.id.content);
        content.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY));
        content.layout(0, 0, 1280, 720);
        View art = a.findViewById(R.id.np_art);
        View bars = a.findViewById(R.id.np_bars);
        assertTrue("the cover is laid out", art.getWidth() > 0);
        assertEquals(art.getWidth(), bars.getWidth());
        assertEquals("and it is a square", art.getWidth(), art.getHeight());
        c.pause().stop().destroy();
    }

    @Test
    public void allFiveTabsAreThere() {
        ActivityController<LibraryActivity> c = openLibrary();
        LibraryActivity a = c.get();
        assertEquals(5, ((LinearLayout) a.findViewById(R.id.tabs)).getChildCount());
        a.findViewById(R.id.tab_favorites).performClick();
        assertEquals("Favorites", ((TextView) a.findViewById(R.id.title)).getText().toString());
        assertEquals(View.VISIBLE, a.findViewById(R.id.empty).getVisibility());
        c.pause().stop().destroy();
    }

    @Test
    public void theHeartInThePlayerPanelFillsTheFavoritesTab() {
        ActivityController<LibraryActivity> c = openLibrary();
        LibraryActivity a = c.get();
        a.findViewById(R.id.tab_favorites).performClick();
        a.findViewById(R.id.np_fav).performClick();
        assertTrue(Favorites.get(app).contains(service.currentTrack()));

        ListView list = a.findViewById(R.id.list);
        assertEquals("the tab updates by itself", 2, list.getAdapter().getCount());
        assertEquals(service.currentTrack().title, ((RowAdapter.Row) list.getAdapter().getItem(1)).title);

        a.findViewById(R.id.np_fav).performClick();
        assertEquals(View.VISIBLE, a.findViewById(R.id.empty).getVisibility());
        c.pause().stop().destroy();
    }

    @Test
    public void pausingStopsTheBarsOnTheCarScreen() {
        ActivityController<LibraryActivity> c = openLibrary();
        LibraryActivity a = c.get();
        EqBarsView over = a.findViewById(R.id.np_bars);
        service.togglePlayPause();
        TestMedia.idle();
        assertFalse(over.isPlaying());
        c.pause().stop().destroy();
    }

    @Test
    public void theEqualizerScreenHasItsBarsInTheCarLayoutToo() {
        ActivityController<EqualizerActivity> c =
                Robolectric.buildActivity(EqualizerActivity.class).create().start().resume().visible();
        TestMedia.idle();
        EqBarsView bars = c.get().findViewById(R.id.eq_bars);
        assertNotNull("this layout is a separate file", bars);
        assertTrue(bars.isPlaying());
        c.pause().stop().destroy();
    }

    @Test
    public void everyOtherScreenOpensInTheCarLayout() {
        for (Class<? extends Activity> screen : new Class[]{QueueActivity.class, SettingsActivity.class}) {
            ActivityController<? extends Activity> c =
                    Robolectric.buildActivity(screen).create().start().resume().visible();
            TestMedia.idle();
            assertNotNull(screen.getSimpleName(), c.get().findViewById(android.R.id.content));
            c.pause().stop().destroy();
        }
    }
}
