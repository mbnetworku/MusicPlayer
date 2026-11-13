package com.musicplayer.lite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.Switch;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/** The vibration option. */
@RunWith(RobolectricTestRunner.class)
public class HapticsTest {
    /** Counts the touch feedback a view was asked for. */
    private static final class Counting extends View {
        int count;

        Counting(Application a) {
            super(a);
        }

        @Override
        public boolean performHapticFeedback(int feedbackConstant) {
            count++;
            return true;
        }
    }

    @Test
    public void tapsVibrateOnlyWhenTheOptionIsOn() {
        Application app = RuntimeEnvironment.getApplication();
        Prefs p = Prefs.get(app);
        Counting v = new Counting(app);
        assertTrue("on by default", p.vibrate());
        Haptics.tap(v);
        assertEquals(1, v.count);
        p.setVibrate(false);
        Haptics.tap(v);
        assertEquals("off: no buzz", 1, v.count);
        p.setVibrate(true);
    }

    @Test
    public void settingsHasTheSwitch() {
        Application app = RuntimeEnvironment.getApplication();
        Prefs.get(app).setVibrate(true);
        SettingsActivity a = Robolectric.buildActivity(SettingsActivity.class).create().start().resume().get();
        Switch s = a.findViewById(R.id.row_vibrate).findViewById(R.id.set_switch);
        assertTrue(s.isChecked());
        s.setChecked(false);
        assertEquals(false, Prefs.get(app).vibrate());
        Prefs.get(app).setVibrate(true);
    }

    @Test
    public void theMusicPulseRunsOnlyWhenOnAndPlaying() {
        Application app = RuntimeEnvironment.getApplication();
        Prefs p = Prefs.get(app);
        p.setVibratePlaying(false);
        BeatBuzz off = new BeatBuzz(app);
        off.setPlaying(true);
        org.robolectric.shadows.ShadowLooper.idleMainLooper(2000, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertEquals("off: no pulses", 0, off.pulses());

        p.setVibratePlaying(true);
        BeatBuzz on = new BeatBuzz(app);
        on.setPlaying(true);
        org.robolectric.shadows.ShadowLooper.idleMainLooper(2100, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertEquals("four pulses in two seconds", 4, on.pulses());
        on.setPlaying(false);
        org.robolectric.shadows.ShadowLooper.idleMainLooper(2000, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertEquals("paused: it stops", 4, on.pulses());
        p.setVibratePlaying(false);
    }

    @Test
    public void settingsHasBothVibrationSwitches() {
        Application app = RuntimeEnvironment.getApplication();
        SettingsActivity a = Robolectric.buildActivity(SettingsActivity.class).create().start().resume().get();
        Switch s = a.findViewById(R.id.row_vibrate_playing).findViewById(R.id.set_switch);
        assertEquals("off until turned on", false, s.isChecked());
        s.setChecked(true);
        assertTrue(Prefs.get(app).vibratePlaying());
        Prefs.get(app).setVibratePlaying(false);
    }
}
