package com.musicplayer.lite;

import android.media.audiofx.Equalizer;

/**
 * The Android equalizer of the whole device (the "output mix", audio session 0), as far as the
 * device lets an app see and change it. Many devices and Android versions block it, and an
 * effect like this only lives while the app that created it keeps it open, so everything here
 * is best effort: {@link #open} returns null when the device says no.
 */
final class DeviceEq {
    /** The few things we need from an equalizer, so tests can supply a fake one. */
    interface Backend {
        int bands();

        /** {minimum, maximum} band level in millibels. */
        int[] range();

        int center(int band);

        boolean enabled();

        int level(int band);

        /** Index of the device's current preset, or -1. */
        int preset();

        String presetName(int index);

        /** Whether this app may change the equalizer (another app may hold control). */
        boolean hasControl();

        void setEnabled(boolean on);

        void setLevel(int band, int millibels);

        void release();
    }

    interface Opener {
        /** Returns the equalizer, or null when the device does not offer it. May throw on devices that refuse. */
        Backend open();
    }

    /** What was read from the device. */
    static final class State {
        final boolean enabled;
        final int[] levels;
        final int preset;
        final String presetName;
        final boolean control;

        State(boolean enabled, int[] levels, int preset, String presetName, boolean control) {
            this.enabled = enabled;
            this.levels = levels;
            this.preset = preset;
            this.presetName = presetName;
            this.control = control;
        }
    }

    /** Opens the platform's equalizer on the output mix. */
    static final Opener PLATFORM = () -> new Backend() {
        private final Equalizer eq = new Equalizer(0, 0);

        @Override
        public int bands() {
            return eq.getNumberOfBands();
        }

        @Override
        public int[] range() {
            short[] r = eq.getBandLevelRange();
            return new int[]{r[0], r[1]};
        }

        @Override
        public int center(int band) {
            return eq.getCenterFreq((short) band);
        }

        @Override
        public boolean enabled() {
            return eq.getEnabled();
        }

        @Override
        public int level(int band) {
            return eq.getBandLevel((short) band);
        }

        @Override
        public int preset() {
            return eq.getCurrentPreset();
        }

        @Override
        public String presetName(int index) {
            return eq.getPresetName((short) index);
        }

        @Override
        public boolean hasControl() {
            return eq.hasControl();
        }

        @Override
        public void setEnabled(boolean on) {
            eq.setEnabled(on);
        }

        @Override
        public void setLevel(int band, int millibels) {
            eq.setBandLevel((short) band, (short) millibels);
        }

        @Override
        public void release() {
            eq.release();
        }
    };

    private final Backend backend;

    private DeviceEq(Backend backend) {
        this.backend = backend;
    }

    /** Opens the device equalizer, or returns null if there is none or the device refuses. */
    static DeviceEq open(Opener opener) {
        try {
            Backend b = opener.open();
            if (b == null || b.bands() <= 0) {
                if (b != null) b.release();
                return null;
            }
            return new DeviceEq(b);
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    int bandCount() {
        return backend.bands();
    }

    boolean hasControl() {
        try {
            return backend.hasControl();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Reads the live state, or null if the engine does not answer. */
    State read() {
        try {
            int n = backend.bands();
            int[] levels = new int[n];
            for (int i = 0; i < n; i++) levels[i] = backend.level(i);
            int preset = backend.preset();
            String name = preset >= 0 ? backend.presetName(preset) : "";
            return new State(backend.enabled(), levels, preset, name == null ? "" : name, backend.hasControl());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Applies an on/off state and band levels. Returns false if the device did not accept them. */
    boolean write(boolean enabled, int[] levels) {
        try {
            if (!backend.hasControl()) return false;
            int n = Math.min(levels.length, backend.bands());
            for (int i = 0; i < n; i++) backend.setLevel(i, levels[i]);
            backend.setEnabled(enabled);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    boolean setLevel(int band, int millibels) {
        try {
            if (!backend.hasControl()) return false;
            backend.setLevel(band, millibels);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    void release() {
        try {
            backend.release();
        } catch (RuntimeException ignored) {
            // Already gone.
        }
    }
}
