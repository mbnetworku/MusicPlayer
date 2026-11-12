package com.musicplayer.lite;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
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
    private final ImageButton more;
    private final ImageButton favorite;
    private final EqBarsView bars;
    private final Favorites favorites;
    private final ColorStateList accent;
    private final ColorStateList dim;
    private final ColorStateList plain;

    /** The "more" button next to the title. */
    interface MoreListener {
        void onMore(Track track);
    }

    private PlaybackService service;
    private MoreListener moreListener;
    private boolean dragging;
    private String shownArt = "";
    /** The song on screen: the heart belongs to it. */
    private Track shown;
    private final Favorites.Listener favoritesListener = this::syncFavorite;

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
        more = findViewById(R.id.np_more);
        favorite = findViewById(R.id.np_fav);
        bars = findViewById(R.id.np_bars);
        favorites = Favorites.get(context);
        accent = ColorStateList.valueOf(context.getColor(R.color.accent_text));
        dim = ColorStateList.valueOf(context.getColor(R.color.icon_dim));
        plain = ColorStateList.valueOf(context.getColor(R.color.icon));

        more.setOnClickListener(v -> {
            Track t = service == null ? null : service.currentTrack();
            if (t != null && moreListener != null) moreListener.onMore(t);
        });
        favorite.setOnClickListener(v -> {
            Track t = service == null ? null : service.currentTrack();
            if (t == null) return;
            // The Favorites listener redraws the heart here and in every other view that shows it.
            if (favorites.toggle(t)) pop(favorite);
        });
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

    void setMoreListener(MoreListener l) {
        moreListener = l;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        favorites.addListener(favoritesListener);
        syncFavorite();   // it may have changed while this view was away
    }

    @Override
    protected void onDetachedFromWindow() {
        favorites.removeListener(favoritesListener);
        super.onDetachedFromWindow();
    }

    /** A little bounce for the heart when a song becomes a favorite. */
    private static void pop(View v) {
        v.animate().cancel();
        v.setScaleX(1f);
        v.setScaleY(1f);
        v.animate().scaleX(1.3f).scaleY(1.3f).setDuration(120)
                .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(120)).start();
    }

    /** The favorites changed (here or elsewhere): show the heart as the song on screen now has it. */
    private void syncFavorite() {
        showFavorite(shown != null && favorites.contains(shown));
    }

    private void showFavorite(boolean on) {
        favorite.setImageResource(on ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
        favorite.setImageTintList(on ? accent : plain);
        favorite.setContentDescription(getContext().getString(on ? R.string.cd_favorite_remove : R.string.cd_favorite_add));
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
        shown = t;
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
        more.setVisibility(has ? VISIBLE : INVISIBLE);
        favorite.setVisibility(has ? VISIBLE : INVISIBLE);
        showFavorite(has && favorites.contains(t));
        // Visible first, then told to move: the bars ease in from their resting height.
        bars.setVisibility(has ? VISIBLE : GONE);
        bars.setPlaying(has && playing);
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
