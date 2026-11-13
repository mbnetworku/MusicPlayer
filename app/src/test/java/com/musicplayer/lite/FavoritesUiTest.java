package com.musicplayer.lite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.content.Intent;
import android.os.Build;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
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
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowToast;
import org.robolectric.util.ReflectionHelpers;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The heart, the Favorites tab and the moving bars, as the user meets them on the real screens. */
@RunWith(RobolectricTestRunner.class)
public class FavoritesUiTest {
    private Application app;
    private int realSdk;

    @Before
    public void setUp() {
        TestMedia.resetSingletons();
        app = RuntimeEnvironment.getApplication();
        realSdk = Build.VERSION.SDK_INT;
    }

    @After
    public void restoreSdk() {
        ReflectionHelpers.setStaticField(Build.VERSION.class, "SDK_INT", realSdk);
    }

    // ---- helpers --------------------------------------------------------------------------------------

    private ActivityController<LibraryActivity> openLibrary() throws Exception {
        TestMedia.install(app);
        ActivityController<LibraryActivity> c =
                Robolectric.buildActivity(LibraryActivity.class).create().start().resume().visible();
        TestMedia.awaitLibrary(Library.get(app));
        return c;
    }

    private PlaybackService startService() {
        PlaybackService service = Robolectric.buildService(PlaybackService.class).create().get();
        TestMedia.exposeService(app, service);
        FakePlayback.register();
        return service;
    }

    private static ListView list(LibraryActivity a) {
        return a.findViewById(R.id.list);
    }

    private static RowAdapter.Row row(ListView list, int position) {
        return (RowAdapter.Row) list.getAdapter().getItem(position);
    }

    private static View sheetItem(String label) {
        LinearLayout items = ShadowDialog.getLatestDialog().findViewById(R.id.gd_items);
        for (int i = 0; i < items.getChildCount(); i++) {
            TextView text = items.getChildAt(i).findViewById(R.id.gd_item_label);
            if (label.contentEquals(text.getText())) return items.getChildAt(i);
        }
        return null;
    }

    /** Opens the "…" menu of the song in this row of the Songs list. */
    private static void openMenu(ListView list, int position) {
        list.getAdapter().getView(position, null, list).findViewById(R.id.row_more).performClick();
    }

    private Track songNamed(String title) {
        for (Track t : Library.get(app).tracks()) {
            if (t.title.equals(title)) return t;
        }
        throw new AssertionError("no song " + title);
    }

    // ---- the Favorites tab ----------------------------------------------------------------------------

    @Test
    public void theTabBarHasAFavoritesTab() throws Exception {
        ActivityController<LibraryActivity> c = openLibrary();
        LibraryActivity a = c.get();
        TextView tab = a.findViewById(R.id.tab_favorites);
        assertEquals("Favorites", tab.getText().toString());
        assertEquals("right after Artists", 4, ((LinearLayout) a.findViewById(R.id.tabs)).indexOfChild(tab));
        assertEquals(5, ((LinearLayout) a.findViewById(R.id.tabs)).getChildCount());
        c.pause().stop().destroy();
    }

    @Test
    public void withNoFavoritesTheTabSaysHowToAddOne() throws Exception {
        ActivityController<LibraryActivity> c = openLibrary();
        LibraryActivity a = c.get();
        a.findViewById(R.id.tab_favorites).performClick();

        assertEquals("Favorites", ((TextView) a.findViewById(R.id.title)).getText().toString());
        assertTrue(a.findViewById(R.id.tab_favorites).isSelected());
        assertEquals(View.VISIBLE, a.findViewById(R.id.empty).getVisibility());
        assertEquals(View.GONE, list(a).getVisibility());
        assertEquals(app.getString(R.string.empty_fav_title),
                ((TextView) a.findViewById(R.id.empty_title)).getText().toString());
        assertEquals(app.getString(R.string.empty_fav_body),
                ((TextView) a.findViewById(R.id.empty_body)).getText().toString());
        assertEquals("nothing to press: it is explained, not a button", View.GONE,
                a.findViewById(R.id.empty_action).getVisibility());
        assertEquals("", ((TextView) a.findViewById(R.id.subtitle)).getText().toString());

        // Back on Songs, the usual empty screen logic is untouched.
        a.findViewById(R.id.tab_songs).performClick();
        assertEquals(View.VISIBLE, list(a).getVisibility());
        assertEquals(View.GONE, a.findViewById(R.id.empty).getVisibility());
        c.pause().stop().destroy();
    }

    @Test
    public void theMenuAddsASongToFavoritesAndTheTabShowsItNewestFirst() throws Exception {
        ActivityController<LibraryActivity> c = openLibrary();
        LibraryActivity a = c.get();
        ListView list = list(a);

        openMenu(list, TestMedia.songPos(list, 0));                                   // Ember
        View add = sheetItem("Add to Favorites");
        assertNotNull(add);
        add.performClick();
        assertEquals("Added to Favorites", ShadowToast.getTextOfLatestToast());
        assertTrue(Favorites.get(app).contains(songNamed("Ember")));

        openMenu(list, TestMedia.songPos(list, 1));                                   // Glass Cities
        sheetItem("Add to Favorites").performClick();

        a.findViewById(R.id.tab_favorites).performClick();
        assertEquals(View.VISIBLE, list.getVisibility());
        assertEquals(View.GONE, a.findViewById(R.id.empty).getVisibility());
        assertEquals("a header and two songs", 3, list.getAdapter().getCount());
        assertEquals(RowAdapter.HEADER, row(list, 0).type);
        assertTrue("Play and Shuffle are offered", row(list, 0).actions);
        assertEquals("2 songs", row(list, 0).sub);
        assertEquals("the one added last is on top", "Glass Cities", row(list, 1).title);
        assertEquals("Ember", row(list, 2).title);
        assertEquals("the header row says how many, so there is no second count up here", "",
                ((TextView) a.findViewById(R.id.subtitle)).getText().toString());
        assertEquals("no alphabet bubble: the order is not alphabetical", 0, ((RowAdapter) list.getAdapter()).getSections().length);
        c.pause().stop().destroy();
    }

    @Test
    public void theMenuOffersRemoveForASongThatIsAlreadyAFavorite() throws Exception {
        ActivityController<LibraryActivity> c = openLibrary();
        LibraryActivity a = c.get();
        ListView list = list(a);
        Favorites.get(app).toggle(songNamed("Ember"));

        openMenu(list, TestMedia.songPos(list, 0));
        assertNull("it is already one", sheetItem("Add to Favorites"));
        sheetItem("Remove from Favorites").performClick();
        assertEquals("Removed from Favorites", ShadowToast.getTextOfLatestToast());
        assertFalse(Favorites.get(app).contains(songNamed("Ember")));
        c.pause().stop().destroy();
    }

    @Test
    public void takingTheLastFavoriteOffTheTabShowsTheEmptyMessageAgain() throws Exception {
        ActivityController<LibraryActivity> c = openLibrary();
        LibraryActivity a = c.get();
        Favorites.get(app).toggle(songNamed("Ember"));
        a.findViewById(R.id.tab_favorites).performClick();
        assertEquals(View.VISIBLE, list(a).getVisibility());

        openMenu(list(a), 1);                                 // row 0 is the header; row 1 is Ember
        sheetItem("Remove from Favorites").performClick();
        assertEquals(View.GONE, list(a).getVisibility());
        assertEquals(View.VISIBLE, a.findViewById(R.id.empty).getVisibility());
        c.pause().stop().destroy();
    }

    @Test
    public void anAlbumMenuCanAddAllItsSongsAtOnce() throws Exception {
        ActivityController<LibraryActivity> c = openLibrary();
        LibraryActivity a = c.get();
        a.findViewById(R.id.tab_albums).performClick();
        ListView list = list(a);
        openMenu(list, 0);                                    // Afterglow: two songs
        sheetItem("Add all to Favorites").performClick();
        assertEquals("Added 2 songs to Favorites", ShadowToast.getTextOfLatestToast());
        assertEquals(2, Favorites.get(app).size());

        openMenu(list, 0);
        assertNull(sheetItem("Add all to Favorites"));
        sheetItem("Remove all from Favorites").performClick();
        assertEquals("Removed 2 songs from Favorites", ShadowToast.getTextOfLatestToast());
        assertEquals(0, Favorites.get(app).size());
        c.pause().stop().destroy();
    }

    @Test
    public void aSongThatIsOnlyPartlyFavoriteStillOffersToAddTheRest() throws Exception {
        ActivityController<LibraryActivity> c = openLibrary();
        LibraryActivity a = c.get();
        Favorites.get(app).toggle(songNamed("Midnight Drive"));
        a.findViewById(R.id.tab_albums).performClick();
        openMenu(list(a), 0);                                 // Afterglow has Midnight Drive and Glass Cities
        sheetItem("Add all to Favorites").performClick();
        assertEquals("one was already there", "Added 1 song to Favorites", ShadowToast.getTextOfLatestToast());
        assertEquals(2, Favorites.get(app).size());
        c.pause().stop().destroy();
    }

    @Test
    public void favoriteSongsHaveASmallHeartInEveryList() throws Exception {
        ActivityController<LibraryActivity> c = openLibrary();
        LibraryActivity a = c.get();
        ListView list = list(a);
        Favorites.get(app).toggle(songNamed("Ember"));

        View ember = list.getAdapter().getView(TestMedia.songPos(list, 0), null, list);
        View glass = list.getAdapter().getView(TestMedia.songPos(list, 1), null, list);
        assertEquals(View.VISIBLE, ember.findViewById(R.id.row_fav).getVisibility());
        assertEquals(View.GONE, glass.findViewById(R.id.row_fav).getVisibility());

        Favorites.get(app).toggle(songNamed("Ember"));
        assertEquals("the heart goes with the favorite", View.GONE,
                list.getAdapter().getView(TestMedia.songPos(list, 0), null, list).findViewById(R.id.row_fav).getVisibility());

        // Folder, album and artist rows are not songs: no heart.
        a.findViewById(R.id.tab_albums).performClick();
        assertEquals(View.GONE, list.getAdapter().getView(0, null, list).findViewById(R.id.row_fav).getVisibility());
        c.pause().stop().destroy();
    }

    @Test
    public void tappingAFavoritePlaysTheFavoritesInOrder() throws Exception {
        PlaybackService service = startService();
        ActivityController<LibraryActivity> c = openLibrary();
        LibraryActivity a = c.get();
        Favorites f = Favorites.get(app);
        f.add(Arrays.asList(songNamed("Highway Hymn"), songNamed("Ember"), songNamed("Sunday Static")));
        a.findViewById(R.id.tab_favorites).performClick();
        ListView list = list(a);
        assertEquals(4, list.getAdapter().getCount());

        list.performItemClick(null, 2, 0);                    // the second favorite: Ember
        TestMedia.idle();
        assertEquals("Ember", service.currentTrack().title);
        assertEquals("the queue is the favorites, not the whole library", 3, service.queue().size());
        assertEquals(Arrays.asList("Highway Hymn", "Ember", "Sunday Static"), titles(service.queue().tracks()));
        c.pause().stop().destroy();
    }

    @Test
    public void thePlayAndShuffleButtonsOfTheFavoritesStartThem() throws Exception {
        PlaybackService service = startService();
        ActivityController<LibraryActivity> c = openLibrary();
        LibraryActivity a = c.get();
        Favorites.get(app).add(Arrays.asList(songNamed("Sunday Static"), songNamed("Ember")));
        a.findViewById(R.id.tab_favorites).performClick();
        ListView list = list(a);

        View header = list.getAdapter().getView(0, null, list);
        header.findViewById(R.id.hdr_play).performClick();
        TestMedia.idle();
        assertEquals("Sunday Static", service.currentTrack().title);
        assertEquals(2, service.queue().size());
        assertFalse(service.queue().isShuffle());

        header.findViewById(R.id.hdr_shuffle).performClick();
        TestMedia.idle();
        assertTrue(service.queue().isShuffle());
        assertEquals(2, service.queue().size());
        c.pause().stop().destroy();
    }

    private static List<String> titles(List<Track> tracks) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (Track t : tracks) out.add(t.title);
        return out;
    }

    @Test
    public void theLastOpenTabIsRememberedAndOutOfRangeValuesAreClamped() throws Exception {
        TestMedia.install(app);
        Prefs.get(app).setLastTab(4);
        ActivityController<LibraryActivity> c =
                Robolectric.buildActivity(LibraryActivity.class).create().start().resume().visible();
        TestMedia.awaitLibrary(Library.get(app));
        assertEquals("Favorites", ((TextView) c.get().findViewById(R.id.title)).getText().toString());
        c.pause().stop().destroy();

        Prefs.get(app).setLastTab(9);                          // from some later version of the app
        c = Robolectric.buildActivity(LibraryActivity.class).create().start().resume().visible();
        assertEquals("Favorites", ((TextView) c.get().findViewById(R.id.title)).getText().toString());
        c.pause().stop().destroy();
    }

    @Test
    public void favoritesSurviveClosingTheApp() throws Exception {
        TestMedia.install(app);
        Library.get(app).refresh();
        TestMedia.awaitLibrary(Library.get(app));
        Favorites.get(app).toggle(songNamed("Ember"));
        Favorites.get(app).toggle(songNamed("Sunday Static"));

        // A new process: every singleton starts again, only the saved preferences remain.
        java.lang.reflect.Field f;
        try {
            f = Favorites.class.getDeclaredField("instance");
            f.setAccessible(true);
            f.set(null, null);
            f = Prefs.class.getDeclaredField("instance");
            f.setAccessible(true);
            f.set(null, null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        Favorites again = Favorites.get(app);
        assertEquals(2, again.size());
        assertTrue(again.contains(songNamed("Ember")));
        assertEquals("Sunday Static", again.tracks(Library.get(app)).get(0).title);
    }

    @Test
    public void aDeletedSongIsNoLongerAFavorite() throws Exception {
        ActivityController<LibraryActivity> c = openLibrary();
        LibraryActivity a = c.get();
        shadowOf(app).grantPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        ReflectionHelpers.setStaticField(Build.VERSION.class, "SDK_INT", 29);
        Favorites.get(app).add(Arrays.asList(songNamed("Ember"), songNamed("Sunday Static")));

        openMenu(list(a), TestMedia.songPos(list(a), 0));                                 // Ember
        LinearLayout items = ShadowDialog.getLatestDialog().findViewById(R.id.gd_items);
        items.getChildAt(items.getChildCount() - 1).performClick();   // Delete…
        ShadowDialog.getLatestDialog().findViewById(R.id.gd_positive).performClick();

        assertEquals(Collections.singletonList(5L), TestMedia.DELETED);
        assertFalse(Favorites.get(app).contains(songNamed("Ember")));
        assertEquals("only the deleted one left the list", 1, Favorites.get(app).size());
        c.pause().stop().destroy();
    }

    @Test
    public void theFavoritesTabAlsoWorksInTheGlassLook() throws Exception {
        TestMedia.install(app);
        Prefs.get(app).setGlass(true);
        ActivityController<LibraryActivity> c =
                Robolectric.buildActivity(LibraryActivity.class).create().start().resume().visible();
        TestMedia.awaitLibrary(Library.get(app));
        LibraryActivity a = c.get();
        a.findViewById(R.id.tab_favorites).performClick();
        assertEquals(View.VISIBLE, a.findViewById(R.id.empty).getVisibility());
        Favorites.get(app).toggle(songNamed("Ember"));
        assertEquals(2, list(a).getAdapter().getCount());
        c.pause().stop().destroy();
    }

    // ---- the heart on Now Playing ---------------------------------------------------------------------

    private ActivityController<PlayerActivity> openPlayer(PlaybackService service) throws Exception {
        Library library = Library.get(app);
        library.refresh();
        TestMedia.awaitLibrary(library);
        service.playQueue(library.tracks(), 0, false);
        TestMedia.idle();
        ActivityController<PlayerActivity> c =
                Robolectric.buildActivity(PlayerActivity.class).create().start().resume().visible();
        TestMedia.idle();
        return c;
    }

    @Test
    public void theHeartOnNowPlayingMarksTheSongThatIsPlaying() throws Exception {
        TestMedia.install(app);
        PlaybackService service = startService();
        ActivityController<PlayerActivity> c = openPlayer(service);
        PlayerActivity a = c.get();
        ImageButton heart = a.findViewById(R.id.np_fav);

        assertEquals(View.VISIBLE, heart.getVisibility());
        assertEquals(app.getString(R.string.cd_favorite_add), heart.getContentDescription().toString());
        assertEquals("an empty heart is plain, not red", app.getColor(R.color.icon), heart.getImageTintList().getDefaultColor());

        heart.performClick();
        Track playing = service.currentTrack();
        assertTrue(Favorites.get(app).contains(playing));
        assertEquals(app.getString(R.string.cd_favorite_remove), heart.getContentDescription().toString());
        assertEquals("a filled heart is red", app.getColor(R.color.accent_text), heart.getImageTintList().getDefaultColor());

        heart.performClick();
        assertFalse(Favorites.get(app).contains(playing));
        assertEquals(app.getString(R.string.cd_favorite_add), heart.getContentDescription().toString());
        c.pause().stop().destroy();
    }

    @Test
    public void theHeartFollowsChangesMadeElsewhere() throws Exception {
        TestMedia.install(app);
        PlaybackService service = startService();
        ActivityController<PlayerActivity> c = openPlayer(service);
        ImageButton heart = c.get().findViewById(R.id.np_fav);

        Favorites.get(app).toggle(service.currentTrack());     // from a menu on another screen
        assertEquals(app.getString(R.string.cd_favorite_remove), heart.getContentDescription().toString());

        service.next(true);                                     // another song: not a favorite
        TestMedia.idle();
        assertEquals(app.getString(R.string.cd_favorite_add), heart.getContentDescription().toString());
        c.pause().stop().destroy();
    }

    @Test
    public void withNothingPlayingThereIsNoHeartToPress() throws Exception {
        TestMedia.install(app);
        startService();
        ActivityController<PlayerActivity> c =
                Robolectric.buildActivity(PlayerActivity.class).create().start().resume().visible();
        TestMedia.idle();
        PlayerActivity a = c.get();
        assertEquals(View.INVISIBLE, a.findViewById(R.id.np_fav).getVisibility());
        a.findViewById(R.id.np_fav).performClick();
        assertEquals(0, Favorites.get(app).size());
        assertEquals("no bars over a cover that is not there", View.GONE, a.findViewById(R.id.np_bars).getVisibility());
        c.pause().stop().destroy();
    }

    // ---- the moving bars ------------------------------------------------------------------------------

    @Test
    public void barsOverTheCoverMoveWithThePlayButton() throws Exception {
        TestMedia.install(app);
        PlaybackService service = startService();
        ActivityController<PlayerActivity> c = openPlayer(service);
        PlayerActivity a = c.get();
        EqBarsView over = a.findViewById(R.id.np_bars);
        EqBarsView top = a.findViewById(R.id.btn_eq);

        assertTrue(service.isPlaying());
        assertEquals(View.VISIBLE, over.getVisibility());
        assertTrue(over.isPlaying());
        assertTrue("the shared clock is moving them", over.isRunning());
        assertTrue(top.isPlaying());

        service.togglePlayPause();                              // pause
        TestMedia.idle();
        assertFalse(over.isPlaying());
        assertFalse(top.isPlaying());

        service.togglePlayPause();                              // play again
        TestMedia.idle();
        assertTrue(over.isPlaying());
        assertTrue(over.isRunning());
        c.pause().stop().destroy();
    }

    @Test
    public void theEqualizerButtonOfThePlayerIsTheMovingBarsAndOpensTheEqualizer() throws Exception {
        TestMedia.install(app);
        PlaybackService service = startService();
        ActivityController<PlayerActivity> c = openPlayer(service);
        PlayerActivity a = c.get();
        View button = a.findViewById(R.id.btn_eq);
        assertTrue(button instanceof EqBarsView);
        assertEquals(app.getString(R.string.cd_equalizer), button.getContentDescription().toString());
        assertTrue("big enough to press", button.getLayoutParams().width >= (int) (48 * app.getResources().getDisplayMetrics().density));

        button.performClick();
        Intent started = shadowOf(a).getNextStartedActivity();
        assertEquals(EqualizerActivity.class.getName(), started.getComponent().getClassName());
        c.pause().stop().destroy();
    }

    @Test
    public void theLibraryShowsTheBarsOnThePlayingRowTheMiniPlayerAndTheTopButton() throws Exception {
        PlaybackService service = startService();
        ActivityController<LibraryActivity> c = openLibrary();
        LibraryActivity a = c.get();
        ListView list = list(a);
        EqBarsView top = a.findViewById(R.id.btn_eq);
        EqBarsView mini = a.findViewById(R.id.mini_bars);
        assertFalse("nothing plays yet", top.isPlaying());
        assertEquals(View.GONE, mini.getVisibility());

        list.performItemClick(null, TestMedia.songPos(list, 0), 0);   // Ember
        TestMedia.idle();
        assertTrue(top.isPlaying());
        assertEquals(View.VISIBLE, mini.getVisibility());
        assertTrue(mini.isPlaying());

        View playingRow = list.getAdapter().getView(TestMedia.songPos(list, 0), null, list);
        EqBarsView bars = playingRow.findViewById(R.id.row_playing);
        assertEquals(View.VISIBLE, bars.getVisibility());
        assertTrue(bars.isPlaying());
        View otherRow = list.getAdapter().getView(TestMedia.songPos(list, 1), null, list);
        assertEquals("only the current song", View.GONE, otherRow.findViewById(R.id.row_playing).getVisibility());

        service.togglePlayPause();                              // pause
        TestMedia.idle();
        assertFalse(top.isPlaying());
        assertEquals("the mini player keeps quiet while paused", View.GONE, mini.getVisibility());
        playingRow = list.getAdapter().getView(TestMedia.songPos(list, 0), null, list);
        assertEquals("the current song is still marked", View.VISIBLE, playingRow.findViewById(R.id.row_playing).getVisibility());
        assertFalse("but its bars rest", ((EqBarsView) playingRow.findViewById(R.id.row_playing)).isPlaying());
        c.pause().stop().destroy();
    }

    @Test
    public void theQueueMarksThePlayingSongWithBarsToo() throws Exception {
        TestMedia.install(app);
        PlaybackService service = startService();
        Library library = Library.get(app);
        library.refresh();
        TestMedia.awaitLibrary(library);
        service.playQueue(library.tracks(), 1, false);
        TestMedia.idle();
        ActivityController<QueueActivity> c =
                Robolectric.buildActivity(QueueActivity.class).create().start().resume().visible();
        TestMedia.idle();
        ListView list = c.get().findViewById(R.id.list);
        View row = list.getAdapter().getView(1, null, list);
        assertEquals(View.VISIBLE, row.findViewById(R.id.row_playing).getVisibility());
        assertTrue(((EqBarsView) row.findViewById(R.id.row_playing)).isPlaying());

        Favorites.get(app).toggle(library.tracks().get(0));
        assertEquals("hearts show in the queue too", View.VISIBLE,
                list.getAdapter().getView(0, null, list).findViewById(R.id.row_fav).getVisibility());
        c.pause().stop().destroy();
    }

    @Test
    public void theEqualizerScreenHasLiveBarsThatFollowTheMusic() throws Exception {
        TestMedia.install(app);
        PlaybackService service = startService();
        Library library = Library.get(app);
        library.refresh();
        TestMedia.awaitLibrary(library);
        service.playQueue(library.tracks(), 0, false);
        TestMedia.idle();

        ActivityController<EqualizerActivity> c =
                Robolectric.buildActivity(EqualizerActivity.class).create().start().resume().visible();
        TestMedia.idle();
        EqBarsView bars = c.get().findViewById(R.id.eq_bars);
        assertTrue(bars.isPlaying());
        service.togglePlayPause();
        TestMedia.idle();
        assertFalse(bars.isPlaying());
        c.pause().stop().destroy();
    }
}
