package com.musicplayer.lite;

import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Looper;
import android.provider.MediaStore;

import org.robolectric.Robolectric;
import org.robolectric.shadows.ShadowLooper;

/** A fake MediaStore with five songs in three folders, for the Robolectric smoke tests. */
final class TestMedia {
    private TestMedia() {}

    /** id, title, artist, album, albumId, durationMs, track, path */
    static final Object[][] SONGS = {
            {1L, "Midnight Drive", "Neon Harbor", "Afterglow", 10L, 222000, 1, "/storage/emulated/0/Music/Afterglow/01 Midnight Drive.mp3"},
            {2L, "Glass Cities", "Neon Harbor", "Afterglow", 10L, 245000, 2, "/storage/emulated/0/Music/Afterglow/02 Glass Cities.mp3"},
            {3L, "Highway Hymn", "The Gravel Kings", "Open Road", 11L, 271000, 1, "/storage/emulated/0/Music/Open Road/01 Highway Hymn.mp3"},
            {4L, "Sunday Static", "Lumen", "Quiet Signals", 12L, 312000, 1, "/storage/emulated/0/Music/Quiet Signals/01 Sunday Static.mp3"},
            {5L, "Ember", "Okoro", "Fieldwork", 13L, 361000, 1, "/storage/1234-ABCD/Songs/Ember.mp3"},
    };

    /** The ids of the songs the fake MediaStore was asked to delete. */
    static final java.util.List<Long> DELETED = new java.util.ArrayList<>();

    public static final class Provider extends ContentProvider {
        @Override
        public boolean onCreate() {
            return true;
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
            MatrixCursor c = new MatrixCursor(projection);
            for (Object[] s : SONGS) {
                Object[] row = new Object[projection.length];
                for (int i = 0; i < projection.length; i++) row[i] = value(projection[i], s);
                c.addRow(row);
            }
            return c;
        }

        private static Object value(String column, Object[] s) {
            String path = (String) s[7];
            switch (column) {
                case MediaStore.Audio.Media._ID:
                    return s[0];
                case MediaStore.Audio.Media.TITLE:
                    return s[1];
                case MediaStore.Audio.Media.ARTIST:
                    return s[2];
                case MediaStore.Audio.Media.ALBUM:
                    return s[3];
                case MediaStore.Audio.Media.ALBUM_ID:
                    return s[4];
                case MediaStore.Audio.Media.DURATION:
                    return s[5];
                case MediaStore.Audio.Media.TRACK:
                    return s[6];
                case MediaStore.Audio.Media.DATA:
                    return path;
                case MediaStore.Audio.Media.DISPLAY_NAME:
                    return path.substring(path.lastIndexOf('/') + 1);
                case MediaStore.Audio.Media.VOLUME_NAME:
                    return "external_primary";
                default:
                    return null;
            }
        }

        @Override
        public String getType(Uri uri) {
            return "audio/mpeg";
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            return null;
        }

        @Override
        public int delete(Uri uri, String selection, String[] selectionArgs) {
            // Deleting a known song succeeds and is recorded; anything else deletes nothing.
            try {
                long id = Long.parseLong(uri.getLastPathSegment());
                for (Object[] song : SONGS) {
                    if ((Long) song[0] == id) {
                        DELETED.add(id);
                        return 1;
                    }
                }
            } catch (NumberFormatException ignored) {
                // Not a song URI.
            }
            return 0;
        }

        @Override
        public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
            return 0;
        }
    }

    /** The app keeps one Library, Prefs and ArtLoader per process; tests need fresh ones. */
    static void resetSingletons() {
        DELETED.clear();
        EqBarsView.resetClock();
        for (Class<?> c : new Class<?>[]{Library.class, Prefs.class, ArtLoader.class, Updater.class, Favorites.class, SoundBars.class}) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField("instance");
                f.setAccessible(true);
                f.set(null, null);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }
    }

    /** Grants the audio permission and installs the fake provider. */
    static void install(Application app) {
        resetSingletons();
        shadowOf(app).grantPermissions(Library.audioPermission());
        Robolectric.setupContentProvider(Provider.class, MediaStore.AUTHORITY);
    }

    /** Waits for the library's background load and lets the main thread deliver the result. */
    static void awaitLibrary(Library library) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!library.isLoaded() && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper();
            Thread.sleep(20);
        }
        ShadowLooper.idleMainLooper();
        if (!library.isLoaded()) throw new AssertionError("library did not load");
    }

    /** Makes {@code service} what the activities bind to. */
    static void exposeService(Application app, PlaybackService service) {
        shadowOf(app).setComponentNameAndServiceForBindService(
                new ComponentName(app, PlaybackService.class), service.onBind(null));
    }

    static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }
}
