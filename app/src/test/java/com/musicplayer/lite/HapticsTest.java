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
    public void theBuzzFollowsTheEnvelopeOnlyWhenOnAndPlaying() {
        Application app = RuntimeEnvironment.getApplication();
        Prefs p = Prefs.get(app);
        final int[] position = {0};
        float[] heavy = new float[100];
        java.util.Arrays.fill(heavy, 1f);
        p.setVibratePlaying(false);
        BeatBuzz off = new BeatBuzz(app, () -> position[0]);
        off.setPlaying(true);
        off.useEnvelope(heavy);
        org.robolectric.shadows.ShadowLooper.idleMainLooper(1000, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertEquals("off: no buzz", 0, off.buzzes());

        p.setVibratePlaying(true);
        BeatBuzz on = new BeatBuzz(app, () -> position[0]);
        on.setPlaying(true);
        on.useEnvelope(heavy);
        org.robolectric.shadows.ShadowLooper.idleMainLooper(1000, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertTrue("heavy music buzzes", on.buzzes() > 10);
        assertTrue("and strongly", on.lastStrength() > 200);
        int before = on.buzzes();
        on.setPlaying(false);
        org.robolectric.shadows.ShadowLooper.idleMainLooper(1000, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertEquals("paused: it stops", before, on.buzzes());
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
