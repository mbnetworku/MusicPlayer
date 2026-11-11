package com.musicplayer.lite;

import android.net.Uri;

import org.robolectric.shadows.ShadowMediaPlayer;
import org.robolectric.shadows.util.DataSource;

/** Tells Robolectric's fake MediaPlayer that the test songs exist, so they can "play". */
final class FakePlayback {
    private FakePlayback() {}

    static void register() {
        for (Object[] s : TestMedia.SONGS) {
            Uri uri = android.content.ContentUris.withAppendedId(
                    android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, (Long) s[0]);
            ShadowMediaPlayer.addMediaInfo(
                    DataSource.toDataSource(org.robolectric.RuntimeEnvironment.getApplication(), uri),
                    new ShadowMediaPlayer.MediaInfo((Integer) s[5], 0));
        }
    }
}
