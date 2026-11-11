package com.musicplayer.lite;

import java.text.Normalizer;
import java.util.Locale;

/** Small text-formatting helpers. */
final class Fmt {
    private Fmt() {}

    /** 3:41 or 1:02:09. */
    static String time(long ms) {
        long s = Math.max(0L, ms) / 1000L;
        long h = s / 3600L;
        long m = (s % 3600L) / 60L;
        long sec = s % 60L;
        StringBuilder sb = new StringBuilder(8);
        if (h > 0) {
            sb.append(h).append(':');
            if (m < 10) sb.append('0');
        }
        sb.append(m).append(':');
        if (sec < 10) sb.append('0');
        return sb.append(sec).toString();
    }

    /** "+3 dB", "-1.5 dB", "0 dB". Input in millibels. */
    static String db(int millibels) {
        float d = millibels / 100f;
        int whole = Math.round(d);
        if (Math.abs(d - whole) < 0.05f) return (whole > 0 ? "+" : "") + whole + " dB";
        return String.format(Locale.US, "%+.1f dB", d);
    }

    /** "60 Hz", "3.6 kHz", "14 kHz". Input in milliHertz, as the platform reports it. */
    static String freq(int milliHz) {
        float hz = milliHz / 1000f;
        if (hz < 1000f) return Math.round(hz) + " Hz";
        float k = hz / 1000f;
        if (k >= 10f || Math.abs(k - Math.round(k)) < 0.05f) return Math.round(k) + " kHz";
        return String.format(Locale.US, "%.1f kHz", k);
    }

    /** Alphabet-bar section for a title: first letter upper-cased, '#' for digits and symbols. */
    static String section(String title) {
        if (title == null || title.isEmpty()) return "#";
        // "Éclair" belongs under E: use the first letter without its accent.
        String first = title.substring(0, Character.charCount(title.codePointAt(0)));
        int cp = Normalizer.normalize(first, Normalizer.Form.NFD).codePointAt(0);
        if (!Character.isLetter(cp)) return "#";
        return new String(Character.toChars(Character.toUpperCase(cp)));
    }

    /** Case-insensitive contains. {@code needle} may be in any case. */
    static boolean containsIgnoreCase(String hay, String needle) {
        if (hay == null) return false;
        final int n = needle.length();
        final int max = hay.length() - n;
        for (int i = 0; i <= max; i++) {
            if (hay.regionMatches(true, i, needle, 0, n)) return true;
        }
        return false;
    }

    /** "850 KB", "2.1 MB". */
    static String size(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024L) return Math.round(bytes / 1024f) + " KB";
        return String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f));
    }

    static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
