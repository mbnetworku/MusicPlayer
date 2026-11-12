package com.musicplayer.lite;

import android.annotation.SuppressLint;
import android.content.Context;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * The songs the user marked with a heart, newest first.
 *
 * <p>A song is remembered by its file path, not by its MediaStore id: ids can change when the
 * library is scanned again (a new SD card, a cleared media database), the path does not. If the
 * file is gone the heart is simply not shown, and it comes back if the file does. Songs the user
 * deletes through the app are forgotten right away.
 *
 * <p>Used from the main thread only.
 */
final class Favorites {

    /** Where the list is kept between runs. */
    interface Store {
        String load();

        void save(String text);
    }

    interface Listener {
        void onFavoritesChanged();
    }

    // Holds the application context only (through Prefs), so it cannot leak an Activity.
    @SuppressLint("StaticFieldLeak")
    private static Favorites instance;

    static synchronized Favorites get(Context c) {
        if (instance == null) {
            final Prefs prefs = Prefs.get(c);
            instance = new Favorites(new Store() {
                @Override
                public String load() {
                    return prefs.favorites();
                }

                @Override
                public void save(String text) {
                    prefs.setFavorites(text);
                }
            });
        }
        return instance;
    }

    private final Store store;
    private final List<String> order = new ArrayList<>();
    private final Set<String> members = new HashSet<>();
    private final List<Listener> listeners = new ArrayList<>();

    Favorites(Store store) {
        this.store = store;
        for (String key : decode(store.load())) {
            if (members.add(key)) order.add(key);
        }
    }

    /** What identifies a song in the list. */
    static String keyOf(Track t) {
        return t.path();
    }

    // ---- reading ------------------------------------------------------------------------------

    boolean contains(Track t) {
        return t != null && members.contains(keyOf(t));
    }

    /** True when every one of the songs is a favorite (and there is at least one). */
    boolean containsAll(Collection<Track> tracks) {
        if (tracks.isEmpty()) return false;
        for (Track t : tracks) {
            if (!contains(t)) return false;
        }
        return true;
    }

    /** How many songs are remembered, including any whose file is not on the device right now. */
    int size() {
        return order.size();
    }

    /** The favorites that exist in the library, newest first. */
    List<Track> tracks(Library library) {
        return tracks(library::byPath);
    }

    /** The favorites that {@code byPath} can find (it gives null for a file that is not there), newest first. */
    List<Track> tracks(Function<String, Track> byPath) {
        List<Track> out = new ArrayList<>(order.size());
        for (String key : order) {
            Track t = byPath.apply(key);
            if (t != null) out.add(t);
        }
        return out;
    }

    // ---- changing -----------------------------------------------------------------------------

    /** Adds the song if it is not a favorite, removes it if it is. Returns true if it is one now. */
    boolean toggle(Track t) {
        if (contains(t)) {
            remove(java.util.Collections.singletonList(t));
            return false;
        }
        add(java.util.Collections.singletonList(t));
        return true;
    }

    /**
     * Puts the songs at the top of the list, in the order given (an album stays in album order).
     * Songs that are already favorites keep their place. Returns how many were new.
     */
    int add(Collection<Track> tracks) {
        List<String> fresh = new ArrayList<>();
        for (Track t : tracks) {
            String key = keyOf(t);
            if (members.add(key)) fresh.add(key);
        }
        if (fresh.isEmpty()) return 0;
        order.addAll(0, fresh);
        changed();
        return fresh.size();
    }

    /** Takes the songs off the list. Returns how many were on it. */
    int remove(Collection<Track> tracks) {
        int gone = 0;
        for (Track t : tracks) {
            String key = keyOf(t);
            if (members.remove(key)) {
                order.remove(key);
                gone++;
            }
        }
        if (gone > 0) changed();
        return gone;
    }

    void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void changed() {
        store.save(encode(order));
        for (Listener l : new ArrayList<>(listeners)) l.onFavoritesChanged();
    }

    // ---- saving -------------------------------------------------------------------------------

    /** One path per line. A backslash or a line break inside a path is written as \\ or \n. */
    static String encode(List<String> keys) {
        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            if (sb.length() > 0) sb.append('\n');
            for (int i = 0; i < key.length(); i++) {
                char ch = key.charAt(i);
                if (ch == '\\') sb.append("\\\\");
                else if (ch == '\n') sb.append("\\n");
                else if (ch != '\r') sb.append(ch);
            }
        }
        return sb.toString();
    }

    static List<String> decode(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        StringBuilder line = new StringBuilder();
        for (int i = 0; i <= text.length(); i++) {
            char ch = i < text.length() ? text.charAt(i) : '\n';
            if (ch == '\n') {
                if (line.length() > 0) out.add(line.toString());
                line.setLength(0);
            } else if (ch == '\\' && i + 1 < text.length()) {
                char next = text.charAt(++i);
                line.append(next == 'n' ? '\n' : next);
            } else {
                line.append(ch);
            }
        }
        return out;
    }
}
