package com.musicplayer.lite;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.DisplayCutout;
import android.view.View;

/** An activity that is bound to the playback service while it is on screen. */
abstract class BoundActivity extends Activity implements PlaybackService.Listener {
    private static final int TICK_MS = 500;
    private static final int BACKDROP_ART_PX = 96;

    /** Non-null between {@link #onServiceReady()} and onStop. */
    protected PlaybackService service;

    private boolean bound;
    /** The look (theme mode and glass) this screen was built with. */
    private int look;
    /** The glass look's window background; null in the flat looks. */
    private BackdropDrawable backdrop;
    private String backdropKey = "";
    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (service != null) onTick();
            ticker.postDelayed(this, TICK_MS);
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            if (!(binder instanceof PlaybackService.LocalBinder)) return;
            service = ((PlaybackService.LocalBinder) binder).getService();
            service.addListener(BoundActivity.this);
            onServiceReady();
            onPlaybackChanged();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
        }
    };

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Appearance.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        look = Appearance.signature(this);
        super.onCreate(state);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The look was changed in Settings while this screen waited in the background.
        if (Appearance.signature(this) != look) recreate();
    }

    @Override
    protected void onStart() {
        super.onStart();
        bound = bindService(new Intent(this, PlaybackService.class), connection, BIND_AUTO_CREATE);
        ticker.postDelayed(tick, TICK_MS);
    }

    @Override
    protected void onStop() {
        ticker.removeCallbacks(tick);
        if (service != null) {
            service.removeListener(this);
            service = null;
        }
        if (bound) {
            unbindService(connection);
            bound = false;
        }
        super.onStop();
    }

    /** The service is connected; {@link #service} can be used. */
    protected void onServiceReady() {
    }

    /** About twice a second while the screen is visible and the service is connected. */
    protected void onTick() {
    }

    /** Track, play state, queue or modes changed. Called on the main thread. */
    protected void onPlaybackUpdated() {
    }

    @Override
    public final void onPlaybackChanged() {
        if (backdrop != null) updateBackdrop();
        onPlaybackUpdated();
    }

    private void updateBackdrop() {
        final Track t = service == null ? null : service.currentTrack();
        final String key = t == null ? "" : t.artKey();
        if (key.equals(backdropKey)) return;
        backdropKey = key;
        if (t == null) {
            backdrop.setArt(null, "", true);
            return;
        }
        ArtLoader.get(this).loadAsync(t, BACKDROP_ART_PX, bitmap -> {
            if (backdrop != null && key.equals(backdropKey)) backdrop.setArt(bitmap, key, true);
        });
    }

    /**
     * Call right after setContentView. Keeps content clear of the status and navigation bars
     * when the system draws edge to edge (Android 15 and newer; older versions report zero),
     * styles the system bars for the light or dark look, and applies the liquid-glass look
     * when it is switched on.
     */
    protected void prepareWindow() {
        final View content = findViewById(android.R.id.content);
        content.setOnApplyWindowInsetsListener((v, insets) -> {
            int left = insets.getSystemWindowInsetLeft();
            int top = insets.getSystemWindowInsetTop();
            int right = insets.getSystemWindowInsetRight();
            int bottom = insets.getSystemWindowInsetBottom();
            if (Build.VERSION.SDK_INT >= 28) {
                DisplayCutout cut = insets.getDisplayCutout();
                if (cut != null) {
                    left = Math.max(left, cut.getSafeInsetLeft());
                    top = Math.max(top, cut.getSafeInsetTop());
                    right = Math.max(right, cut.getSafeInsetRight());
                    bottom = Math.max(bottom, cut.getSafeInsetBottom());
                }
            }
            v.setPadding(left, top, right, bottom);
            return insets;
        });

        final boolean glass = Appearance.glass(this);
        Appearance.styleSystemBars(this, glass);
        if (glass) {
            backdrop = new BackdropDrawable(this);
            backdrop.setArt(null, "", false);
            getWindow().setBackgroundDrawable(backdrop);
            Glass.decorate(content);
        }
    }
}
