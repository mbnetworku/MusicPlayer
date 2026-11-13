package com.musicplayer.lite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import android.app.Application;
import android.widget.ListView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.List;

/** Choosing how the songs are ordered. */
@RunWith(RobolectricTestRunner.class)
public class SortOrderTest {
    private static Track t(long id, String title, String artist, String album, int ms, int no) {
        return new Track(id, title, artist, album, 1, ms, no, "/m", title + ".mp3");
    }

    private final List<Track> list = new ArrayList<>();
    private Application app;

    @Before
    public void setUp() {
        app = RuntimeEnvironment.getApplication();
        TestMedia.install(app);
        FakePlayback.register();
        Prefs.get(app).setSort(0, false);
        list.add(t(3, "Charlie", "Zed", "B-side", 100, 2));
        list.add(t(1, "Alpha", "Mia", "Z-album", 300, 1));
        list.add(t(2, "Bravo", "Abe", "A-album", 200, 1));
    }

    private static String titles(List<Track> l) {
        StringBuilder b = new StringBuilder();
        for (Track x : l) b.append(x.title.charAt(0));
        return b.toString();
    }

    @Test
    public void everyOrder() {
        assertSame("title order is the library's own", list, SortOrder.apply(list, SortOrder.TITLE, false));
        assertEquals("CBA", titles(SortOrder.apply(list, SortOrder.TITLE, true)));
        assertEquals("BAC", titles(SortOrder.apply(list, SortOrder.ARTIST, false)));
        assertEquals("BCA", titles(SortOrder.apply(list, SortOrder.ALBUM, false)));
        assertEquals("CBA", titles(SortOrder.apply(list, SortOrder.RECENT, false)));
        assertEquals("ABC", titles(SortOrder.apply(list, SortOrder.LENGTH, false)));
        assertEquals("CBA", titles(SortOrder.apply(list, SortOrder.LENGTH, true)));
    }

    @Test
    public void theChoiceIsKeptAndBadValuesFallBack() {
        Prefs p = Prefs.get(app);
        p.setSort(3, true);
        assertEquals(3, p.sortKey());
        assertEquals(true, p.sortReverse());
        p.setSort(42, false);
        assertEquals(0, p.sortKey());
    }

    @Test
    public void theSongsTabFollowsTheChoice() throws Exception {
        Prefs.get(app).setSort(SortOrder.RECENT, false);
        LibraryActivity a = Robolectric.buildActivity(LibraryActivity.class).create().start().resume().visible().get();
        TestMedia.awaitLibrary(Library.get(app));
        TestMedia.idle();
        ListView list = a.findViewById(R.id.list);
        RowAdapter.Row first = (RowAdapter.Row) list.getAdapter().getItem(TestMedia.songPos(list, 0));
        long newest = 0;
        for (Track x : Library.get(app).tracks()) newest = Math.max(newest, x.id);
        assertEquals("newest file first", newest, first.track.id);
    }
}
