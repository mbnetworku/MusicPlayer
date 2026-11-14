package com.musicplayer.lite;

import android.content.Context;
import android.util.TypedValue;

/**
 * The two designs of the app. Classic is the look the app has always had. New is the iOS-style
 * Liquid Glass look: a small glass tab bar and mini player, rounded shapes everywhere, a blue
 * accent and a Now Playing screen with the volume under the buttons.
 *
 * <p>The app design and the playing screen are chosen separately, so the playing screen can stay
 * Classic while the rest of the app is New (and the other way round). Classic is the default.
 *
 * <p>Colours and shapes that differ are read through theme attributes ({@code appAccent},
 * {@code appArt} ...). The New design is a theme overlay ({@code R.style.NewDesign}) laid over the
 * normal theme, so a layout only says {@code ?attr/appAccent} and gets the right colour.
 */
final class Design {
    static final int CLASSIC = 0;
    static final int NEW = 1;

    private Design() {}

    /** True when the app (lists, bars, settings) uses the New design. */
    static boolean isNew(Context c) {
        return Prefs.get(c).design() == NEW;
    }

    /** True when the Now Playing screen (full screen on a phone, the left panel in a car) is New. */
    static boolean playerNew(Context c) {
        return Prefs.get(c).playerDesign() == NEW;
    }

    /** Lays the New design's colours and shapes over a context's theme. Does nothing for Classic. */
    static void apply(Context c) {
        if (isNew(c)) c.getTheme().applyStyle(R.style.NewDesign, true);
    }

    /** The accent colour of the current design (red in Classic, blue in New). */
    static int accent(Context c) {
        TypedValue v = new TypedValue();
        if (c.getTheme().resolveAttribute(R.attr.appAccent, v, true)) {
            return v.resourceId != 0 ? c.getColor(v.resourceId) : v.data;
        }
        return c.getColor(R.color.accent_text);
    }

    /** A drawable that differs between the designs, such as the rounded corners of a cover. */
    static int drawableRes(Context c, int attr, int fallback) {
        TypedValue v = new TypedValue();
        if (c.getTheme().resolveAttribute(attr, v, true) && v.resourceId != 0) return v.resourceId;
        return fallback;
    }

    /** Changes whenever either choice changes, so an open screen notices and rebuilds. */
    static int signature(Context c) {
        Prefs p = Prefs.get(c);
        return p.design() * 2 + p.playerDesign();
    }
}
