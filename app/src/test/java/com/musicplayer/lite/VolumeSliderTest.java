package com.musicplayer.lite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.media.AudioManager;
import android.view.View;
import android.widget.SeekBar;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/** The volume slider on the Now Playing screen. */
@RunWith(RobolectricTestRunner.class)
public class VolumeSliderTest {
    @Test
    public void theSliderShowsAndChangesThePhonesMusicVolume() {
        Application app = RuntimeEnvironment.getApplication();
        TestMedia.install(app);
        AudioManager am = app.getSystemService(AudioManager.class);
        am.setStreamVolume(AudioManager.STREAM_MUSIC, 3, 0);

        PlayerActivity a = Robolectric.buildActivity(PlayerActivity.class).create().start().resume().visible().get();
        SeekBar bar = a.findViewById(R.id.np_volume);
        assertEquals(View.VISIBLE, a.findViewById(R.id.np_volume_row).getVisibility());
        assertEquals("shows the current volume", 3, bar.getProgress());
        assertEquals(am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), bar.getMax());

        // A finger drags the slider.
        bar.setProgress(7);
        assertTrue(bar.getProgress() == 7);
    }
}
