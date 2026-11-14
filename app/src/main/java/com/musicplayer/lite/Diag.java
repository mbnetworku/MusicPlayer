package com.musicplayer.lite;

import android.content.Context;
import android.util.Log;

import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A few counters for the unusual things the background work has to deal with: cover requests that
 * were skipped, a song that took too long to start, a scan or a download that was stopped. They
 * are shown in Settings under About, and each kind is also written to the system log the first time
 * it happens and then on every tenth, hundredth ... time. Nothing is recorded about the user's music.
 */
final class Diag {
    static final int ART_SKIPPED = 0;
    static final int PREPARE_TIMEOUT = 1;
    static final int SCAN_STOPPED = 2;
    static final int BASS_TOO_LONG = 3;
    static final int DOWNLOAD_STOPPED = 4;
    static final int ART_TOO_BIG = 5;
    static final int PLAY_FAILED = 6;
    private static final int KINDS = 7;
    private static final String TAG = "MusicPlayer";
    private static final String[] NAMES = {
            "cover request skipped", "song took too long to start", "scan stopped after waiting too long",
            "song too long for the bass buzz", "download stopped", "cover picture too big", "song could not be played"};

    private static final AtomicIntegerArray COUNTS = new AtomicIntegerArray(KINDS);
    private static final AtomicLong RELOAD_MS = new AtomicLong(-1);
    private static final AtomicLong RELOAD_SONGS = new AtomicLong(0);
    private static volatile String lastPlayProblem = "";

    private Diag() {}

    /** Counts one event of this kind. Safe from any thread. */
    static void count(int kind) {
        final int n = COUNTS.incrementAndGet(kind);
        if (isPowerOfTen(n)) Log.w(TAG, NAMES[kind] + " (" + n + " so far)");
    }

    static int get(int kind) {
        return COUNTS.get(kind);
    }

    /** A song could not be played: counted, and its name and the reason kept for Settings. */
    static void playFailed(String description) {
        lastPlayProblem = description == null ? "" : description;
        count(PLAY_FAILED);
    }

    static String lastPlayProblem() {
        return lastPlayProblem;
    }

    /** How long the last reload of the song list took, and how many songs it found. */
    static void reloaded(long millis, int songs) {
        RELOAD_MS.set(millis);
        RELOAD_SONGS.set(songs);
    }

    static long lastReloadMs() {
        return RELOAD_MS.get();
    }

    /** For the tests. */
    static void reset() {
        for (int i = 0; i < KINDS; i++) COUNTS.set(i, 0);
        RELOAD_MS.set(-1);
        RELOAD_SONGS.set(0);
        lastPlayProblem = "";
    }

    /** The lines shown in Settings, one per kind of event that happened. Empty when nothing did. */
    static String summary(Context c) {
        final int[] strings = {R.string.diag_art_skipped, R.string.diag_prepare_timeout, R.string.diag_scan_stopped,
                R.string.diag_bass_too_long, R.string.diag_download_stopped, R.string.diag_art_too_big,
                R.string.diag_play_failed};
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < KINDS; i++) {
            final int n = COUNTS.get(i);
            if (n > 0) sb.append(c.getString(strings[i], n)).append('\n');
        }
        if (!lastPlayProblem.isEmpty()) sb.append(c.getString(R.string.diag_play_last, lastPlayProblem)).append('\n');
        if (RELOAD_MS.get() >= 0) {
            sb.append(c.getString(R.string.diag_reload, RELOAD_MS.get(), RELOAD_SONGS.get())).append('\n');
        }
        return sb.toString().trim();
    }

    private static boolean isPowerOfTen(int n) {
        while (n >= 10 && n % 10 == 0) n /= 10;
        return n == 1;
    }
}
