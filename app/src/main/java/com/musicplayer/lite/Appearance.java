package com.musicplayer.lite;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;

/**
 * Light, dark or "follow the device", plus the optional liquid-glass style.
 *
 * Light and dark are plain resource qualifiers (values-notnight holds the light colours).
 * "Follow the device" leaves the system's night mode alone, so the app switches the moment
 * the device does. Light or Dark forces the qualifier for this app only.
 */
final class Appearance {
    static final int FOLLOW_DEVICE = 0;
    static final int LIGHT = 1;
    static final int DARK = 2;

    private Appearance() {}

    /** Returns a context whose resources use the chosen light or dark mode. */
    static Context wrap(Context base) {
        int mode = Prefs.get(base).themeMode();
        if (mode == FOLLOW_DEVICE) return base;
        Configuration config = new Configuration(base.getResources().getConfiguration());
        int night = mode == DARK ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
        return base.createConfigurationContext(config);
    }

    static boolean isNight(Context c) {
        int night = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }

    static boolean glass(Context c) {
        return Prefs.get(c).glass();
    }

    /** Changes whenever the chosen look changes, so an open screen can notice and rebuild itself. */
    static int signature(Context c) {
        Prefs p = Prefs.get(c);
        return p.themeMode() * 2 + (p.glass() ? 1 : 0);
    }

    /** Dark icons on light backgrounds and light icons on dark ones; clear bars for glass. */
    static void styleSystemBars(Activity a, boolean transparentBars) {
        Window w = a.getWindow();
        if (transparentBars) {
            w.setStatusBarColor(Color.TRANSPARENT);
            w.setNavigationBarColor(Color.TRANSPARENT);
        }
        final boolean lightBackground = !isNight(a);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = w.getInsetsController();
            if (controller != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(lightBackground ? mask : 0, mask);
            }
        } else {
            View decor = w.getDecorView();
            int bits = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            int flags = decor.getSystemUiVisibility();
            decor.setSystemUiVisibility(lightBackground ? (flags | bits) : (flags & ~bits));
        }
    }
}
