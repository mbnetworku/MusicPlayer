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

    static void scan(Context context, final Set<String> known, final Callback cb) {
        final Context app = context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            final List<String> found = new ArrayList<>();
            for (File root : roots(app)) walk(root, 0, known, found);
            if (found.isEmpty()) {
                main.post(() -> cb.onDone(0));
                return;
            }
            final AtomicInteger left = new AtomicInteger(found.size());
            MediaScannerConnection.scanFile(app, found.toArray(new String[0]), null, (path, uri) -> {
                if (left.decrementAndGet() == 0) main.post(() -> cb.onDone(found.size()));
            });
        }, "storage-scan").start();
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

    private static void walk(File dir, int depth, Set<String> known, List<String> out) {
        if (depth > MAX_DEPTH || out.size() >= MAX_FILES) return;
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File f : kids) {
            if (f.getName().equals(".nomedia")) return; // the owner asked for this folder to be skipped
        }
        for (File f : kids) {
            String name = f.getName();
            if (name.startsWith(".")) continue;
            if (f.isDirectory()) {
                if (depth == 0 && name.equals("Android")) continue;
                walk(f, depth + 1, known, out);
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
