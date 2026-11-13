package com.musicplayer.lite;

import android.view.HapticFeedbackConstants;
import android.view.View;

/**
 * A short vibration when a button or row is tapped, if the user has it switched on in Settings.
 * It uses the view's own touch feedback, so it needs no permission and also follows Android's
 * "Touch feedback" setting.
 */
final class Haptics {
    private Haptics() {}

    static void tap(View v) {
        if (v == null || !Prefs.get(v.getContext()).vibrate()) return;
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }
}
