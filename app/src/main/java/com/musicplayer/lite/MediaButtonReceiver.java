package com.musicplayer.lite;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.KeyEvent;

/**
 * Receives headset, Bluetooth and steering-wheel media keys while the player is not
 * running, and starts it. While the player runs, its MediaSession gets the keys directly.
 */
public final class MediaButtonReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) return;
        KeyEvent key = keyEventOf(intent);
        if (key == null || key.getAction() != KeyEvent.ACTION_DOWN) return;
        Intent start = new Intent(context, PlaybackService.class)
                .setAction(PlaybackService.ACTION_MEDIA_BUTTON)
                .putExtra(Intent.EXTRA_KEY_EVENT, key);
        try {
            context.startForegroundService(start);
        } catch (RuntimeException ignored) {
            // The system refused a background start; nothing sensible to do.
        }
    }

    @SuppressWarnings("deprecation")
    static KeyEvent keyEventOf(Intent intent) {
        if (Build.VERSION.SDK_INT >= 33) {
            return intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent.class);
        }
        return intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
    }
}
