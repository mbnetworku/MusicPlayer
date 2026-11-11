package com.musicplayer.lite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class FolderTreeTest {

    private static int nextId = 1;

    private static Track at(String path) {
        int slash = path.lastIndexOf('/');
        return new Track(nextId++, "t", "a", "b", 1, 1000, 0, path.substring(0, slash), path.substring(slash + 1));
    }

    /** A phone with internal storage and an SD card. */
    private static FolderTree.Node phone() {
        List<Track> all = new ArrayList<>();
        all.add(at("/storage/emulated/0/Music/Rock/a.mp3"));
        all.add(at("/storage/emulated/0/Music/Rock/b.mp3"));
        all.add(at("/storage/emulated/0/Music/Pop/c.mp3"));
        all.add(at("/storage/emulated/0/Download/d.mp3"));
        all.add(at("/storage/1234-ABCD/Songs/e.mp3"));
        return FolderTree.build(all);
    }

    private static FolderTree.Node node(FolderTree.Node root, String path) {
        FolderTree.Node n = FolderTree.find(root, path);
        assertNotNull(path, n);
        return n;
    }

    @Test
    public void countsTracksPerSubtree() {
        FolderTree.Node root = phone();
        assertEquals(5, root.total);
        assertEquals(3, node(root, "/storage/emulated/0/Music").total);
        assertEquals(2, node(root, "/storage/emulated/0/Music/Rock").total);
        assertEquals(4, FolderTree.countFolders(root));
    }

    @Test
    public void visibleRootSkipsEmptySingleChildPrefixes() {
        FolderTree.Node root = phone();
        FolderTree.Node vroot = FolderTree.visibleRoot(root);
        assertEquals("/storage", vroot.path);
    }

    @Test
    public void volumesGetFriendlyNamesAndChainsCollapse() {
        FolderTree.Node vroot = FolderTree.visibleRoot(phone());
        List<FolderTree.Entry> entries = FolderTree.entries(vroot);
        assertEquals(2, entries.size());
        // sorted naturally by folder name: "1234-ABCD" before "emulated"
        assertEquals("SD card or USB (1234-ABCD) › Songs", entries.get(0).label);
        assertEquals("/storage/1234-ABCD/Songs", entries.get(0).node.path);
        assertEquals("Internal storage", entries.get(1).label);
        assertEquals("/storage/emulated/0", entries.get(1).node.path);
    }

    @Test
    public void breadcrumbStartsAtTheVolume() {
        FolderTree.Node root = phone();
        assertEquals("Internal storage › Music › Rock",
                FolderTree.breadcrumb(node(root, "/storage/emulated/0/Music/Rock")));
        assertEquals("Internal storage", FolderTree.breadcrumb(node(root, "/storage/emulated/0")));
    }

    @Test
    public void upSkipsCollapsedFolders() {
        FolderTree.Node root = phone();
        FolderTree.Node vroot = FolderTree.visibleRoot(root);
        FolderTree.Node rock = node(root, "/storage/emulated/0/Music/Rock");
        FolderTree.Node music = FolderTree.parentInView(rock, vroot);
        assertEquals("/storage/emulated/0/Music", music.path);
        FolderTree.Node internal = FolderTree.parentInView(music, vroot);
        assertEquals("/storage/emulated/0", internal.path);
        assertSame(vroot, FolderTree.parentInView(internal, vroot));
        assertNull(FolderTree.parentInView(vroot, vroot));
        // "1234-ABCD" only contains "Songs", so Songs goes straight back to the top
        assertSame(vroot, FolderTree.parentInView(node(root, "/storage/1234-ABCD/Songs"), vroot));
    }

    @Test
    public void flattenListsFolderThenSubfolders() {
        FolderTree.Node music = node(phone(), "/storage/emulated/0/Music");
        List<Track> tracks = FolderTree.flatten(music);
        assertEquals(3, tracks.size());
        assertEquals("c.mp3", tracks.get(0).fileName); // Pop comes before Rock
        assertEquals("a.mp3", tracks.get(1).fileName);
        assertEquals("b.mp3", tracks.get(2).fileName);
    }

    @Test
    public void findReturnsNullForUnknownPaths() {
        FolderTree.Node root = phone();
        assertNull(FolderTree.find(root, "/nope"));
        assertNull(FolderTree.find(root, ""));
        assertNull(FolderTree.find(root, "/storage/emulated/0/Music/Jazz"));
    }

    @Test
    public void aSingleMusicFolderIsShownDirectly() {
        List<Track> all = new ArrayList<>();
        all.add(at("/storage/emulated/0/Music/x.mp3"));
        all.add(at("/storage/emulated/0/Music/y.mp3"));
        FolderTree.Node root = FolderTree.build(all);
        FolderTree.Node vroot = FolderTree.visibleRoot(root);
        assertEquals("/storage/emulated/0/Music", vroot.path);
        assertEquals("Internal storage › Music", FolderTree.breadcrumb(vroot));
        assertEquals(2, vroot.tracks.size());
    }

    @Test
    public void emptyLibraryHasAnEmptyRoot() {
        FolderTree.Node root = FolderTree.build(new ArrayList<Track>());
        assertEquals(0, root.total);
        assertSame(root, FolderTree.visibleRoot(root));
        assertTrue(FolderTree.entries(root).isEmpty());
    }

    @Test
    public void tracksInsideAFolderUseNaturalOrder() {
        List<Track> all = new ArrayList<>();
        all.add(at("/m/Track 10.mp3"));
        all.add(at("/m/Track 2.mp3"));
        all.add(at("/m/Track 1.mp3"));
        FolderTree.Node m = node(FolderTree.build(all), "/m");
        assertEquals("Track 1.mp3", m.tracks.get(0).fileName);
        assertEquals("Track 2.mp3", m.tracks.get(1).fileName);
        assertEquals("Track 10.mp3", m.tracks.get(2).fileName);
    }

    @Test
    public void friendlyVolumeNames() {
        assertEquals("Internal storage", FolderTree.friendly("/storage/emulated/0"));
        assertEquals("Internal storage", FolderTree.friendly("/sdcard"));
        assertEquals("SD card", FolderTree.friendly("/storage/extSdCard"));
        assertEquals("SD card or USB (AB12-CD34)", FolderTree.friendly("/storage/ab12-cd34"));
        assertEquals("SD card or USB (AB12-CD34)", FolderTree.friendly("/mnt/media_rw/AB12-CD34"));
        assertEquals("USB drive", FolderTree.friendly("/storage/usbotg"));
        assertEquals("USB drive", FolderTree.friendly("/storage/udisk"));
        assertEquals("USB drive", FolderTree.friendly("/mnt/usb_storage/USB_DISK2/udisk0"));
        assertNull(FolderTree.friendly("/storage/emulated/0/Music"));
        assertNull(FolderTree.friendly("/storage"));
    }

    @Test
    public void naturalOrderComparesNumbersByValue() {
        assertTrue(FolderTree.natural("Track 2", "Track 10") < 0);
        assertTrue(FolderTree.natural("Track 10", "Track 2") > 0);
        assertTrue(FolderTree.natural("a", "B") < 0);
        assertEquals(0, FolderTree.natural("same", "SAME"));
        assertTrue(FolderTree.natural("01 intro", "2 song") < 0);
        assertTrue(FolderTree.natural("song", "song 2") < 0);
    }
}
