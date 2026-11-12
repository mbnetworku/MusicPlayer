package com.musicplayer.lite;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/** The equalizer of the whole device: reading it, adopting it and controlling it. */
@RunWith(RobolectricTestRunner.class)
public class DeviceEqTest {

    /** A pretend device equalizer with five bands. */
    static final class FakeEq implements DeviceEq.Backend {
        boolean enabled;
        boolean control = true;
        int preset = -1;
        int[] levels = new int[5];
        boolean released;
        int writes;

        @Override public int bands() { return levels.length; }
        @Override public int[] range() { return new int[]{-1500, 1500}; }
        @Override public int center(int band) { return 60_000 * (band + 1); }
        @Override public boolean enabled() { return enabled; }
        @Override public int level(int band) { return levels[band]; }
        @Override public int preset() { return preset; }
        @Override public String presetName(int index) { return index == 4 ? "Rock" : "Preset " + index; }
        @Override public boolean hasControl() { return control; }
        @Override public void setEnabled(boolean on) { enabled = on; writes++; }
        @Override public void setLevel(int band, int millibels) { levels[band] = millibels; writes++; }
        @Override public void release() { released = true; }
    }

    private Application app;
    private Prefs prefs;

    @Before
    public void setUp() {
        TestMedia.resetSingletons();
        app = RuntimeEnvironment.getApplication();
        prefs = Prefs.get(app);
    }

    /** The controller's own (player) equalizer needs a real audio session, so these tests run without one. */
    private EffectsController controller(final FakeEq eq) {
        return new EffectsController(app, 0, () -> eq);
    }

    @Test
    public void readsWhatTheDeviceSays() {
        FakeEq eq = new FakeEq();
        eq.enabled = true;
        eq.preset = 4;
        eq.levels = new int[]{500, 300, 0, -200, 100};
        DeviceEq.State s = DeviceEq.open(() -> eq).read();
        assertNotNull(s);
        assertTrue(s.enabled);
        assertEquals("Rock", s.presetName);
        assertArrayEquals(new int[]{500, 300, 0, -200, 100}, s.levels);
        assertTrue(s.control);
    }

    @Test
    public void aDeviceThatRefusesIsNotAvailable() {
        assertNull(DeviceEq.open(() -> {
            throw new UnsupportedOperationException("no global effects here");
        }));
        assertNull("no equalizer at all", DeviceEq.open(() -> null));
        assertNull("an effect with no bands is useless", DeviceEq.open(() -> {
            FakeEq none = new FakeEq();
            none.levels = new int[0];
            return none;
        }));
    }

    @Test
    public void aMissingNativeLibraryIsNotACrash() {
        assertNull(DeviceEq.open(() -> {
            throw new UnsatisfiedLinkError("libaudioeffect_jni.so");
        }));
    }

    @Test
    public void writingNeedsControl() {
        FakeEq eq = new FakeEq();
        eq.control = false;
        DeviceEq d = DeviceEq.open(() -> eq);
        assertFalse(d.write(true, new int[]{1, 2, 3, 4, 5}));
        assertFalse("another app holds control, so nothing was written", eq.enabled);
        assertEquals(0, eq.writes);
        assertFalse(d.read().control);
    }

    @Test
    public void theControllerReportsTheDeviceStateWithoutChangingAnything() {
        FakeEq eq = new FakeEq();
        eq.enabled = true;
        eq.preset = 4;
        EffectsController fx = controller(eq);
        DeviceEq.State s = fx.deviceState();
        assertNotNull(s);
        assertTrue(s.enabled);
        assertEquals("Rock", s.presetName);
        assertFalse("looking is not controlling", fx.isDeviceMode());
        assertTrue("the probe was closed again", eq.released);
        assertEquals(0, eq.writes);
    }

    @Test
    public void whenTheDeviceDoesNotAllowItThereIsNoState() {
        EffectsController fx = new EffectsController(app, 0, () -> {
            throw new RuntimeException("nope");
        });
        assertNull(fx.deviceState());
        assertFalse("and it cannot be controlled", fx.setDeviceMode(true));
        assertFalse(prefs.eqDevice());
    }

    @Test
    public void controllingTheDeviceIsRememberedAndCanBeStopped() {
        FakeEq eq = new FakeEq();
        EffectsController fx = controller(eq);
        assertTrue(fx.setDeviceMode(true));
        assertTrue(fx.isDeviceMode());
        assertTrue(prefs.eqDevice());
        assertTrue(fx.setDeviceMode(false));
        assertFalse(fx.isDeviceMode());
        assertFalse(prefs.eqDevice());
        assertTrue("the device equalizer is let go", eq.released);
    }

    @Test
    public void aDeviceHeldByAnotherAppCannotBeControlled() {
        FakeEq eq = new FakeEq();
        eq.control = false;
        EffectsController fx = controller(eq);
        assertFalse(fx.setDeviceMode(true));
        assertFalse(fx.isDeviceMode());
        assertTrue(eq.released);
    }
}
