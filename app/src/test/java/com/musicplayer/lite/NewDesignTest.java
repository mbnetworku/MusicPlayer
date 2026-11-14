package com.musicplayer.lite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.ArrayList;

/**
 * The New design (iOS Liquid Glass look) and the choice between it and Classic: the setting, the
 * four tabs, the slim bars, the blue accent, rounder shapes, and the playing screen that is
 * chosen on its own.
 */
@RunWith(RobolectricTestRunner.class)
public class NewDesignTest {
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

    // ---- the setting -------------------------------------------------------------------------------

    @Test
    public void classicIsTheDefaultForTheAppAndForThePlayingScreen() {
        assertEquals(Design.CLASSIC, prefs.design());
        assertEquals(Design.CLASSIC, prefs.playerDesign());
        assertFalse(Design.isNew(app));
        assertFalse(Design.playerNew(app));
    }

    @Test
    public void settingsOffersBothDesignsAndSavesTheChoices() {
        SettingsActivity a = open(SettingsActivity.class).get();
        RadioGroup app = a.findViewById(R.id.design_group);
        RadioGroup player = a.findViewById(R.id.player_design_group);
        assertEquals(R.id.design_classic, app.getCheckedRadioButtonId());
        assertEquals(R.id.player_design_classic, player.getCheckedRadioButtonId());

        app.check(R.id.design_new);
        assertEquals(Design.NEW, prefs.design());
        assertEquals("the playing screen is chosen on its own", Design.CLASSIC, prefs.playerDesign());

        SettingsActivity b = open(SettingsActivity.class).get();
        ((RadioGroup) b.findViewById(R.id.player_design_group)).check(R.id.player_design_new);
        assertEquals(Design.NEW, prefs.playerDesign());
        assertEquals(Design.NEW, prefs.design());
    }

    @Test
    public void changingTheDesignRebuildsAnOpenScreen() {
        ActivityController<LibraryActivity> c = open(LibraryActivity.class);
        assertNotNull(c.get().findViewById(R.id.tab_artists));
        int before = Appearance.signature(c.get());
        prefs.setDesign(Design.NEW);
        assertTrue("an open screen can tell that the look changed", Appearance.signature(c.get()) != before);
    }

    @Test
    public void theNewDesignIsAlwaysGlassEvenWithTheClassicSwitchOff() {
        prefs.setGlass(false);
        assertFalse(Appearance.glass(app));
        prefs.setDesign(Design.NEW);
        assertTrue(Appearance.glass(app));
    }

    // ---- the library ---------------------------------------------------------------------------------

    @Test
    public void classicKeepsAllFiveTabs() {
        LibraryActivity a = open(LibraryActivity.class).get();
        for (int id : new int[]{R.id.tab_songs, R.id.tab_folders, R.id.tab_albums, R.id.tab_artists, R.id.tab_favorites}) {
            assertNotNull(a.findViewById(id));
        }
    }

    @Test
    public void theNewDesignHasFourTabsAndNoArtists() {
        prefs.setDesign(Design.NEW);
        LibraryActivity a = open(LibraryActivity.class).get();
        for (int id : new int[]{R.id.tab_songs, R.id.tab_folders, R.id.tab_albums, R.id.tab_favorites}) {
            assertNotNull(a.findViewById(id));
        }
        assertNull(a.findViewById(R.id.tab_artists));
        assertNull("the search button is in the top pill, not next to the mini player", a.findViewById(R.id.btn_search_round));
    }

    @Test
    public void aSavedArtistsTabOpensSongsInTheNewDesign() {
        prefs.setLastTab(3);   // Artists
        prefs.setDesign(Design.NEW);
        LibraryActivity a = open(LibraryActivity.class).get();
        assertTrue(a.findViewById(R.id.tab_songs).isSelected());
        assertEquals("Songs", ((TextView) a.findViewById(R.id.title)).getText().toString());
    }

    @Test
    public void everyNewTabOpensItsList() {
        prefs.setDesign(Design.NEW);
        LibraryActivity a = open(LibraryActivity.class).get();
        for (int id : new int[]{R.id.tab_folders, R.id.tab_albums, R.id.tab_favorites, R.id.tab_songs}) {
            a.findViewById(id).performClick();
            TestMedia.idle();
            assertTrue(a.findViewById(id).isSelected());
        }
    }

    @Test
    public void theNewTabBarIsASlimGlassCapsuleWithAPillOnTheChosenTab() {
        prefs.setDesign(Design.NEW);
        LibraryActivity a = open(LibraryActivity.class).get();
        View tabs = a.findViewById(R.id.tabs);
        assertTrue(tabs.getBackground() instanceof GlassDrawable);
        View songs = a.findViewById(R.id.tab_songs);
        songs.setSelected(true);
        assertTrue("the chosen tab is a rounded pill", songs.getBackground().getCurrent() instanceof GradientDrawable);
        float dp = a.getResources().getDisplayMetrics().density;
        assertEquals(46f * dp, songs.getLayoutParams().height, 0.5f);
    }

    @Test
    public void theNewMiniPlayerIsASlimerCapsule() {
        ActivityController<LibraryActivity> classic = open(LibraryActivity.class);
        View classicProgress = classic.get().findViewById(R.id.mini_progress);
        assertEquals(View.VISIBLE, classicProgress.getVisibility());
        classic.pause().stop().destroy();

        prefs.setDesign(Design.NEW);
        LibraryActivity a = open(LibraryActivity.class).get();
        View mini = a.findViewById(R.id.mini);
        assertEquals(View.VISIBLE, mini.getVisibility());
        assertEquals(View.GONE, a.findViewById(R.id.mini_progress).getVisibility());
        assertTrue(a.findViewById(R.id.mini_body).getBackground() instanceof GlassDrawable);
        assertFalse(((TextView) a.findViewById(R.id.mini_title)).getText().toString().isEmpty());
    }

    @Test
    public void theAccentIsBlueInTheNewDesignAndRedInClassic() {
        LibraryActivity classic = open(LibraryActivity.class).get();
        assertEquals(classic.getColor(R.color.accent_text), Design.accent(classic));
        prefs.setDesign(Design.NEW);
        LibraryActivity a = open(LibraryActivity.class).get();
        assertEquals(a.getColor(R.color.n_accent), Design.accent(a));
        TextView songsTab = a.findViewById(R.id.tab_songs);
        assertEquals("the chosen tab is blue", a.getColor(R.color.n_accent),
                songsTab.getTextColors().getColorForState(new int[]{android.R.attr.state_selected}, 0));
        assertEquals("the others are plain white or black", a.getColor(R.color.text_primary),
                songsTab.getTextColors().getDefaultColor());
    }

    @Test
    public void foldersGetTheTwoLayerIconInTheNewDesign() {
        prefs.setDesign(Design.NEW);
        LibraryActivity a = open(LibraryActivity.class).get();
        a.findViewById(R.id.tab_folders).performClick();
        TestMedia.idle();
        ListView list = a.findViewById(R.id.list);
        boolean sawFolder = false;
        for (int i = 0; i < list.getAdapter().getCount(); i++) {
            RowAdapter.Row row = (RowAdapter.Row) list.getAdapter().getItem(i);
            if (row.type != RowAdapter.FOLDER) continue;
            View v = list.getAdapter().getView(i, null, list);
            ImageView art = v.findViewById(R.id.row_art);
            assertNotNull(art.getImageTintList());
            assertEquals("one plain colour, not the accent",
                    a.getColor(R.color.text_primary), art.getImageTintList().getDefaultColor());
            sawFolder = true;
        }
        assertTrue(sawFolder);
    }

    @Test
    public void everyOtherScreenOpensInTheNewDesignInBothLooks() {
        for (int mode : new int[]{Appearance.DARK, Appearance.LIGHT}) {
            prefs.setThemeMode(mode);
            prefs.setDesign(Design.NEW);
            prefs.setPlayerDesign(Design.NEW);
            for (Class<? extends android.app.Activity> screen : new Class[]{LibraryActivity.class, PlayerActivity.class,
                    QueueActivity.class, EqualizerActivity.class, SettingsActivity.class}) {
                ActivityController<? extends android.app.Activity> c = open(screen);
                assertNotNull(c.get().findViewById(android.R.id.content));
                c.pause().stop().destroy();
            }
        }
    }

    // ---- the playing screen -----------------------------------------------------------------------------

    @Test
    public void theNewPlayingScreenShowsTheVolumeEvenWhereClassicHidesIt() {
        PlayerActivity classic = open(PlayerActivity.class).get();
        assertEquals("a small phone: Classic hides the volume", View.GONE,
                classic.findViewById(R.id.np_volume_row).getVisibility());

        prefs.setPlayerDesign(Design.NEW);
        PlayerActivity a = open(PlayerActivity.class).get();
        assertEquals(View.VISIBLE, a.findViewById(R.id.np_volume_row).getVisibility());
        assertNotNull(a.findViewById(R.id.np_volume));
    }

    @Test
    public void thePlayingScreenIsChosenOnItsOwn() {
        prefs.setDesign(Design.NEW);   // the app is New, the playing screen stays Classic
        PlayerActivity a = open(PlayerActivity.class).get();
        assertEquals(View.GONE, a.findViewById(R.id.np_volume_row).getVisibility());

        prefs.setDesign(Design.CLASSIC);
        prefs.setPlayerDesign(Design.NEW);   // the other way round
        PlayerActivity b = open(PlayerActivity.class).get();
        assertEquals(View.VISIBLE, b.findViewById(R.id.np_volume_row).getVisibility());
        LibraryActivity lib = open(LibraryActivity.class).get();
        assertNotNull("the library is still Classic", lib.findViewById(R.id.tab_artists));
    }

    @Test
    public void theNewPlayingScreenHasGlassHeartAndMoreButtons() {
        prefs.setPlayerDesign(Design.NEW);
        PlayerActivity a = open(PlayerActivity.class).get();
        assertTrue(a.findViewById(R.id.np_fav).getBackground() instanceof GlassDrawable);
        assertTrue(a.findViewById(R.id.np_more).getBackground() instanceof GlassDrawable);
        a.findViewById(R.id.np_fav).performClick();   // still the same heart
        assertFalse("tapping the heart still saves the favorite", prefs.favorites().isEmpty());
    }

    // ---- the car ----------------------------------------------------------------------------------------------

    @Test
    @Config(qualifiers = "w900dp-h500dp-land")
    public void theNewCarScreenHasARoundedPlayerPanelFourTabsAndTheVolume() {
        prefs.setDesign(Design.NEW);
        prefs.setPlayerDesign(Design.NEW);
        LibraryActivity a = open(LibraryActivity.class).get();
        assertNull("the player is always on screen", a.findViewById(R.id.mini));
        assertNotNull(a.findViewById(R.id.np));
        assertNull(a.findViewById(R.id.tab_artists));
        assertNotNull(a.findViewById(R.id.tab_favorites));
        assertEquals("the volume is shown in a car in the New playing screen", View.VISIBLE,
                a.findViewById(R.id.np_volume_row).getVisibility());
        View panel = (View) a.findViewById(R.id.np).getParent();
        assertTrue("a rounded glass card", panel.getBackground() instanceof GlassDrawable);
    }

    @Test
    @Config(qualifiers = "w900dp-h500dp-land")
    public void theClassicCarScreenIsUnchanged() {
        LibraryActivity a = open(LibraryActivity.class).get();
        assertNotNull(a.findViewById(R.id.tab_artists));
        assertEquals("a car has its own volume knob in Classic", View.GONE,
                a.findViewById(R.id.np_volume_row).getVisibility());
    }

    // ---- the redesigned Settings ----------------------------------------------------------------------------

    @Test
    public void theNewSettingsHasEveryChoiceOfTheClassicOneExceptTheGlassSwitch() {
        SettingsActivity classic = open(SettingsActivity.class).get();
        assertNotNull(classic.findViewById(R.id.row_glass));
        prefs.setDesign(Design.NEW);
        SettingsActivity a = open(SettingsActivity.class).get();
        assertNull("the New design is always glass, so there is no switch for it", a.findViewById(R.id.row_glass));
        for (int id : new int[]{R.id.design_group, R.id.player_design_group, R.id.theme_group, R.id.clarity_group,
                R.id.row_ambient, R.id.bg_group, R.id.bar_style_group, R.id.row_vibrate, R.id.row_vibrate_playing,
                R.id.row_cover_tap, R.id.row_show_all, R.id.row_scan, R.id.row_version, R.id.row_auto,
                R.id.upd_action, R.id.about_text, R.id.about_source, R.id.settings_scroll}) {
            assertNotNull("missing in the New settings: " + a.getResources().getResourceEntryName(id), a.findViewById(id));
        }
    }

    @Test
    public void theNewSettingsChoicesStillWork() {
        prefs.setDesign(Design.NEW);
        SettingsActivity a = open(SettingsActivity.class).get();
        ((RadioGroup) a.findViewById(R.id.clarity_group)).check(R.id.clarity_frosted);
        assertEquals(2, prefs.glassClarity());
        SettingsActivity b = open(SettingsActivity.class).get();
        ((RadioGroup) b.findViewById(R.id.theme_group)).check(R.id.theme_dark);
        assertEquals(Appearance.DARK, prefs.themeMode());
        SettingsActivity c = open(SettingsActivity.class).get();
        ((RadioGroup) c.findViewById(R.id.bar_style_group)).check(R.id.bar_style_none);
        assertEquals(EqBarsView.STYLE_NONE, prefs.barStyle());
        SettingsActivity d = open(SettingsActivity.class).get();
        d.findViewById(R.id.row_vibrate).performClick();   // a whole row is a switch
        assertFalse(prefs.vibrate());
    }

    @Test
    public void theNewSettingsOpensInBothLooksAndBothSizes() {
        prefs.setDesign(Design.NEW);
        for (int mode : new int[]{Appearance.DARK, Appearance.LIGHT}) {
            prefs.setThemeMode(mode);
            ActivityController<SettingsActivity> c = open(SettingsActivity.class);
            assertNotNull(c.get().findViewById(R.id.settings_scroll));
            c.pause().stop().destroy();
        }
    }
}
