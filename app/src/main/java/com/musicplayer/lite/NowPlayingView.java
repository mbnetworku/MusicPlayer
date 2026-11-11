package com.musicplayer.lite;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/** Album art, title, seek bar and transport controls. Used full screen and as the car panel. */
public class NowPlayingView extends LinearLayout {
    private static final int ART_PX = 640;

    private final ImageView art;
    private final TextView title;
    private final TextView artist;
    private final TextView elapsed;
    private final TextView duration;
    private final SeekBar seek;
    private final ImageButton shuffle;
    private final ImageButton prev;
    private final ImageButton play;
    private final ImageButton next;
    private final ImageButton repeat;
    private final ColorStateList accent;
    private final ColorStateList dim;

    private PlaybackService service;
    private boolean dragging;
    private String shownArt = "";

    public NowPlayingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.view_now_playing, this, true);
        art = findViewById(R.id.np_art);
        title = findViewById(R.id.np_title);
        artist = findViewById(R.id.np_artist);
        elapsed = findViewById(R.id.np_elapsed);
        duration = findViewById(R.id.np_duration);
        seek = findViewById(R.id.np_seek);
        shuffle = findViewById(R.id.np_shuffle);
        prev = findViewById(R.id.np_prev);
        play = findViewById(R.id.np_play);
        next = findViewById(R.id.np_next);
        repeat = findViewById(R.id.np_repeat);
        accent = ColorStateList.valueOf(context.getColor(R.color.accent_text));
        dim = ColorStateList.valueOf(context.getColor(R.color.icon_dim));

        play.setOnClickListener(v -> {
            if (service != null) service.togglePlayPause();
        });
        next.setOnClickListener(v -> {
            if (service != null) service.next(true);
        });
        prev.setOnClickListener(v -> {
            if (service != null) service.previous();
        });
        shuffle.setOnClickListener(v -> {
            if (service != null) service.setShuffle(!service.queue().isShuffle());
        });
        repeat.setOnClickListener(v -> {
            if (service != null) service.cycleRepeat();
        });
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) elapsed.setText(Fmt.time(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                dragging = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                dragging = false;
                if (service != null) service.seekTo(bar.getProgress());
            }
        });
        render(null, false, 0, 0, false, PlayQueue.REPEAT_OFF);
    }

    void bind(PlaybackService s) {
        service = s;
        refresh();
    }

    /** Re-reads everything from the service. */
    void refresh() {
        if (service == null) {
            render(null, false, 0, 0, false, PlayQueue.REPEAT_OFF);
            return;
        }
        render(service.currentTrack(), service.isPlaying(), service.position(), service.duration(),
                service.queue().isShuffle(), service.queue().repeat());
    }

    /** Called about twice a second while visible: moves the seek bar. */
    void tick() {
        if (service == null || dragging) return;
        int pos = service.position();
        seek.setProgress(pos);
        elapsed.setText(Fmt.time(pos));
    }

    /** Pure view update from plain values, so it can also draw a sample state. */
    void render(Track t, boolean playing, int posMs, int durMs, boolean shuffleOn, int repeatMode) {
        final boolean has = t != null;
        final Context c = getContext();
        title.setText(has ? t.title : c.getString(R.string.nothing_playing));
        artist.setText(has ? t.artist + " · " + t.album : c.getString(R.string.nothing_playing_hint));
        seek.setEnabled(has);
        seek.setMax(Math.max(1, durMs));
        if (!dragging) {
            seek.setProgress(Math.min(posMs, Math.max(1, durMs)));
            elapsed.setText(Fmt.time(posMs));
        }
        duration.setText(Fmt.time(durMs));

        play.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        play.setContentDescription(c.getString(playing ? R.string.cd_pause : R.string.cd_play));
        shuffle.setImageTintList(shuffleOn ? accent : dim);
        repeat.setImageResource(repeatMode == PlayQueue.REPEAT_ONE ? R.drawable.ic_repeat_one : R.drawable.ic_repeat);
        repeat.setImageTintList(repeatMode == PlayQueue.REPEAT_OFF ? dim : accent);
        final float alpha = has ? 1f : 0.4f;
        play.setAlpha(alpha);
        prev.setAlpha(alpha);
        next.setAlpha(alpha);

        if (has) {
            String key = t.artKey();
            if (!key.equals(shownArt)) {
                shownArt = key;
                ArtLoader.get(c).load(t, art, ART_PX);
            }
        } else if (!shownArt.isEmpty() || art.getDrawable() == null) {
            shownArt = "";
            art.setTag(R.id.tag_art, null);
            art.setImageDrawable(ArtLoader.get(c).placeholder("none"));
        }
    }
}
