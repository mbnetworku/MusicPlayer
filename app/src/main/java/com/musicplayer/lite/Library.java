package com.musicplayer.lite;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Every audio file on the device, read from MediaStore. The index is built on a
 * background thread and swapped in on the main thread, where it is read.
 */
final class Library {

    /** An album or an artist. */
    static final class Group {
        final String key;
        final String name;
        String subtitle = "";
        final List<Track> tracks = new ArrayList<>();

        Group(String key, String name) {
            this.key = key;
            this.name = name;
        }

        Track artTrack() {
            return tracks.isEmpty() ? null : tracks.get(0);
        }
    }

    interface Listener {
        void onLibraryChanged();
    }

    private static final class Snapshot {
        List<Track> tracks = Collections.emptyList();
        Map<Long, Track> byId = Collections.emptyMap();
        Map<String, Track> byPath = Collections.emptyMap();
        List<Group> albums = Collections.emptyList();
        List<Group> artists = Collections.emptyList();
        FolderTree.Node root = new FolderTree.Node("", "", null);
        int folders;
    }

    // Holds the application context only, so it cannot leak an Activity.
    @SuppressLint("StaticFieldLeak")
    private static Library instance;

    static synchronized Library get(Context c) {
        if (instance == null) instance = new Library(c.getApplicationContext());
        return instance;
    }

    static String audioPermission() {
        return Build.VERSION.SDK_INT >= 33
                ? android.Manifest.permission.READ_MEDIA_AUDIO
                : android.Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    static boolean hasPermission(Context c) {
        return c.checkSelfPermission(audioPermission()) == PackageManager.PERMISSION_GRANTED;
    }

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<Listener> listeners = new ArrayList<>();
    private final List<Runnable> waiting = new ArrayList<>();
    private final Runnable delayedRefresh = this::refresh;

    private Snapshot data = new Snapshot();
    private boolean loaded;
    private boolean loading;
    private boolean dirty;

    private Library(Context app) {
        this.app = app;
        // New files, a USB stick or an SD card being scanned: refresh shortly after.
        app.getContentResolver().registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, new ContentObserver(main) {
                    @Override
                    public void onChange(boolean selfChange) {
                        main.removeCallbacks(delayedRefresh);
                        main.postDelayed(delayedRefresh, 1500);
                    }
                });
    }

    // ---- access (main thread) -----------------------------------------------

    List<Track> tracks() {
        return data.tracks;
    }

    Track byId(long id) {
        return data.byId.get(id);
    }

    /** The song stored at this file path, or null. */
    Track byPath(String path) {
        return data.byPath.get(path);
    }

    List<Group> albums() {
        return data.albums;
    }

    List<Group> artists() {
        return data.artists;
    }

    FolderTree.Node folderRoot() {
        return data.root;
    }

    int folderCount() {
        return data.folders;
    }

    boolean isLoaded() {
        return loaded;
    }

    Group findGroup(boolean album, String key) {
        if (key == null) return null;
        for (Group g : album ? data.albums : data.artists) {
            if (g.key.equals(key)) return g;
        }
        return null;
    }

    List<Track> search(String query) {
        String q = query.trim();
        if (q.isEmpty()) return data.tracks;
        String[] tokens = q.split("\\s+");
        List<Track> out = new ArrayList<>();
        outer:
        for (Track t : data.tracks) {
            for (String k : tokens) {
                if (!(Fmt.containsIgnoreCase(t.title, k)
                        || Fmt.containsIgnoreCase(t.artist, k)
                        || Fmt.containsIgnoreCase(t.album, k)
                        || Fmt.containsIgnoreCase(t.fileName, k))) {
                    continue outer;
                }
            }
            out.add(t);
        }
        return out;
    }

    Set<String> knownPaths() {
        Set<String> s = new HashSet<>(data.tracks.size() * 2);
        for (Track t : data.tracks) s.add(t.path());
        return s;
    }

    void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    void removeListener(Listener l) {
        listeners.remove(l);
    }

    /** Runs {@code r} once the first load has finished (immediately if it already has). */
    void whenLoaded(Runnable r) {
        if (loaded) {
            r.run();
            return;
        }
        waiting.add(r);
        ensureLoaded();
    }

    void ensureLoaded() {
        if (!loaded && !loading) refresh();
    }

    // ---- loading ------------------------------------------------------------

    void refresh() {
        if (loading) {
            dirty = true;
            return;
        }
        loading = true;
        dirty = false;
        final boolean showAll = Prefs.get(app).showAllAudio();
        io.execute(() -> {
            final Snapshot s = query(showAll);
            main.post(() -> {
                data = s;
                loaded = true;
                loading = false;
                ArtLoader.get(app).forgetMisses();
                for (Listener l : new ArrayList<>(listeners)) l.onLibraryChanged();
                List<Runnable> run = new ArrayList<>(waiting);
                waiting.clear();
                for (Runnable r : run) r.run();
                if (dirty) refresh();
            });
        });
    }

    private Snapshot query(boolean showAll) {
        final Snapshot s = new Snapshot();
        final int sdk = Build.VERSION.SDK_INT;
        final String[] projection;
        if (sdk >= 29) {
            projection = new String[]{
                    MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.TRACK, MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.RELATIVE_PATH,
                    MediaStore.Audio.Media.VOLUME_NAME};
        } else {
            projection = new String[]{
                    MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.TRACK, MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.DISPLAY_NAME};
        }
        final String selection = showAll ? null
                : MediaStore.Audio.Media.IS_MUSIC + "!=0 AND "
                + MediaStore.Audio.Media.IS_ALARM + "=0 AND "
                + MediaStore.Audio.Media.IS_NOTIFICATION + "=0 AND "
                + MediaStore.Audio.Media.IS_RINGTONE + "=0";

        final String unknownArtist = app.getString(R.string.unknown_artist);
        final String unknownAlbum = app.getString(R.string.unknown_album);
        final Map<String, String> pool = new HashMap<>();
        final List<Track> out = new ArrayList<>();

        try (Cursor c = app.getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, null)) {
            if (c == null) return s;
            final int iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            final int iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            final int iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            final int iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
            final int iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
            final int iDuration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            final int iTrack = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK);
            final int iData = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
            final int iName = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);
            final int iRel = sdk >= 29 ? c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH) : -1;
            final int iVol = sdk >= 29 ? c.getColumnIndexOrThrow(MediaStore.Audio.Media.VOLUME_NAME) : -1;

            while (c.moveToNext()) {
                String path = c.getString(iData);
                String dir;
                String file;
                if (path != null && !path.isEmpty()) {
                    int slash = path.lastIndexOf('/');
                    dir = slash > 0 ? path.substring(0, slash) : "";
                    file = slash >= 0 ? path.substring(slash + 1) : path;
                } else {
                    String name = c.getString(iName);
                    dir = fallbackDir(iRel >= 0 ? c.getString(iRel) : null, iVol >= 0 ? c.getString(iVol) : null);
                    file = name == null ? "" : name;
                }
                String title = c.getString(iTitle);
                if (title == null || title.trim().isEmpty()) title = stripExtension(file);
                out.add(new Track(
                        c.getLong(iId),
                        title,
                        intern(pool, clean(c.getString(iArtist), unknownArtist)),
                        intern(pool, clean(c.getString(iAlbum), unknownAlbum)),
                        c.getLong(iAlbumId),
                        c.getInt(iDuration),
                        c.getInt(iTrack),
                        intern(pool, dir),
                        file));
            }
        } catch (RuntimeException e) {
            // No permission yet, or the provider is not ready: show an empty library.
            return s;
        }

        final Collator coll = Collator.getInstance();
        coll.setStrength(Collator.PRIMARY);
        Collections.sort(out, (a, b) -> {
            int r = coll.compare(a.title, b.title);
            if (r == 0) r = coll.compare(a.artist, b.artist);
            return r != 0 ? r : Long.compare(a.id, b.id);
        });

        final Map<Long, Track> byId = new HashMap<>(out.size() * 2);
        final Map<String, Track> byPath = new HashMap<>(out.size() * 2);
        for (Track t : out) {
            byId.put(t.id, t);
            byPath.put(t.path(), t);
        }

        s.tracks = Collections.unmodifiableList(out);
        s.byId = byId;
        s.byPath = byPath;
        s.albums = buildAlbums(out, coll);
        s.artists = buildArtists(out, coll);
        s.root = FolderTree.build(out);
        s.folders = FolderTree.countFolders(s.root);
        return s;
    }

    private List<Group> buildAlbums(List<Track> tracks, Collator coll) {
        Map<String, Group> map = new LinkedHashMap<>();
        for (Track t : tracks) {
            String key = t.albumId > 0 ? "a" + t.albumId : "n" + t.album.toLowerCase(Locale.ROOT);
            Group g = map.get(key);
            if (g == null) {
                g = new Group(key, t.album);
                map.put(key, g);
            }
            g.tracks.add(t);
        }
        final Comparator<Track> inAlbum = (a, b) -> {
            if (a.trackNo != b.trackNo) return Integer.compare(a.trackNo, b.trackNo);
            if (a.trackNo == 0) return FolderTree.natural(a.fileName, b.fileName);
            return coll.compare(a.title, b.title);
        };
        List<Group> out = new ArrayList<>(map.values());
        for (Group g : out) {
            Collections.sort(g.tracks, inAlbum);
            String first = g.tracks.get(0).artist;
            boolean various = false;
            for (Track t : g.tracks) {
                if (!t.artist.equals(first)) {
                    various = true;
                    break;
                }
            }
            int n = g.tracks.size();
            g.subtitle = (various ? app.getString(R.string.various_artists) : first)
                    + " · " + app.getResources().getQuantityString(R.plurals.songs_count, n, n);
        }
        Collections.sort(out, (a, b) -> coll.compare(a.name, b.name));
        return Collections.unmodifiableList(out);
    }

    private List<Group> buildArtists(List<Track> tracks, Collator coll) {
        Map<String, Group> map = new LinkedHashMap<>();
        for (Track t : tracks) {
            String key = t.artist.toLowerCase(Locale.ROOT);
            Group g = map.get(key);
            if (g == null) {
                g = new Group(key, t.artist);
                map.put(key, g);
            }
            g.tracks.add(t);
        }
        final Comparator<Track> inArtist = (a, b) -> {
            int r = coll.compare(a.album, b.album);
            if (r != 0) return r;
            if (a.trackNo != b.trackNo) return Integer.compare(a.trackNo, b.trackNo);
            return coll.compare(a.title, b.title);
        };
        List<Group> out = new ArrayList<>(map.values());
        for (Group g : out) {
            Collections.sort(g.tracks, inArtist);
            Set<String> albums = new HashSet<>();
            for (Track t : g.tracks) albums.add(t.album);
            int na = albums.size();
            int ns = g.tracks.size();
            g.subtitle = app.getResources().getQuantityString(R.plurals.albums_count, na, na)
                    + " · " + app.getResources().getQuantityString(R.plurals.songs_count, ns, ns);
        }
        Collections.sort(out, (a, b) -> coll.compare(a.name, b.name));
        return Collections.unmodifiableList(out);
    }

    // ---- helpers --------------------------------------------------------------

    private static String fallbackDir(String relative, String volume) {
        String root = volume == null || volume.startsWith("external_primary") || volume.equals("external")
                ? "/storage/emulated/0"
                : "/storage/" + volume.toUpperCase(Locale.ROOT);
        String r = relative == null ? "" : relative;
        while (r.endsWith("/")) r = r.substring(0, r.length() - 1);
        return r.isEmpty() ? root : root + "/" + r;
    }

    private static String clean(String v, String fallback) {
        if (v == null) return fallback;
        String t = v.trim();
        return t.isEmpty() || t.equals("<unknown>") ? fallback : t;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String intern(Map<String, String> pool, String s) {
        String p = pool.get(s);
        if (p == null) {
            pool.put(s, s);
            p = s;
        }
        return p;
    }
}
