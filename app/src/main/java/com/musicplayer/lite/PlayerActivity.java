package com.musicplayer.lite;

import android.content.Intent;
import android.os.Bundle;

/** Full-screen Now Playing for phones and portrait screens. */
public class PlayerActivity extends BoundActivity {
    private NowPlayingView player;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (getResources().getBoolean(R.bool.split_layout)) {
            // Car and tablet landscape: the library screen already shows the player.
            finish();
            return;
        }
        setContentView(R.layout.activity_player);
        prepareWindow();
        player = findViewById(R.id.np);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_queue).setOnClickListener(v -> startActivity(new Intent(this, QueueActivity.class)));
        findViewById(R.id.btn_eq).setOnClickListener(v -> startActivity(new Intent(this, EqualizerActivity.class)));
    }

    @Override
    protected void onServiceReady() {
        if (player != null) player.bind(service);
    }

    @Override
    protected void onPlaybackUpdated() {
        if (player != null) player.refresh();
    }

    @Override
    protected void onTick() {
        if (player != null) player.tick();
    }
}
