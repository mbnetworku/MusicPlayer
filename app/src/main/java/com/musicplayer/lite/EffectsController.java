package com.musicplayer.lite;

import android.content.Context;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.Virtualizer;

/**
 * The equalizer and sound effects, attached to the player's audio session so they
 * only affect this app's audio and survive track changes. Every effect is optional:
 * devices that lack one simply report it as unavailable.
 */
final class EffectsController {
    static final int STRENGTH_MAX = 1000;
    /** Volume boost range in millibels (0 to 12 dB). */
    static final int LOUDNESS_MAX = 1200;

    private final Prefs prefs;
    private Equalizer eq;
    private BassBoost bassBoost;
    private Virtualizer virtualizer;
    private LoudnessEnhancer loudness;

    private int bands;
    private int minLevel;
    private int maxLevel;
    private int[] centers = new int[0];
    private int[] levels = new int[0];
    private int preset;
    private boolean enabled;
    private int bass;
    private int virt;
    private int loud;

    EffectsController(Context ctx, int audioSession) {
        prefs = Prefs.get(ctx);
        enabled = prefs.eqEnabled();
        preset = prefs.eqPreset();
        bass = prefs.bass();
        virt = prefs.virtualizer();
        loud = prefs.loudness();
        if (audioSession > 0) create(audioSession);
        initLevels();
        applyAll();
    }

    private void create(int session) {
        Equalizer e = null;
        try {
            e = new Equalizer(0, session);
            int n = e.getNumberOfBands();
            short[] range = e.getBandLevelRange();
            int[] c = new int[n];
            for (int i = 0; i < n; i++) c[i] = e.getCenterFreq((short) i);
            bands = n;
            minLevel = range[0];
            maxLevel = range[1];
            centers = c;
            eq = e;
        } catch (RuntimeException ex) {
            if (e != null) e.release();
            eq = null;
            bands = 0;
        }
        try {
            BassBoost b = new BassBoost(0, session);
            if (b.getStrengthSupported()) bassBoost = b;
            else b.release();
        } catch (RuntimeException ignored) {
            bassBoost = null;
        }
        try {
            Virtualizer v = new Virtualizer(0, session);
            if (v.getStrengthSupported()) virtualizer = v;
            else v.release();
        } catch (RuntimeException ignored) {
            virtualizer = null;
        }
        try {
            loudness = new LoudnessEnhancer(session);
        } catch (RuntimeException ignored) {
            loudness = null;
        }
    }

    private void initLevels() {
        levels = new int[bands];
        if (bands == 0) return;
        int[] saved = prefs.eqLevels();
        if (saved != null && saved.length == bands) {
            for (int i = 0; i < bands; i++) levels[i] = Fmt.clamp(saved[i], minLevel, maxLevel);
        } else {
            if (preset < 0) preset = 0;
            levels = EqPresets.levelsFor(preset, centers, minLevel, maxLevel);
        }
    }

    // ---- state for the UI ------------------------------------------------------

    boolean hasEq() {
        return eq != null && bands > 0;
    }

    int bandCount() {
        return bands;
    }

    int minLevel() {
        return minLevel;
    }

    int maxLevel() {
        return maxLevel;
    }

    int centerFreq(int band) {
        return centers[band];
    }

    int level(int band) {
        return levels[band];
    }

    /** Index into {@link EqPresets}, or -1 when the curve was edited by hand. */
    int preset() {
        return preset;
    }

    boolean isEnabled() {
        return enabled;
    }

    boolean hasBass() {
        return bassBoost != null;
    }

    boolean hasVirtualizer() {
        return virtualizer != null;
    }

    boolean hasLoudness() {
        return loudness != null;
    }

    int bass() {
        return bass;
    }

    int virtualizer() {
        return virt;
    }

    int loudness() {
        return loud;
    }

    // ---- changes -----------------------------------------------------------------

    void setEnabled(boolean on) {
        enabled = on;
        prefs.setEqEnabled(on);
        applyAll();
    }

    /** Live change while a slider is dragged. Call {@link #commit()} when it is released. */
    void setBand(int band, int level) {
        if (band < 0 || band >= bands) return;
        levels[band] = Fmt.clamp(level, minLevel, maxLevel);
        preset = -1;
        if (eq != null) {
            try {
                eq.setBandLevel((short) band, (short) levels[band]);
            } catch (RuntimeException ignored) {
                // The audio server may have restarted; the next applyAll() recovers.
            }
        }
    }

    void usePreset(int index) {
        if (bands == 0) return;
        preset = index;
        levels = EqPresets.levelsFor(index, centers, minLevel, maxLevel);
        applyEq();
        commit();
    }

    void setBass(int strength) {
        bass = Fmt.clamp(strength, 0, STRENGTH_MAX);
        applyBass();
    }

    void setVirtualizer(int strength) {
        virt = Fmt.clamp(strength, 0, STRENGTH_MAX);
        applyVirtualizer();
    }

    void setLoudness(int millibels) {
        loud = Fmt.clamp(millibels, 0, LOUDNESS_MAX);
        applyLoudness();
    }

    void reset() {
        bass = 0;
        virt = 0;
        loud = 0;
        if (bands > 0) {
            preset = 0;
            levels = EqPresets.levelsFor(0, centers, minLevel, maxLevel);
        }
        applyAll();
        commit();
    }

    void commit() {
        if (bands > 0) prefs.setEq(preset, levels);
        prefs.setEffects(bass, virt, loud);
    }

    void release() {
        commit();
        if (eq != null) eq.release();
        if (bassBoost != null) bassBoost.release();
        if (virtualizer != null) virtualizer.release();
        if (loudness != null) loudness.release();
        eq = null;
        bassBoost = null;
        virtualizer = null;
        loudness = null;
    }

    // ---- applying to the audio session -----------------------------------------

    private void applyAll() {
        applyEq();
        applyBass();
        applyVirtualizer();
        applyLoudness();
    }

    private void applyEq() {
        if (eq == null) return;
        try {
            for (int i = 0; i < bands; i++) eq.setBandLevel((short) i, (short) levels[i]);
            eq.setEnabled(enabled);
        } catch (RuntimeException ignored) {
            // Effect engine unavailable right now.
        }
    }

    private void applyBass() {
        if (bassBoost == null) return;
        try {
            bassBoost.setStrength((short) bass);
            bassBoost.setEnabled(enabled && bass > 0);
        } catch (RuntimeException ignored) {
            // Ignore: not supported on this device.
        }
    }

    private void applyVirtualizer() {
        if (virtualizer == null) return;
        try {
            virtualizer.setStrength((short) virt);
            virtualizer.setEnabled(enabled && virt > 0);
        } catch (RuntimeException ignored) {
            // Ignore: not supported on this device.
        }
    }

    private void applyLoudness() {
        if (loudness == null) return;
        try {
            loudness.setTargetGain(loud);
            loudness.setEnabled(enabled && loud > 0);
        } catch (RuntimeException ignored) {
            // Ignore: not supported on this device.
        }
    }
}
