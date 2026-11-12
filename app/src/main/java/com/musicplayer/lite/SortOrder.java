package com.musicplayer.lite;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** How the song lists are ordered. The choice is kept in {@link Prefs}. */
final class SortOrder {
    static final int TITLE = 0, ARTIST = 1, ALBUM = 2, RECENT = 3, LENGTH = 4;
    static final int COUNT = 5;

    private SortOrder() {}

    /** A sorted copy of {@code tracks}. Title order, not reversed, is the library's own order. */
    static List<Track> apply(List<Track> tracks, int key, boolean reverse) {
        if (key == TITLE && !reverse) return tracks;
        final Collator coll = Collator.getInstance();
        coll.setStrength(Collator.PRIMARY);
        final Comparator<Track> byTitle = (a, b) -> {
            int r = coll.compare(a.title, b.title);
            if (r == 0) r = coll.compare(a.artist, b.artist);
            return r != 0 ? r : Long.compare(a.id, b.id);
        };
        Comparator<Track> c;
        switch (key) {
            case ARTIST:
                c = (a, b) -> {
                    int r = coll.compare(a.artist, b.artist);
                    return r != 0 ? r : byTitle.compare(a, b);
                };
                break;
            case ALBUM:
                c = (a, b) -> {
                    int r = coll.compare(a.album, b.album);
                    if (r == 0) r = Integer.compare(a.trackNo, b.trackNo);
                    return r != 0 ? r : byTitle.compare(a, b);
                };
                break;
            case RECENT:   // files added later get larger ids: newest first
                c = (a, b) -> Long.compare(b.id, a.id);
                break;
            case LENGTH:   // longest first
                c = (a, b) -> {
                    int r = Integer.compare(b.durationMs, a.durationMs);
                    return r != 0 ? r : byTitle.compare(a, b);
                };
                break;
            default:
                c = byTitle;
                break;
        }
        if (reverse) c = Collections.reverseOrder(c);
        final List<Track> out = new ArrayList<>(tracks);
        Collections.sort(out, c);
        return out;
    }
}
