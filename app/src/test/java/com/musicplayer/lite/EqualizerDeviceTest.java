package com.musicplayer.lite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.widget.Switch;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;

/** What the equalizer screen tells you about the device's own equalizer. */
@RunWith(RobolectricTestRunner.class)
public class EqualizerDeviceTest {
    private Application app;

    @Before
    public void setUp() {
        TestMedia.resetSingletons();
        app = RuntimeEnvironment.getApplication();
    }

    @Test
    public void whenTheDeviceDoesNotAllowItTheScreenSaysSoPlainly() throws Exception {
        TestMedia.install(app);
        PlaybackService service = Robolectric.buildService(PlaybackService.class).create().get();
        TestMedia.exposeService(app, service);
        ActivityController<EqualizerActivity> c =
                Robolectric.buildActivity(EqualizerActivity.class).create().start().resume().visible();
        TestMedia.idle();
        EqualizerActivity a = c.get();

        // This test runtime has no audio effect engine, which is what a device that refuses looks like.
        assertEquals(app.getString(R.string.eq_dev_unavailable),
                ((TextView) a.findViewById(R.id.eq_dev_status)).getText().toString());
        Switch control = a.findViewById(R.id.row_eq_device).findViewById(R.id.set_switch);
        assertFalse("nothing to control", control.isEnabled());
        assertFalse(control.isChecked());
        assertFalse("nothing to read", a.findViewById(R.id.eq_dev_read).isEnabled());
        assertTrue(a.findViewById(R.id.eq_device).getVisibility() == android.view.View.VISIBLE);
        c.pause().stop().destroy();
    }
}
