package com.musicplayer.lite;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Walks every readable storage volume (internal, SD card, USB drives) for audio
 * files that MediaStore does not know yet and asks the system scanner to index them.
 */
final class StorageScanner {
    private StorageScanner() {}

    interface Callback {
        /** @param requested number of new files handed to the system scanner */
        void onDone(int requested);
    }

    private static final Set<String> EXTENSIONS = new HashSet<>(Arrays.asList(
            "mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav", "amr", "mka", "mid", "midi"));
    private static final int MAX_DEPTH = 12;
    private static final int MAX_FILES = 50000;
    /** Entries (files and folders) looked at in one scan, so a huge or looping tree cannot keep it busy for ever. */
    private static final int MAX_VISITED = 300000;
    /** How long to wait for the system scanner before the scan is reported as done anyway. */
    static final long SCAN_TIMEOUT_MS = 120_000;

    static void scan(Context context, final Set<String> known, final Callback cb) {
        final Context app = context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        final AtomicBoolean finished = new AtomicBoolean();   // the caller is told once, by whoever comes first
        new Thread(() -> {
            final List<String> found = new ArrayList<>();
            final int[] visited = {0};
            for (File root : roots(app)) walk(root, 0, known, found, visited);
            if (found.isEmpty()) {
                finish(finished, main, cb, 0);
                return;
            }
            final AtomicInteger left = new AtomicInteger(found.size());
            MediaScannerConnection.scanFile(app, found.toArray(new String[0]), null, (path, uri) -> {
                if (left.decrementAndGet() == 0) finish(finished, main, cb, found.size());
            });
            // If the system scanner never answers for some file, the scan must not stay "busy" for ever.
            main.postDelayed(() -> {
                if (!finished.get()) Diag.count(Diag.SCAN_STOPPED);
                finish(finished, main, cb, found.size());
            }, SCAN_TIMEOUT_MS);
        }, "storage-scan").start();
    }

    private static void finish(AtomicBoolean finished, Handler main, Callback cb, int requested) {
        if (finished.compareAndSet(false, true)) main.post(() -> cb.onDone(requested));
    }

    private static Set<File> roots(Context app) {
        Set<File> roots = new LinkedHashSet<>();
        addIfReadable(roots, Environment.getExternalStorageDirectory());
        File[] appDirs = app.getExternalFilesDirs(null);
        if (appDirs != null) {
            for (File f : appDirs) {
                if (f == null) continue;
                String p = f.getAbsolutePath();
                int i = p.indexOf("/Android/data");
                if (i > 0) addIfReadable(roots, new File(p.substring(0, i)));
            }
        }
        File[] volumes = new File("/storage").listFiles();
        if (volumes != null) {
            for (File v : volumes) {
                String n = v.getName();
                if (!n.equals("emulated") && !n.equals("self")) addIfReadable(roots, v);
            }
        }
        // Head units that mount USB drives under /mnt.
        for (String base : new String[]{"/mnt/usb_storage", "/mnt/media_rw"}) {
            File[] kids = new File(base).listFiles();
            if (kids != null) for (File k : kids) addIfReadable(roots, k);
        }
        return roots;
    }

    private static void addIfReadable(Set<File> roots, File f) {
        if (f != null && f.isDirectory() && f.canRead()) roots.add(f);
    }

    private static void walk(File dir, int depth, Set<String> known, List<String> out, int[] visited) {
        if (depth > MAX_DEPTH || out.size() >= MAX_FILES || visited[0] >= MAX_VISITED) return;
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File f : kids) {
            if (f.getName().equals(".nomedia")) return; // the owner asked for this folder to be skipped
        }
        for (File f : kids) {
            if (++visited[0] > MAX_VISITED) return;
            String name = f.getName();
            if (name.startsWith(".")) continue;
            if (f.isDirectory()) {
                if (depth == 0 && name.equals("Android")) continue;
                walk(f, depth + 1, known, out, visited);
            } else {
                int dot = name.lastIndexOf('.');
                if (dot > 0 && EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT))) {
                    String p = f.getAbsolutePath();
                    if (!known.contains(p)) out.add(p);
                }
            }
        }
    }
}
