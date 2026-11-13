package com.musicplayer.lite;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;

/** Settings, equalizer state and the saved play queue. */
final class Prefs {
    private static Prefs instance;

    static synchronized Prefs get(Context c) {
        if (instance == null) {
            // Activities ask during attachBaseContext, before getApplicationContext() is reliable.
            Context app = c.getApplicationContext();
            instance = new Prefs(app != null ? app : c);
        }
        return instance;
    }

    private final SharedPreferences sp;

    private Prefs(Context c) {
        sp = c.getSharedPreferences("player", Context.MODE_PRIVATE);
    }

    // ---- library view -------------------------------------------------------

    boolean showAllAudio() {
        return sp.getBoolean("show_all", false);
    }

    void setShowAllAudio(boolean v) {
        sp.edit().putBoolean("show_all", v).apply();
    }

    int lastTab() {
        return sp.getInt("tab", 0);
    }

    void setLastTab(int tab) {
        sp.edit().putInt("tab", tab).apply();
    }

    // ---- favourites ------------------------------------------------------------

    /** The favourite songs' paths, as written by {@link Favorites#encode}. */
    String favorites() {
        return sp.getString("favorites", "");
    }

    void setFavorites(String text) {
        sp.edit().putString("favorites", text).apply();
    }

    /** A short vibration on taps. */
    boolean vibrate() {
        return sp.getBoolean("vibrate", true);
    }

    void setVibrate(boolean on) {
        sp.edit().putBoolean("vibrate", on).apply();
    }

    /** A soft pulse while music plays. */
    boolean vibratePlaying() {
        return sp.getBoolean("vibrate_playing", false);
    }

    void setVibratePlaying(boolean on) {
        sp.edit().putBoolean("vibrate_playing", on).apply();
    }

    /** Swiping the big cover to the left or right goes to the next or previous song. */
    boolean coverSwipe() {
        return sp.getBoolean("cover_swipe", true);
    }

    void setCoverSwipe(boolean on) {
        sp.edit().putBoolean("cover_swipe", on).apply();
    }

    /** How song lists are sorted: see {@link SortOrder}. */
    int sortKey() {
        int v = sp.getInt("sort_key", 0);
        return v < 0 || v >= SortOrder.COUNT ? 0 : v;
    }

    boolean sortReverse() {
        return sp.getBoolean("sort_reverse", false);
    }

    void setSort(int key, boolean reverse) {
        sp.edit().putInt("sort_key", key).putBoolean("sort_reverse", reverse).apply();
    }

    // ---- moving bars --------------------------------------------------------------

    /** How the moving bars look: 0 bars, 1 dots, 2 mirror, 3 wave. */
    int barStyle() {
        int v = sp.getInt("bar_style", 0);
        return v < 0 || v > 3 ? 0 : v;
    }

    void setBarStyle(int style) {
        sp.edit().putInt("bar_style", style).apply();
    }

    // ---- appearance ----------------------------------------------------------

    /** One of {@link Appearance#FOLLOW_DEVICE}, {@link Appearance#LIGHT}, {@link Appearance#DARK}. */
    int themeMode() {
        return sp.getInt("theme_mode", Appearance.FOLLOW_DEVICE);
    }

    void setThemeMode(int mode) {
        sp.edit().putInt("theme_mode", mode).apply();
    }

    boolean glass() {
        return sp.getBoolean("glass", true);
    }

    /** What the glass look shows behind its panels: one of the Appearance.BG_* values. */
    int glassBackground() {
        return sp.getInt("glass_bg", Appearance.BG_PLAIN);
    }

    void setGlassBackground(int background) {
        sp.edit().putInt("glass_bg", background).apply();
    }

    /** True when the saved wallpaper background is only a gradient of the wallpaper's colours. */
    boolean wallpaperIsColors() {
        return sp.getBoolean("wallpaper_colors", false);
    }

    void setWallpaperIsColors(boolean colors) {
        sp.edit().putBoolean("wallpaper_colors", colors).apply();
    }

    /** Whether the equalizer screen controls the whole device's equalizer instead of this player's. */
    boolean eqDevice() {
        return sp.getBoolean("eq_device", false);
    }

    void setEqDevice(boolean on) {
        sp.edit().putBoolean("eq_device", on).apply();
    }

    /** The look of one home-screen widget: Appearance.FOLLOW_DEVICE, LIGHT or DARK. */
    int widgetStyle(int widgetId) {
        return sp.getInt("widget_style_" + widgetId, Appearance.FOLLOW_DEVICE);
    }

    void setWidgetStyle(int widgetId, int style) {
        sp.edit().putInt("widget_style_" + widgetId, style).apply();
    }

    /** The last song title and artist the widgets showed, so they are not blank after a restart. */
    String nowPlayingTitle() {
        return sp.getString("np_title", "");
    }

    String nowPlayingArtist() {
        return sp.getString("np_artist", "");
    }

    void setNowPlayingText(String title, String artist) {
        sp.edit().putString("np_title", title).putString("np_artist", artist).apply();
    }

    void removeWidget(int widgetId) {
        sp.edit().remove("widget_style_" + widgetId).apply();
    }

    void setGlass(boolean on) {
        sp.edit().putBoolean("glass", on).apply();
    }

    // ---- updates --------------------------------------------------------------

    /** Look for a newer version once a day when the app opens. */
    boolean autoUpdateCheck() {
        return sp.getBoolean("upd_auto", true);
    }

    void setAutoUpdateCheck(boolean on) {
        sp.edit().putBoolean("upd_auto", on).apply();
    }

    /** When a check last succeeded, in milliseconds since 1970 (0 = never). */
    long lastUpdateCheck() {
        return sp.getLong("upd_checked", 0);
    }

    /** When the last automatic check was tried, so a failing one is not repeated at every launch. */
    long lastUpdateAttempt() {
        return sp.getLong("upd_attempt", 0);
    }

    void saveUpdateAttempt(long time) {
        sp.edit().putLong("upd_attempt", time).apply();
    }

    /** Remembers the newest release (the raw GitHub answer) so "update available" survives a restart. */
    void saveUpdateCheck(long time, String releaseJson) {
        sp.edit().putLong("upd_checked", time).putLong("upd_attempt", time)
                .putString("upd_json", releaseJson).apply();
    }

    String cachedReleaseJson() {
        return sp.getString("upd_json", null);
    }

    /** The version whose banner the user closed. */
    String dismissedUpdate() {
        return sp.getString("upd_dismissed", "");
    }

    /** The version the automatic update pop-up was last shown for, so it appears once per version. */
    String promptedUpdate() {
        return sp.getString("upd_prompted", "");
    }

    void setPromptedUpdate(String version) {
        sp.edit().putString("upd_prompted", version).apply();
    }

    void dismissUpdate(String version) {
        sp.edit().putString("upd_dismissed", version).apply();
    }

    // ---- saved queue --------------------------------------------------------

    void saveQueue(List<Track> order, long currentId, boolean shuffle, int repeat) {
        StringBuilder sb = new StringBuilder(order.size() * 8);
        for (int i = 0; i < order.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(order.get(i).id);
        }
        sp.edit()
                .putString("q_ids", sb.toString())
                .putLong("q_cur", currentId)
                .putBoolean("q_shuffle", shuffle)
                .putInt("q_repeat", repeat)
                .apply();
    }

    void saveProgress(long currentId, int positionMs) {
        sp.edit().putLong("q_cur", currentId).putInt("q_pos", positionMs).apply();
    }

    long[] queueIds() {
        String s = sp.getString("q_ids", "");
        if (s == null || s.isEmpty()) return new long[0];
        int n = 1;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == ',') n++;
        long[] out = new long[n];
        int k = 0;
        long cur = 0;
        boolean any = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == ',') {
                if (any) out[k++] = cur;
                cur = 0;
                any = false;
            } else if (ch >= '0' && ch <= '9') {
                cur = cur * 10 + (ch - '0');
                any = true;
            }
        }
        if (any) out[k++] = cur;
        if (k == n) return out;
        long[] trimmed = new long[k];
        System.arraycopy(out, 0, trimmed, 0, k);
        return trimmed;
    }

    long queueCurrent() {
        return sp.getLong("q_cur", -1);
    }

    int queuePosition() {
        return sp.getInt("q_pos", 0);
    }

    boolean queueShuffle() {
        return sp.getBoolean("q_shuffle", false);
    }

    int queueRepeat() {
        return sp.getInt("q_repeat", PlayQueue.REPEAT_OFF);
    }

    // ---- equalizer ----------------------------------------------------------

    boolean eqEnabled() {
        return sp.getBoolean("eq_on", false);
    }

    void setEqEnabled(boolean on) {
        sp.edit().putBoolean("eq_on", on).apply();
    }

    /** Index into {@link EqPresets}, or -1 for custom. */
    int eqPreset() {
        return sp.getInt("eq_preset", 0);
    }

    int[] eqLevels() {
        String s = sp.getString("eq_levels", null);
        if (s == null || s.isEmpty()) return null;
        String[] parts = s.split(",");
        int[] out = new int[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) out[i] = Integer.parseInt(parts[i].trim());
        } catch (NumberFormatException e) {
            return null;
        }
        return out;
    }

    void setEq(int preset, int[] levels) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < levels.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(levels[i]);
        }
        sp.edit().putInt("eq_preset", preset).putString("eq_levels", sb.toString()).apply();
    }

    int bass() {
        return sp.getInt("fx_bass", 0);
    }

    int virtualizer() {
        return sp.getInt("fx_virt", 0);
    }

    int loudness() {
        return sp.getInt("fx_loud", 0);
    }

    void setEffects(int bass, int virtualizer, int loudness) {
        sp.edit().putInt("fx_bass", bass).putInt("fx_virt", virtualizer).putInt("fx_loud", loudness).apply();
    }
}
