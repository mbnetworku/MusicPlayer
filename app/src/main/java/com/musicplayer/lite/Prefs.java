package com.musicplayer.lite;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;

/** Settings, equalizer state and the saved play queue. */
final class Prefs {
    private static Prefs instance;

    static synchronized Prefs get(Context c) {
        if (instance == null) instance = new Prefs(c.getApplicationContext());
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
