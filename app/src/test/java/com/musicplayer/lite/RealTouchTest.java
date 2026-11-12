package com.musicplayer.lite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ListView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.TimeUnit;

/**
 * Taps made the way a finger makes them: touch events sent to the whole screen, not a click
 * method called on one view. A view that covers the list, or one that swallows touches, would
 * not be noticed by a test that calls the click listener directly.
 */
@RunWith(RobolectricTestRunner.class)
public class RealTouchTest {
    private Application app;
    private PlaybackService service;

    @Before
    public void setUp() throws Exception {
        app = RuntimeEnvironment.getApplication();
        TestMedia.install(app);
        FakePlayback.register();
        service = Robolectric.buildService(PlaybackService.class).create().get();
        TestMedia.exposeService(app, service);
    }

    /** The songs are known (the queue tests need them before any screen has asked for them). */
    private void loadLibrary() throws Exception {
        Library.get(app).refresh();
        TestMedia.awaitLibrary(Library.get(app));
    }

    private ActivityController<LibraryActivity> openLibrary() throws Exception {
        ActivityController<LibraryActivity> c =
                Robolectric.buildActivity(LibraryActivity.class).create().start().resume().visible();
        c.windowFocusChanged(true);   // a list only answers a tap while its window has the focus
        TestMedia.awaitLibrary(Library.get(app));
        TestMedia.idle();
        measure(c.get());
        return c;
    }

    /** The screen as a phone or head unit would have laid it out. */
    private static void measure(Activity a) {
        View content = a.getWindow().getDecorView();
        int w = a.getResources().getDisplayMetrics().widthPixels;
        int h = a.getResources().getDisplayMetrics().heightPixels;
        content.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        content.layout(0, 0, w, h);
    }

    /** A finger touching the middle of {@code target} and lifting again, sent to the whole window. */
    private static void tap(Activity a, View target) {
        int[] at = new int[2];
        target.getLocationInWindow(at);
        float x = at[0] + target.getWidth() / 2f;
        float y = at[1] + target.getHeight() / 2f;
        long t = SystemClock.uptimeMillis();
        View decor = a.getWindow().getDecorView();
        decor.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0));
        ShadowLooper.idleMainLooper(60, TimeUnit.MILLISECONDS);
        decor.dispatchTouchEvent(MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, x, y, 0));
        ShadowLooper.idleMainLooper(600, TimeUnit.MILLISECONDS);
        TestMedia.idle();
    }

    /** A finger resting on {@code target} until the list treats it as a long press. */
    private static void longPress(Activity a, View target) {
        int[] at = new int[2];
        target.getLocationInWindow(at);
        float x = at[0] + target.getWidth() / 2f;
        float y = at[1] + target.getHeight() / 2f;
        long t = SystemClock.uptimeMillis();
        View decor = a.getWindow().getDecorView();
        decor.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0));
        ShadowLooper.idleMainLooper(900, TimeUnit.MILLISECONDS);
        decor.dispatchTouchEvent(MotionEvent.obtain(t, t + 900, MotionEvent.ACTION_UP, x, y, 0));
        ShadowLooper.idleMainLooper(300, TimeUnit.MILLISECONDS);
        TestMedia.idle();
    }

    private static Dialog openDialog() {
        Dialog d = ShadowDialog.getLatestDialog();
        return d != null && d.isShowing() ? d : null;
    }

    private static View firstRow(ListView list) {
        assertTrue("the list is laid out and has rows", list.getChildCount() > 0);
        return list.getChildAt(0);
    }

    @Test
    public void tappingASongWithAFingerPlaysItOnAPhone() throws Exception {
        ActivityController<LibraryActivity> c = openLibrary();
        LibraryActivity a = c.get();
        ListView list = a.findViewById(R.id.list);
        tap(a, firstRow(list));
        assertNotNull("the tap started a song", service.currentTrack());
        assertEquals("Ember", service.currentTrack().title);
        assertTrue(service.isPlaying());
        c.pause().stop().destroy();
    }

    private void tapPartOfFirstRow(int part) throws Exception {
        ActivityController<LibraryActivity> c = openLibrary();
        LibraryActivity a = c.get();
        ListView list = a.findViewById(R.id.list);
        View row = firstRow(list);
        View target = part == 0 ? row.findViewById(R.id.row_art)
                : part == 1 ? row.findViewById(R.id.row_title) : row.findViewById(R.id.row_sub);
        tap(a, target);
        assertNotNull("tapping part " + part + " of the row played nothing", service.currentTrack());
        assertTrue(service.isPlaying());
        c.pause().stop().destroy();
    }

    @Test
    public void tappingOnTheCoverOfARowPlaysIt() throws Exception {
        tapPartOfFirstRow(0);
    }

    @Test
    public void tappingOnTheTitleOfARowPlaysIt() throws Exception {
        tapPartOfFirstRow(1);
    }

    @Test
    public void tappingOnTheSecondLineOfARowPlaysIt() throws Exception {
        tapPartOfFirstRow(2);
    }

    @Test
    public void tappingASecondSongSwitchesToItWhileOneIsPlaying() throws Exception {
        ActivityController<LibraryActivity> c = openLibrary();
        LibraryActivity a = c.get();
        ListView list = a.findViewById(R.id.list);
        tap(a, list.getChildAt(0));
        assertEquals("Ember", service.currentTrack().title);
        measure(a);
        tap(a, list.getChildAt(1));
        assertEquals("Glass Cities", service.currentTrack().title);
        c.pause().stop().destroy();
    }

    @Test
    public void theTabsAndTheButtonsAnswerAFinger() throws Exception {
        ActivityController<LibraryActivity> c = openLibrary();
        LibraryActivity a = c.get();
        tap(a, a.findViewById(R.id.tab_albums));
        assertTrue("the Albums tab was chosen by touch", a.findViewById(R.id.tab_albums).isSelected());
        tap(a, a.findViewById(R.id.tab_favorites));
        assertTrue(a.findViewById(R.id.tab_favorites).isSelected());
        tap(a, a.findViewById(R.id.tab_songs));
        assertTrue(a.findViewById(R.id.tab_songs).isSelected());
        c.pause().stop().destroy();
    }

    @Test
    @Config(qualifiers = "w900dp-h500dp-land")
    public void tappingASongWithAFingerPlaysItOnACarScreen() throws Exception {
        ActivityController<LibraryActivity> c = openLibrary();
        LibraryActivity a = c.get();
        ListView list = a.findViewById(R.id.list);
        tap(a, firstRow(list));
        assertNotNull("the tap started a song in the car layout", service.currentTrack());
        assertTrue(service.isPlaying());
        c.pause().stop().destroy();
    }

    @Test
    @Config(qualifiers = "land")
    public void tappingASongWithAFingerPlaysItOnAPhoneTurnedSideways() throws Exception {
        ActivityController<LibraryActivity> c = openLibrary();
        LibraryActivity a = c.get();
        ListView list = a.findViewById(R.id.list);
        tap(a, firstRow(list));
        assertNotNull(service.currentTrack());
        c.pause().stop().destroy();
    }

    @Test
    public void tappingTheMoreButtonOpensTheMenuAndDoesNotPlay() throws Exception {
        ActivityController<LibraryActivity> c = openLibrary();
        LibraryActivity a = c.get();
        ListView list = a.findViewById(R.id.list);
        tap(a, firstRow(list).findViewById(R.id.row_more));
        assertNotNull("the glass menu is open", openDialog());
        assertEquals(null, service.currentTrack());
        c.pause().stop().destroy();
    }

    @Test
    public void holdingAFingerOnARowOpensTheMenu() throws Exception {
        ActivityController<LibraryActivity> c = openLibrary();
        LibraryActivity a = c.get();
        ListView list = a.findViewById(R.id.list);
        longPress(a, firstRow(list));
        assertNotNull("a long press opens the menu", openDialog());
        c.pause().stop().destroy();
    }

    @Test
    public void tappingAnAlbumOpensIt() throws Exception {
        ActivityController<LibraryActivity> c = openLibrary();
        LibraryActivity a = c.get();
        tap(a, a.findViewById(R.id.tab_albums));
        measure(a);
        ListView list = a.findViewById(R.id.list);
        tap(a, firstRow(list));
        assertEquals("the album is open: its header comes first", RowAdapter.HEADER,
                ((RowAdapter.Row) list.getAdapter().getItem(0)).type);
        assertEquals("Afterglow", ((RowAdapter.Row) list.getAdapter().getItem(0)).title);
        c.pause().stop().destroy();
    }

    @Test
    public void tappingAnArtistOpensThem() throws Exception {
        ActivityController<LibraryActivity> c = openLibrary();
        LibraryActivity a = c.get();
        tap(a, a.findViewById(R.id.tab_artists));
        measure(a);
        ListView list = a.findViewById(R.id.list);
        tap(a, firstRow(list));
        assertEquals(RowAdapter.HEADER, ((RowAdapter.Row) list.getAdapter().getItem(0)).type);
        c.pause().stop().destroy();
    }

    @Test
    public void tappingASongInsideAFolderPlaysIt() throws Exception {
        ActivityController<LibraryActivity> c = openLibrary();
        LibraryActivity a = c.get();
        tap(a, a.findViewById(R.id.tab_folders));
        measure(a);
        ListView list = a.findViewById(R.id.list);
        for (int depth = 0; depth < 6; depth++) {
            RowAdapter.Row r = null;
            int at = -1;
            for (int i = 0; i < list.getAdapter().getCount(); i++) {
                RowAdapter.Row x = (RowAdapter.Row) list.getAdapter().getItem(i);
                if (x.type == RowAdapter.TRACK) { r = x; at = i; break; }
            }
            if (r != null) {
                tap(a, list.getChildAt(at));
                assertNotNull("the tap on a song in a folder started it", service.currentTrack());
                assertEquals(r.track.title, service.currentTrack().title);
                c.pause().stop().destroy();
                return;
            }
            int f = -1;
            for (int i = 0; i < list.getAdapter().getCount(); i++) {
                RowAdapter.Row x = (RowAdapter.Row) list.getAdapter().getItem(i);
                if (x.type == RowAdapter.FOLDER && x.tag != null) { f = i; break; }
            }
            assertTrue("a folder to open", f >= 0);
            tap(a, list.getChildAt(f));
            measure(a);
        }
        throw new AssertionError("no song found in any folder");
    }

    @Test
    public void thePlayAllButtonOfAFolderAnswersAFinger() throws Exception {
        ActivityController<LibraryActivity> c = openLibrary();
        LibraryActivity a = c.get();
        tap(a, a.findViewById(R.id.tab_folders));
        measure(a);
        ListView list = a.findViewById(R.id.list);
        tap(a, list.getChildAt(0).findViewById(R.id.hdr_play));
        assertNotNull("Play all at the top of the folders started music", service.currentTrack());
        int all = service.queue().size();
        assertEquals(5, all);
        tap(a, list.getChildAt(1));
        measure(a);
        tap(a, list.getChildAt(0).findViewById(R.id.hdr_play));
        assertTrue("Play all inside a folder started it", service.queue().size() < all);
        c.pause().stop().destroy();
    }

    /** Every tab, upright and sideways, phone and wide screen: a finger on a song plays it. */
    @Test
    public void everyTabPlaysInEveryOrientation() throws Exception {
        String[] screens = {"w360dp-h640dp-port", "w640dp-h360dp-land", "w1024dp-h600dp-land-night"};
        int[] tabs = {R.id.tab_songs, R.id.tab_folders, R.id.tab_albums, R.id.tab_artists, R.id.tab_favorites};
        loadLibrary();
        Favorites.get(app).toggle(Library.get(app).tracks().get(0));
        for (String q : screens) {
            RuntimeEnvironment.setQualifiers("+" + q);
            for (int tabId : tabs) {
                ActivityController<LibraryActivity> c = openLibrary();
                LibraryActivity a = c.get();
                tap(a, a.findViewById(tabId));
                measure(a);
                ListView list = a.findViewById(R.id.list);
                // Open folders, albums and artists until a song row shows; then tap it.
                for (int depth = 0; depth < 6; depth++) {
                    int songAt = -1, openAt = -1;
                    for (int i = 0; i < list.getChildCount(); i++) {
                        RowAdapter.Row r = (RowAdapter.Row) list.getAdapter().getItem(
                                list.getFirstVisiblePosition() + i);
                        if (r.type == RowAdapter.TRACK && songAt < 0) songAt = i;
                        if ((r.type == RowAdapter.GROUP || (r.type == RowAdapter.FOLDER && r.tag != null))
                                && openAt < 0) openAt = i;
                    }
                    if (songAt >= 0) {
                        tap(a, list.getChildAt(songAt));
                        assertNotNull(q + " tab " + tabId + ": the tap started a song", service.currentTrack());
                        break;
                    }
                    if (openAt < 0) break;               // an empty tab (no favorites yet) has nothing to tap
                    tap(a, list.getChildAt(openAt));
                    measure(a);
                }
                c.pause().stop().destroy();
            }
        }
    }

    @Test
    public void tappingAFolderOpensIt() throws Exception {
        ActivityController<LibraryActivity> c = openLibrary();
        LibraryActivity a = c.get();
        tap(a, a.findViewById(R.id.tab_folders));
        measure(a);
        ListView list = a.findViewById(R.id.list);
        String before = ((RowAdapter.Row) list.getAdapter().getItem(0)).path;
        tap(a, list.getChildAt(1));                        // the row after the header: a folder
        String after = ((RowAdapter.Row) list.getAdapter().getItem(0)).path;
        assertTrue("the folder was opened: '" + before + "' became '" + after + "'", !before.equals(after));
        c.pause().stop().destroy();
    }

    @Test
    public void tappingAFavoritePlaysIt() throws Exception {
        ActivityController<LibraryActivity> c = openLibrary();
        LibraryActivity a = c.get();
        Favorites.get(app).toggle(Library.get(app).tracks().get(2));
        tap(a, a.findViewById(R.id.tab_favorites));
        measure(a);
        ListView list = a.findViewById(R.id.list);
        tap(a, list.getChildAt(1));                        // row 0 is the header with Play all and Shuffle
        assertNotNull(service.currentTrack());
        assertEquals(Library.get(app).tracks().get(2).title, service.currentTrack().title);
        c.pause().stop().destroy();
    }

    @Test
    public void thePlayAllButtonOfAnAlbumAnswersAFinger() throws Exception {
        ActivityController<LibraryActivity> c = openLibrary();
        LibraryActivity a = c.get();
        tap(a, a.findViewById(R.id.tab_albums));
        measure(a);
        ListView list = a.findViewById(R.id.list);
        tap(a, firstRow(list));                            // open Afterglow
        measure(a);
        tap(a, list.getChildAt(0).findViewById(R.id.hdr_play));
        assertNotNull("Play all started the album", service.currentTrack());
        assertEquals(2, service.queue().size());
        c.pause().stop().destroy();
    }

    @Test
    public void theUpNextListAnswersAFinger() throws Exception {
        loadLibrary();
        service.playQueue(Library.get(app).tracks(), 0, false);
        TestMedia.idle();
        ActivityController<QueueActivity> c =
                Robolectric.buildActivity(QueueActivity.class).create().start().resume().visible();
        c.windowFocusChanged(true);
        TestMedia.idle();
        QueueActivity a = c.get();
        measure(a);
        ListView list = a.findViewById(R.id.list);
        tap(a, list.getChildAt(3));
        assertEquals("tapping the fourth song jumped to it", 3, service.queue().position());
        c.pause().stop().destroy();
    }

    @Test
    public void thePlayerControlsAnswerAFinger() throws Exception {
        loadLibrary();
        service.playQueue(Library.get(app).tracks(), 0, false);
        TestMedia.idle();
        ActivityController<PlayerActivity> c =
                Robolectric.buildActivity(PlayerActivity.class).create().start().resume().visible();
        c.windowFocusChanged(true);
        TestMedia.idle();
        PlayerActivity a = c.get();
        measure(a);
        assertTrue(service.isPlaying());
        tap(a, a.findViewById(R.id.np_play));
        assertTrue("the play button paused it", !service.isPlaying());
        tap(a, a.findViewById(R.id.np_next));
        assertEquals("next moved on", 1, service.queue().position());
        c.pause().stop().destroy();
    }
}
