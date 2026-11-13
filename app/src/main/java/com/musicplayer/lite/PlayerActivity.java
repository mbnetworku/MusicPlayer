package com.musicplayer.lite;

import android.content.Intent;
import android.os.Bundle;

/** Full-screen Now Playing for phones and portrait screens. */
public class PlayerActivity extends BoundActivity {
    private NowPlayingView player;
    private EqBarsView eqBars;

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
        player.setMoreListener(t -> showSongMenu(t.title, java.util.Collections.singletonList(t), true, true));
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_queue).setOnClickListener(v -> startActivity(new Intent(this, QueueActivity.class)));
        eqBars = findViewById(R.id.btn_eq);
        eqBars.setOnClickListener(v -> startActivity(new Intent(this, EqualizerActivity.class)));
    }

    @Override
    protected void onServiceReady() {
        if (player != null) player.bind(service);
    }

    @Override
    protected void onPlaybackUpdated() {
        if (player != null) player.refresh();
        if (eqBars != null && service != null) eqBars.setPlaying(service.isPlaying());
    }

    @Override
    protected void onTick() {
        if (player != null) player.tick();
    }
}
