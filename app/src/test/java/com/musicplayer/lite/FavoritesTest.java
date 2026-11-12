package com.musicplayer.lite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The list of hearted songs: order, saving, and what happens to songs that are not there. */
public class FavoritesTest {

    /** Keeps the saved text in memory, like the app keeps it in its preferences. */
    private static final class MemoryStore implements Favorites.Store {
        String text = "";
        int saves;

        @Override
        public String load() {
            return text;
        }

        @Override
        public void save(String t) {
            text = t;
            saves++;
        }
    }

    private static final class CountingListener implements Favorites.Listener {
        int calls;

        @Override
        public void onFavoritesChanged() {
            calls++;
        }
    }

    private static Track song(long id, String name) {
        return new Track(id, name, "Artist", "Album", 1L, 1000, 1, "/storage/emulated/0/Music", name + ".mp3");
    }

    private static List<String> titles(List<Track> tracks) {
        List<String> out = new ArrayList<>();
        for (Track t : tracks) out.add(t.title);
        return out;
    }

    private static Map<String, Track> library(Track... tracks) {
        Map<String, Track> map = new HashMap<>();
        for (Track t : tracks) map.put(t.path(), t);
        return map;
    }

    @Test
    public void startsEmpty() {
        Favorites f = new Favorites(new MemoryStore());
        assertEquals(0, f.size());
        assertFalse(f.contains(song(1, "A")));
        assertTrue(f.tracks(library(song(1, "A"))::get).isEmpty());
    }

    @Test
    public void toggleAddsAndThenRemoves() {
        Favorites f = new Favorites(new MemoryStore());
        Track a = song(1, "A");
        assertTrue("now a favorite", f.toggle(a));
        assertTrue(f.contains(a));
        assertFalse("no longer a favorite", f.toggle(a));
        assertFalse(f.contains(a));
        assertEquals(0, f.size());
    }

    @Test
    public void aSongIsKnownByItsPathNotByItsId() {
        Favorites f = new Favorites(new MemoryStore());
        f.toggle(song(1, "A"));
        // The library was scanned again and the same file got another id: it is still a favorite.
        assertTrue(f.contains(song(99, "A")));
        // Another file with the same title is not.
        Track other = new Track(1, "A", "Artist", "Album", 1L, 1000, 1, "/storage/1234-ABCD/Songs", "A.mp3");
        assertFalse(f.contains(other));
    }

    @Test
    public void newestFirst() {
        Favorites f = new Favorites(new MemoryStore());
        Track a = song(1, "A");
        Track b = song(2, "B");
        Track c = song(3, "C");
        f.toggle(a);
        f.toggle(b);
        f.toggle(c);
        assertEquals(Arrays.asList("C", "B", "A"), titles(f.tracks(library(a, b, c)::get)));
    }

    @Test
    public void severalSongsGoToTheTopTogetherAndKeepTheirOrder() {
        Favorites f = new Favorites(new MemoryStore());
        Track a = song(1, "A");
        Track x = song(2, "X");
        Track y = song(3, "Y");
        Track z = song(4, "Z");
        f.add(Collections.singletonList(a));
        assertEquals(3, f.add(Arrays.asList(x, y, z)));
        assertEquals(Arrays.asList("X", "Y", "Z", "A"), titles(f.tracks(library(a, x, y, z)::get)));
    }

    @Test
    public void aSongThatIsAlreadyAFavoriteKeepsItsPlaceAndIsNotCountedAsNew() {
        Favorites f = new Favorites(new MemoryStore());
        Track a = song(1, "A");
        Track b = song(2, "B");
        Track c = song(3, "C");
        f.add(Collections.singletonList(a));
        f.add(Collections.singletonList(b));
        assertEquals("only C is new", 1, f.add(Arrays.asList(a, c)));
        assertEquals(Arrays.asList("C", "B", "A"), titles(f.tracks(library(a, b, c)::get)));
        assertEquals("nothing new at all", 0, f.add(Arrays.asList(a, b)));
    }

    @Test
    public void removeTakesSongsOffAndCountsThem() {
        Favorites f = new Favorites(new MemoryStore());
        Track a = song(1, "A");
        Track b = song(2, "B");
        Track c = song(3, "C");
        f.add(Arrays.asList(a, b, c));
        assertEquals("C was never a favorite twice, A and B go", 2, f.remove(Arrays.asList(a, b, song(9, "Q"))));
        assertEquals(Collections.singletonList("C"), titles(f.tracks(library(a, b, c)::get)));
        assertEquals(1, f.size());
    }

    @Test
    public void containsAllNeedsEverySongAndAtLeastOne() {
        Favorites f = new Favorites(new MemoryStore());
        Track a = song(1, "A");
        Track b = song(2, "B");
        f.toggle(a);
        assertTrue(f.containsAll(Collections.singletonList(a)));
        assertFalse(f.containsAll(Arrays.asList(a, b)));
        assertFalse("an empty list is not 'all favorites'", f.containsAll(Collections.emptyList()));
        f.toggle(b);
        assertTrue(f.containsAll(Arrays.asList(a, b)));
    }

    @Test
    public void everyChangeIsSavedAndASecondOpeningSeesIt() {
        MemoryStore store = new MemoryStore();
        Track a = song(1, "A");
        Track b = song(2, "B");
        Favorites first = new Favorites(store);
        first.toggle(a);
        first.toggle(b);
        assertEquals(2, store.saves);

        Favorites second = new Favorites(store);   // the app was closed and opened again
        assertEquals(Arrays.asList("B", "A"), titles(second.tracks(library(a, b)::get)));
        assertTrue(second.contains(a));
    }

    @Test
    public void nothingIsSavedWhenNothingChanged() {
        MemoryStore store = new MemoryStore();
        Favorites f = new Favorites(store);
        Track a = song(1, "A");
        f.toggle(a);
        int saved = store.saves;
        f.add(Collections.singletonList(a));
        f.remove(Collections.singletonList(song(7, "Q")));
        assertEquals(saved, store.saves);
    }

    @Test
    public void listenersHearAboutRealChangesOnly() {
        Favorites f = new Favorites(new MemoryStore());
        CountingListener l = new CountingListener();
        f.addListener(l);
        Track a = song(1, "A");
        f.toggle(a);
        assertEquals(1, l.calls);
        f.add(Collections.singletonList(a));
        f.remove(Collections.singletonList(song(7, "Q")));
        assertEquals("no change, no call", 1, l.calls);
        f.toggle(a);
        assertEquals(2, l.calls);
        f.removeListener(l);
        f.toggle(a);
        assertEquals("a listener that left hears nothing", 2, l.calls);
    }

    @Test
    public void aSongWhoseFileIsGoneIsHiddenButComesBackWithTheFile() {
        Favorites f = new Favorites(new MemoryStore());
        Track a = song(1, "A");
        Track b = song(2, "B");
        f.add(Arrays.asList(a, b));
        assertEquals("B's file is missing for now", Collections.singletonList("A"), titles(f.tracks(library(a)::get)));
        assertEquals("but it is still remembered", 2, f.size());
        assertEquals(Arrays.asList("A", "B"), titles(f.tracks(library(a, b)::get)));
    }

    // ---- the saved text -------------------------------------------------------------------------

    @Test
    public void textRoundTripsThroughOddPaths() {
        List<String> keys = Arrays.asList(
                "/storage/emulated/0/Music/Plain.mp3",
                "/storage/emulated/0/Music/back\\slash.mp3",
                "/storage/emulated/0/Music/line\nbreak.mp3",
                "/storage/emulated/0/Music/ends with backslash\\",
                "/storage/emulated/0/Music/Été — 音楽.mp3",
                "/storage/emulated/0/Music/\\n looks like a break but is not");
        assertEquals(keys, Favorites.decode(Favorites.encode(keys)));
    }

    @Test
    public void encodedTextHasOnePathPerLine() {
        String text = Favorites.encode(Arrays.asList("/a.mp3", "/b\nc.mp3"));
        assertEquals(2, text.split("\n").length);
    }

    @Test
    public void badOrEmptyTextGivesAnEmptyList() {
        assertTrue(Favorites.decode(null).isEmpty());
        assertTrue(Favorites.decode("").isEmpty());
        assertTrue(Favorites.decode("\n\n").isEmpty());
        assertEquals(Collections.singletonList("/a.mp3"), Favorites.decode("\n/a.mp3\n\n"));
    }

    @Test
    public void aRepeatedPathInTheSavedTextCountsOnce() {
        MemoryStore store = new MemoryStore();
        store.text = "/a.mp3\n/b.mp3\n/a.mp3";
        Favorites f = new Favorites(store);
        assertEquals(2, f.size());
    }
}
