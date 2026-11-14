package com.musicplayer.lite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.view.View;
import android.widget.ListView;
import android.widget.RadioGroup;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;

import java.util.ArrayList;

/** "None" for the equalizer design: no moving bars anywhere, and the Equalizer button stays. */
@RunWith(RobolectricTestRunner.class)
public class NoEqualizerTest {
    private Application app;
    private Prefs prefs;

    @Before
    public void setUp() throws Exception {
        app = RuntimeEnvironment.getApplication();
        TestMedia.install(app);
        FakePlayback.register();
        EqBarsView.resetClock();
        prefs = Prefs.get(app);
        Library library = Library.get(app);
        library.refresh();
        TestMedia.awaitLibrary(library);
        PlaybackService service = Robolectric.buildService(PlaybackService.class).create().get();
        TestMedia.exposeService(app, service);
        service.playQueue(new ArrayList<>(library.tracks()), 1, false);
        TestMedia.idle();
    }

    private <T extends android.app.Activity> ActivityController<T> open(Class<T> cls) {
        ActivityController<T> c = Robolectric.buildActivity(cls).create().start().resume().visible();
        TestMedia.idle();
        return c;
    }

    @Test
    public void noneIsAFifthChoiceThatIsSaved() {
        prefs.setBarStyle(EqBarsView.STYLE_NONE);
        assertEquals(EqBarsView.STYLE_NONE, prefs.barStyle());
        prefs.setBarStyle(5);
        assertEquals("a bad value still falls back to bars", 0, prefs.barStyle());
    }

    @Test
    public void settingsOffersNoneInBothDesigns() {
        for (int design : new int[]{Design.CLASSIC, Design.NEW}) {
            prefs.setDesign(design);
            prefs.setBarStyle(0);
            SettingsActivity a = open(SettingsActivity.class).get();
            RadioGroup g = a.findViewById(R.id.bar_style_group);
            assertEquals(5, g.getChildCount());
            g.check(R.id.bar_style_none);
            assertEquals(EqBarsView.STYLE_NONE, prefs.barStyle());
            g.check(R.id.bar_style_wave);
            assertEquals(EqBarsView.STYLE_WAVE, prefs.barStyle());
        }
    }

    @Test
    public void theCoverBarsStayAwayWhilePlaying() {
        prefs.setBarStyle(EqBarsView.STYLE_NONE);
        PlayerActivity a = open(PlayerActivity.class).get();
        assertEquals(View.GONE, a.findViewById(R.id.np_bars).getVisibility());
        a.findViewById(R.id.np_play).performClick();   // pause, then play: still nothing
        TestMedia.idle();
        assertEquals(View.GONE, a.findViewById(R.id.np_bars).getVisibility());
    }

    @Test
    public void theCoverBarsComeBackWhenTheChoiceIsBars() {
        prefs.setBarStyle(0);
        PlayerActivity a = open(PlayerActivity.class).get();
        assertEquals(View.VISIBLE, a.findViewById(R.id.np_bars).getVisibility());
    }

    @Test
    public void theMiniPlayerAndTheCurrentRowHaveNoBars() {
        prefs.setBarStyle(EqBarsView.STYLE_NONE);
        LibraryActivity a = open(LibraryActivity.class).get();
        assertEquals(View.GONE, a.findViewById(R.id.mini_bars).getVisibility());

        ListView list = a.findViewById(R.id.list);
        boolean sawCurrent = false;
        for (int i = 0; i < list.getAdapter().getCount(); i++) {
            RowAdapter.Row row = (RowAdapter.Row) list.getAdapter().getItem(i);
            if (row.type != RowAdapter.TRACK) continue;
            View v = list.getAdapter().getView(i, null, list);
            assertEquals("no bars over any cover", View.GONE, v.findViewById(R.id.row_playing).getVisibility());
            sawCurrent = true;
        }
        assertTrue(sawCurrent);
    }

    @Test
    public void theMiniPlayerAndTheCurrentRowShowBarsWhenTheChoiceIsBars() {
        prefs.setBarStyle(0);
        LibraryActivity a = open(LibraryActivity.class).get();
        assertEquals(View.VISIBLE, a.findViewById(R.id.mini_bars).getVisibility());
    }

    @Test
    public void theNewMiniPlayerHasNoBarsEither() {
        prefs.setDesign(Design.NEW);
        prefs.setBarStyle(EqBarsView.STYLE_NONE);
        LibraryActivity a = open(LibraryActivity.class).get();
        assertEquals(View.GONE, a.findViewById(R.id.mini_bars).getVisibility());
    }

    @Test
    public void theEqualizerButtonStaysAndStandsStill() {
        prefs.setBarStyle(EqBarsView.STYLE_NONE);
        LibraryActivity a = open(LibraryActivity.class).get();
        EqBarsView button = a.findViewById(R.id.btn_eq);
        assertEquals("the button is still there to open the equalizer", View.VISIBLE, button.getVisibility());
        button.setPlaying(true);
        assertFalse("it does not move", button.isRunning());
        assertNotNull(a.findViewById(R.id.btn_eq));
    }

    @Test
    public void theEqualizerScreenHidesItsMovingBarsButKeepsTheSliders() {
        prefs.setBarStyle(EqBarsView.STYLE_NONE);
        EqualizerActivity a = open(EqualizerActivity.class).get();
        assertEquals(View.GONE, a.findViewById(R.id.eq_bars).getVisibility());
        assertNotNull(a.findViewById(R.id.eq_switch));
    }

    @Test
    public void changingTheChoiceAtRunTimeHidesAndShowsTheBars() {
        prefs.setBarStyle(0);
        LibraryActivity a = open(LibraryActivity.class).get();
        EqBarsView mini = a.findViewById(R.id.mini_bars);
        assertEquals(View.VISIBLE, mini.getVisibility());
        mini.setStyle(EqBarsView.STYLE_NONE);
        assertEquals(View.GONE, mini.getVisibility());
        mini.setVisibility(View.VISIBLE);
        assertEquals("asking to show it does not help while the choice is None", View.GONE, mini.getVisibility());
    }
}
