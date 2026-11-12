package com.musicplayer.lite;

import android.content.ContentResolver;
import android.content.Context;
import android.content.IntentSender;
import android.os.Build;
import android.net.Uri;
import android.provider.MediaStore;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Deletes songs for good. How that works depends on the Android version:
 * <ul>
 *   <li>Android 11 and newer: the system deletes the files after the user confirms its own
 *       dialog (apps may not delete other apps' media silently).</li>
 *   <li>Android 10 and older: the app deletes them itself, once it has write access.</li>
 * </ul>
 * Either way the files are gone at once; nothing goes to a recycle bin.
 */
final class MediaDeleter {
    /** Android 10 and older, with write access: delete directly. */
    static final int DIRECT = 0;
    /** Android 10 and older, without write access: ask for it first. */
    static final int NEEDS_PERMISSION = 1;
    /** Android 11 and newer: hand the list to the system's delete request. */
    static final int SYSTEM = 2;

    private MediaDeleter() {}

    static int planFor(int sdk, boolean hasWriteAccess) {
        if (sdk >= 30) return SYSTEM;
        return hasWriteAccess ? DIRECT : NEEDS_PERMISSION;
    }

    static List<Uri> uris(List<Track> tracks) {
        List<Uri> out = new ArrayList<>(tracks.size());
        for (Track t : tracks) out.add(t.uri());
        return out;
    }

    static Set<Long> ids(List<Track> tracks) {
        Set<Long> out = new HashSet<>();
        for (Track t : tracks) out.add(t.id);
        return out;
    }

    /** Android 11 and newer: the system's "delete these files?" request. Throws if it cannot be made. */
    static IntentSender systemRequest(ContentResolver resolver, List<Track> tracks) {
        if (Build.VERSION.SDK_INT < 30) throw new UnsupportedOperationException("needs Android 11");
        return MediaStore.createDeleteRequest(resolver, uris(tracks)).getIntentSender();
    }

    /**
     * Android 10 and older: deletes each file and its MediaStore entry. Returns the ids of the
     * songs that are really gone.
     */
    static Set<Long> deleteDirect(Context c, List<Track> tracks) {
        ContentResolver resolver = c.getContentResolver();
        Set<Long> gone = new HashSet<>();
        for (Track t : tracks) {
            // MediaStore removes the file together with its entry when it is allowed to.
            boolean entryRemoved = tryDelete(resolver, t.uri());
            File file = new File(t.path());
            boolean fileDeleted = file.exists() && file.delete();
            if (entryRemoved) {
                gone.add(t.id);
            } else if (fileDeleted) {
                tryDelete(resolver, t.uri());   // drop the now stale entry; a media scan would too
                gone.add(t.id);
            }
        }
        return gone;
    }

    private static boolean tryDelete(ContentResolver resolver, Uri uri) {
        try {
            return resolver.delete(uri, null, null) > 0;
        } catch (RuntimeException e) {
            return false;   // not allowed to touch this entry
        }
    }
}
