package com.musicplayer.lite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Deleting songs for good, for each way Android allows it. */
@RunWith(RobolectricTestRunner.class)
public class MediaDeleterTest {

    private static Track track(long id, File file) {
        return new Track(id, "Song " + id, "Artist", "Album", 1, 1000, 1, file.getParent(), file.getName());
    }

    @Test
    public void theMethodDependsOnTheAndroidVersion() {
        assertEquals("Android 11 and newer: the system asks", MediaDeleter.SYSTEM, MediaDeleter.planFor(30, false));
        assertEquals(MediaDeleter.SYSTEM, MediaDeleter.planFor(35, true));
        assertEquals("Android 10 with write access: direct", MediaDeleter.DIRECT, MediaDeleter.planFor(29, true));
        assertEquals("Android 8 with write access: direct", MediaDeleter.DIRECT, MediaDeleter.planFor(26, true));
        assertEquals("without write access, ask for it first", MediaDeleter.NEEDS_PERMISSION, MediaDeleter.planFor(28, false));
    }

    @Test
    public void urisAndIdsDescribeTheSongs() {
        List<Track> tracks = Arrays.asList(
                new Track(5, "a", "x", "y", 1, 1, 1, "/m", "a.mp3"),
                new Track(9, "b", "x", "y", 1, 1, 1, "/m", "b.mp3"));
        assertEquals(2, MediaDeleter.uris(tracks).size());
        assertTrue(MediaDeleter.uris(tracks).get(0).toString().endsWith("/5"));
        assertEquals(new java.util.HashSet<>(Arrays.asList(5L, 9L)), MediaDeleter.ids(tracks));
    }

    @Test
    public void directDeleteRemovesTheEntryAndTheFile() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        TestMedia.install(app);
        File dir = Files.createTempDirectory("songs").toFile();
        File file = new File(dir, "Ember.mp3");
        assertTrue(file.createNewFile());

        // Song 5 is in the fake MediaStore, which reports the entry as removed (as the real one does,
        // together with the file); the helper also removes the file if it is still there.
        Set<Long> gone = MediaDeleter.deleteDirect(app, Collections.singletonList(track(5, file)));
        assertEquals(Collections.singleton(5L), gone);
        assertEquals(Collections.singletonList(5L), TestMedia.DELETED);
        assertFalse("the file is gone too", file.exists());
    }

    @Test
    public void aFileMediaStoreDoesNotKnowIsStillDeleted() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        TestMedia.install(app);
        File dir = Files.createTempDirectory("songs").toFile();
        File file = new File(dir, "Not in the library.mp3");
        assertTrue(file.createNewFile());

        Set<Long> gone = MediaDeleter.deleteDirect(app, Collections.singletonList(track(999, file)));
        assertEquals("the file was deleted, so the song is gone", Collections.singleton(999L), gone);
        assertFalse(file.exists());
    }

    @Test
    public void whenNothingCanBeDeletedTheSongStays() {
        Application app = RuntimeEnvironment.getApplication();
        TestMedia.install(app);
        // Unknown to MediaStore and no such file on disk: nothing was deleted.
        Track missing = new Track(777, "x", "y", "z", 1, 1, 1, "/no/such/folder", "none.mp3");
        assertTrue(MediaDeleter.deleteDirect(app, new ArrayList<>(Collections.singletonList(missing))).isEmpty());
    }
}
