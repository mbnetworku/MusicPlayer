package com.musicplayer.lite;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** The strip at the bottom of the library: current song, play/pause, next. Tap to open the player. */
public class MiniPlayerView extends LinearLayout {
    private static final int ART_PX = 96;

    private final ProgressBar progress;
    private final ImageView art;
    private final TextView title;
    private final TextView artist;
    private final ImageButton play;
    private final EqBarsView bars;

    private PlaybackService service;
    private String shownArt = "";

    public MiniPlayerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.view_mini_player, this, true);
        progress = findViewById(R.id.mini_progress);
        art = findViewById(R.id.mini_art);
        title = findViewById(R.id.mini_title);
        artist = findViewById(R.id.mini_artist);
        play = findViewById(R.id.mini_play);
        bars = findViewById(R.id.mini_bars);

        findViewById(R.id.mini_body).setOnClickListener(
                v -> context.startActivity(new Intent(context, PlayerActivity.class)));
        play.setOnClickListener(v -> {
            if (service != null) service.togglePlayPause();
        });
        findViewById(R.id.mini_next).setOnClickListener(v -> {
            if (service != null) service.next(true);
        });
        setVisibility(GONE);
    }

    void bind(PlaybackService s) {
        service = s;
        refresh();
    }

    void refresh() {
        if (service == null) {
            setVisibility(GONE);
            return;
        }
        render(service.currentTrack(), service.isPlaying(), service.position(), service.duration());
    }

    void tick() {
        if (service == null || getVisibility() != VISIBLE) return;
        setProgress(service.position(), service.duration());
    }

    /** Pure view update from plain values, so it can also draw a sample state. */
    void render(Track t, boolean playing, int posMs, int durMs) {
        if (t == null) {
            setVisibility(GONE);
            return;
        }
        setVisibility(View.VISIBLE);
        title.setText(t.title);
        artist.setText(t.artist);
        play.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        play.setContentDescription(getContext().getString(playing ? R.string.cd_pause : R.string.cd_play));
        // The bars are there only while the music plays: a paused mini player stays quiet.
        bars.setVisibility(playing ? View.VISIBLE : View.GONE);
        bars.setPlaying(playing);
        setProgress(posMs, durMs);
        String key = t.artKey();
        if (!key.equals(shownArt)) {
            shownArt = key;
            ArtLoader.get(getContext()).load(t, art, ART_PX);
        }
    }

    private void setProgress(int posMs, int durMs) {
        progress.setProgress(durMs > 0 ? (int) (1000L * Math.min(posMs, durMs) / durMs) : 0);
    }
}
