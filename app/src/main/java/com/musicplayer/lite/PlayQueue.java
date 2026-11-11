package com.musicplayer.lite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The play order, shuffle and repeat rules. No Android dependencies besides
 * {@link Track}, so it is unit-tested on the JVM.
 */
final class PlayQueue {
    static final int REPEAT_OFF = 0;
    static final int REPEAT_ALL = 1;
    static final int REPEAT_ONE = 2;

    private final Random rnd;
    /** The order the tracks were handed to us in; restored when shuffle goes off. */
    private final List<Track> original = new ArrayList<>();
    /** The order that will actually play. */
    private final List<Track> order = new ArrayList<>();
    private int pos = -1;
    private boolean shuffle;
    private int repeat = REPEAT_OFF;
    private int version;

    PlayQueue() {
        this(new Random());
    }

    PlayQueue(Random rnd) {
        this.rnd = rnd;
    }

    /** Replaces the queue. With shuffle on, {@code start} still plays first. */
    void set(List<Track> tracks, int start, boolean shuffleOn) {
        version++;
        original.clear();
        original.addAll(tracks);
        order.clear();
        shuffle = shuffleOn;
        if (original.isEmpty()) {
            pos = -1;
            return;
        }
        start = Fmt.clamp(start, 0, original.size() - 1);
        if (shuffle) {
            buildShuffled(original.get(start));
            pos = 0;
        } else {
            order.addAll(original);
            pos = start;
        }
    }

    /** Restores a saved queue: {@code tracks} is the order that was playing. */
    void restore(List<Track> tracks, int index, boolean shuffleOn, int repeatMode) {
        version++;
        original.clear();
        original.addAll(tracks);
        order.clear();
        order.addAll(tracks);
        shuffle = shuffleOn;
        repeat = repeatMode;
        pos = tracks.isEmpty() ? -1 : Fmt.clamp(index, 0, tracks.size() - 1);
    }

    private void buildShuffled(Track first) {
        List<Track> rest = new ArrayList<>(original);
        if (first != null) rest.remove(first);
        Collections.shuffle(rest, rnd);
        order.clear();
        if (first != null) order.add(first);
        order.addAll(rest);
    }

    Track current() {
        return pos >= 0 && pos < order.size() ? order.get(pos) : null;
    }

    int size() {
        return order.size();
    }

    /** Changes whenever the list of tracks (not just the position) changes. */
    int version() {
        return version;
    }

    int position() {
        return pos;
    }

    List<Track> tracks() {
        return Collections.unmodifiableList(order);
    }

    boolean isShuffle() {
        return shuffle;
    }

    int repeat() {
        return repeat;
    }

    void setRepeat(int mode) {
        repeat = mode;
    }

    int cycleRepeat() {
        repeat = (repeat + 1) % 3;
        return repeat;
    }

    /**
     * Moves to the next track. {@code user} is true for the Next button, false for
     * a track that finished by itself. Returns null when playback should stop.
     */
    Track advance(boolean user) {
        if (order.isEmpty()) return null;
        if (!user && repeat == REPEAT_ONE) return current();
        if (pos + 1 < order.size()) {
            pos++;
            return current();
        }
        if (repeat == REPEAT_ALL || user) {
            if (shuffle && order.size() > 1) {
                version++;
                reshuffle();
            }
            pos = 0;
            return current();
        }
        return null;
    }

    /** Moves to the previous track (or stays on the first one). */
    Track retreat() {
        if (order.isEmpty()) return null;
        if (pos > 0) {
            pos--;
        } else if (repeat == REPEAT_ALL) {
            pos = order.size() - 1;
        }
        return current();
    }

    private void reshuffle() {
        Track last = order.get(order.size() - 1);
        Collections.shuffle(order, rnd);
        if (order.get(0) == last) {
            Collections.swap(order, 0, 1 + rnd.nextInt(order.size() - 1));
        }
    }

    void jumpTo(int index) {
        if (index >= 0 && index < order.size()) pos = index;
    }

    void setShuffle(boolean on) {
        if (on == shuffle) return;
        shuffle = on;
        if (order.isEmpty()) return;
        version++;
        Track cur = current();
        if (on) {
            buildShuffled(cur);
            pos = 0;
        } else {
            order.clear();
            order.addAll(original);
            pos = Math.max(0, indexOf(order, cur));
        }
    }

    /** Inserts right after the current track. */
    void playNext(Track t) {
        if (order.isEmpty()) {
            set(Collections.singletonList(t), 0, false);
            return;
        }
        Track cur = current();
        version++;
        order.add(pos + 1, t);
        int oi = indexOf(original, cur);
        original.add(oi < 0 ? original.size() : oi + 1, t);
    }

    void add(Track t) {
        if (order.isEmpty()) {
            set(Collections.singletonList(t), 0, false);
            return;
        }
        version++;
        order.add(t);
        original.add(t);
    }

    /** Removes the entry at {@code index}. Returns true when it was the current track. */
    boolean remove(int index) {
        if (index < 0 || index >= order.size()) return false;
        version++;
        Track t = order.remove(index);
        int oi = indexOf(original, t);
        if (oi >= 0) original.remove(oi);
        if (index < pos) {
            pos--;
            return false;
        }
        if (index == pos) {
            if (pos >= order.size()) pos = order.size() - 1;
            return true;
        }
        return false;
    }

    private static int indexOf(List<Track> list, Track t) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == t) return i;
        }
        return -1;
    }
}
