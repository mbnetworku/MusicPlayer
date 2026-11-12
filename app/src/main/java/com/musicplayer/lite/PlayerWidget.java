package com.musicplayer.lite;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.widget.RemoteViews;

/**
 * The home-screen widget: cover, title, artist and previous / play / next. The playback service
 * pushes every change here. Each widget has its own style (follow the device, light or dark),
 * chosen when it is added and editable by tapping the widget's settings.
 */
public final class PlayerWidget extends AppWidgetProvider {
    private static final int ART_PX = 200;

    // The last state the service reported, so a widget added later still shows it.
    private static Bitmap lastArt;
    private static boolean lastPlaying;

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) manager.updateAppWidget(id, build(context, id));
    }

    @Override
    public void onDeleted(Context context, int[] ids) {
        Prefs prefs = Prefs.get(context);
        for (int id : ids) prefs.removeWidget(id);
    }

    /** Called by the playback service when the song, the play state or the cover changes. */
    static void publish(Context context, Track track, boolean playing, Bitmap art) {
        synchronized (PlayerWidget.class) {
            lastPlaying = playing;
            lastArt = art == null ? null : Bitmap.createScaledBitmap(art, ART_PX, ART_PX, true);
        }
        if (track != null) Prefs.get(context).setNowPlayingText(track.title, track.artist);
        refreshAll(context);
    }

    /** Redraws every widget that is on a home screen. Cheap when there is none. */
    static void refreshAll(Context context) {
        Context app = context.getApplicationContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        int[] ids = manager.getAppWidgetIds(new ComponentName(app, PlayerWidget.class));
        for (int id : ids) manager.updateAppWidget(id, build(app, id));
    }

    static int layoutFor(int style) {
        switch (style) {
            case Appearance.LIGHT:
                return R.layout.widget_player_light;
            case Appearance.DARK:
                return R.layout.widget_player_dark;
            default:
                return R.layout.widget_player;
        }
    }

    static RemoteViews build(Context context, int widgetId) {
        Prefs prefs = Prefs.get(context);
        RemoteViews views = new RemoteViews(context.getPackageName(), layoutFor(prefs.widgetStyle(widgetId)));
        final Bitmap art;
        final boolean playing;
        synchronized (PlayerWidget.class) {
            art = lastArt;
            playing = lastPlaying;
        }
        String title = prefs.nowPlayingTitle();
        String artist = prefs.nowPlayingArtist();
        views.setTextViewText(R.id.w_title, title.isEmpty() ? context.getString(R.string.nothing_playing) : title);
        views.setTextViewText(R.id.w_artist, title.isEmpty() ? context.getString(R.string.widget_tap_to_open) : artist);
        if (art != null) views.setImageViewBitmap(R.id.w_art, art);
        else views.setImageViewResource(R.id.w_art, R.drawable.ic_music_note);
        views.setImageViewResource(R.id.w_play, playing ? R.drawable.ic_pause : R.drawable.ic_play);
        views.setContentDescription(R.id.w_play, context.getString(playing ? R.string.cd_pause : R.string.cd_play));

        views.setOnClickPendingIntent(R.id.w_play, service(context, PlaybackService.ACTION_PLAY_PAUSE, 11));
        views.setOnClickPendingIntent(R.id.w_next, service(context, PlaybackService.ACTION_NEXT, 12));
        views.setOnClickPendingIntent(R.id.w_prev, service(context, PlaybackService.ACTION_PREVIOUS, 13));
        Intent open = new Intent(context, LibraryActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        views.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(context, 10, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        return views;
    }

    private static PendingIntent service(Context context, String action, int code) {
        Intent i = new Intent(context, PlaybackService.class).setAction(action);
        return PendingIntent.getService(context, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
