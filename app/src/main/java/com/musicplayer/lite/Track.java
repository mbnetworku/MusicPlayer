package com.musicplayer.lite;

import android.content.ContentUris;
import android.net.Uri;
import android.provider.MediaStore;

/** One audio file known to MediaStore. Immutable. */
final class Track {
    final long id;
    final String title;
    final String artist;
    final String album;
    final long albumId;
    final int durationMs;
    /** Disc*1000 + track number, as MediaStore stores it. 0 when unknown. */
    final int trackNo;
    /** Absolute parent directory, no trailing slash ("" when unknown). */
    final String dir;
    final String fileName;

    private Uri uri;

    Track(long id, String title, String artist, String album, long albumId,
          int durationMs, int trackNo, String dir, String fileName) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.albumId = albumId;
        this.durationMs = durationMs;
        this.trackNo = trackNo;
        this.dir = dir;
        this.fileName = fileName;
    }

    String path() {
        return dir + "/" + fileName;
    }

    Uri uri() {
        Uri u = uri;
        if (u == null) {
            u = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
            uri = u;
        }
        return u;
    }

    /** Tracks of one album share artwork, so cache by album when we know it. */
    String artKey() {
        return albumId > 0 ? "a" + albumId : "t" + id;
    }
}
