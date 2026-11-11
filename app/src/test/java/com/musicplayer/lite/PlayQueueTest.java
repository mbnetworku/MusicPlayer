package com.musicplayer.lite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class PlayQueueTest {

    private static Track track(int id) {
        return new Track(id, "T" + id, "Artist", "Album", 1, 1000, id, "/music", "f" + id + ".mp3");
    }

    private static List<Track> tracks(int n) {
        List<Track> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(track(i));
        return out;
    }

    private static PlayQueue queue(int n, int start) {
        PlayQueue q = new PlayQueue(new Random(42));
        q.set(tracks(n), start, false);
        return q;
    }

    @Test
    public void startsAtRequestedTrackAndPlaysInOrder() {
        PlayQueue q = queue(5, 2);
        assertEquals(2, q.current().id);
        assertEquals(3, q.advance(false).id);
        assertEquals(4, q.advance(false).id);
        assertNull("end of queue with repeat off stops", q.advance(false));
        assertEquals("position is unchanged when stopping", 4, q.current().id);
    }

    @Test
    public void emptyQueueHasNoCurrent() {
        PlayQueue q = new PlayQueue();
        assertNull(q.current());
        assertNull(q.advance(true));
        assertNull(q.retreat());
        assertEquals(0, q.size());
    }

    @Test
    public void repeatAllWrapsAround() {
        PlayQueue q = queue(3, 2);
        q.setRepeat(PlayQueue.REPEAT_ALL);
        assertEquals(0, q.advance(false).id);
    }

    @Test
    public void repeatOneStaysOnAutoAdvanceButNextButtonMoves() {
        PlayQueue q = queue(3, 1);
        q.setRepeat(PlayQueue.REPEAT_ONE);
        assertEquals(1, q.advance(false).id);
        assertEquals(2, q.advance(true).id);
    }

    @Test
    public void nextButtonWrapsEvenWithRepeatOff() {
        PlayQueue q = queue(3, 2);
        assertEquals(0, q.advance(true).id);
    }

    @Test
    public void previousStopsAtFirstUnlessRepeatAll() {
        PlayQueue q = queue(3, 0);
        assertEquals(0, q.retreat().id);
        q.setRepeat(PlayQueue.REPEAT_ALL);
        assertEquals(2, q.retreat().id);
        assertEquals(1, q.retreat().id);
    }

    @Test
    public void repeatCyclesOffAllOne() {
        PlayQueue q = queue(2, 0);
        assertEquals(PlayQueue.REPEAT_ALL, q.cycleRepeat());
        assertEquals(PlayQueue.REPEAT_ONE, q.cycleRepeat());
        assertEquals(PlayQueue.REPEAT_OFF, q.cycleRepeat());
    }

    @Test
    public void shuffleKeepsEveryTrackOnceAndStartTrackFirst() {
        PlayQueue q = new PlayQueue(new Random(7));
        q.set(tracks(20), 13, true);
        assertEquals(13, q.current().id);
        assertEquals(0, q.position());
        Set<Long> seen = new HashSet<>();
        for (Track t : q.tracks()) seen.add(t.id);
        assertEquals(20, seen.size());
        assertEquals(20, q.size());
    }

    @Test
    public void turningShuffleOffRestoresOriginalOrderAtCurrentTrack() {
        PlayQueue q = new PlayQueue(new Random(7));
        q.set(tracks(10), 4, true);
        q.advance(false);
        q.advance(false);
        Track playing = q.current();
        q.setShuffle(false);
        assertFalse(q.isShuffle());
        assertSame(playing, q.current());
        assertEquals(playing.id, q.position());
        for (int i = 0; i < q.size(); i++) assertEquals(i, q.tracks().get(i).id);
    }

    @Test
    public void turningShuffleOnKeepsCurrentTrackPlaying() {
        PlayQueue q = queue(10, 6);
        q.setShuffle(true);
        assertEquals(6, q.current().id);
        assertEquals(0, q.position());
    }

    @Test
    public void reshuffleOnWrapDoesNotRepeatTheLastTrackFirst() {
        for (int seed = 0; seed < 50; seed++) {
            PlayQueue q = new PlayQueue(new Random(seed));
            q.set(tracks(4), 0, true);
            q.setRepeat(PlayQueue.REPEAT_ALL);
            for (int i = 0; i < 3; i++) q.advance(false); // play through to the last track
            Track last = q.current();
            Track first = q.advance(false);               // wraps and reshuffles
            assertNotEquals("seed " + seed, last.id, first.id);
        }
    }

    @Test
    public void playNextInsertsRightAfterCurrent() {
        PlayQueue q = queue(3, 1);
        Track extra = track(99);
        q.playNext(extra);
        assertEquals(99, q.advance(false).id);
        assertEquals(2, q.advance(false).id);
        q.setShuffle(true);
        q.setShuffle(false);
        assertTrue("still in the unshuffled list", q.tracks().contains(extra));
    }

    @Test
    public void addAppendsToTheEnd() {
        PlayQueue q = queue(2, 0);
        q.add(track(50));
        assertEquals(3, q.size());
        assertEquals(50, q.tracks().get(2).id);
    }

    @Test
    public void addToEmptyQueueMakesItCurrent() {
        PlayQueue q = new PlayQueue();
        q.add(track(5));
        assertEquals(5, q.current().id);
    }

    @Test
    public void removeBeforeCurrentKeepsCurrentTrack() {
        PlayQueue q = queue(5, 3);
        assertFalse(q.remove(0));
        assertEquals(3, q.current().id);
        assertEquals(2, q.position());
    }

    @Test
    public void removeCurrentReportsItAndMovesToNext() {
        PlayQueue q = queue(5, 2);
        assertTrue(q.remove(2));
        assertEquals(3, q.current().id);
    }

    @Test
    public void removeCurrentAtEndMovesToNewLastTrack() {
        PlayQueue q = queue(3, 2);
        assertTrue(q.remove(2));
        assertEquals(1, q.current().id);
    }

    @Test
    public void removingEverythingLeavesNoCurrent() {
        PlayQueue q = queue(1, 0);
        assertTrue(q.remove(0));
        assertNull(q.current());
        assertEquals(0, q.size());
    }

    @Test
    public void removeAfterCurrentDoesNotMoveIt() {
        PlayQueue q = queue(5, 1);
        assertFalse(q.remove(4));
        assertEquals(1, q.current().id);
    }

    @Test
    public void versionChangesWhenTheListChangesButNotWhenMoving() {
        PlayQueue q = queue(5, 0);
        int v = q.version();
        q.advance(false);
        q.jumpTo(3);
        assertEquals(v, q.version());
        q.add(track(9));
        assertNotEquals(v, q.version());
        int v2 = q.version();
        q.remove(0);
        assertNotEquals(v2, q.version());
    }

    @Test
    public void restoreRebuildsTheQueueAtTheSavedTrack() {
        PlayQueue q = new PlayQueue();
        q.restore(tracks(6), 4, true, PlayQueue.REPEAT_ALL);
        assertEquals(4, q.current().id);
        assertTrue(q.isShuffle());
        assertEquals(PlayQueue.REPEAT_ALL, q.repeat());
    }

    @Test
    public void startIndexIsClamped() {
        PlayQueue q = new PlayQueue();
        q.set(tracks(3), 99, false);
        assertEquals(2, q.current().id);
        q.set(tracks(3), -4, false);
        assertEquals(0, q.current().id);
    }
}
