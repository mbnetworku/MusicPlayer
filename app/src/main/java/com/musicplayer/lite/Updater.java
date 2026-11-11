package com.musicplayer.lite;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Looks for a newer version on GitHub Releases, downloads it, checks it and hands it to
 * Android's installer. One instance lives for the whole process, so a download keeps going
 * while the Settings screen is closed and reopened.
 *
 * The state is only changed on the main thread. Network work happens on a background thread.
 */
final class Updater {
    enum State {
        IDLE, CHECKING, UP_TO_DATE, NONE_PUBLISHED, AVAILABLE,
        DOWNLOADING, VERIFYING, READY, NEEDS_PERMISSION, INSTALLING, ERROR
    }

    interface Listener {
        void onUpdaterChanged();
    }

    interface Verifier {
        /** 0 when the file is fine, otherwise a string resource explaining the problem. */
        int check(Context c, File apk, String installedVersion);
    }

    interface Committer {
        void commit(Context c, File apk) throws IOException;
    }

    /** Where to look and how; tests point it at a local server. */
    static final class Config {
        final String apiBase;
        final String repo;
        final boolean allowInsecure;
        final File dir;
        final String currentVersion;
        final Verifier verifier;
        final Committer committer;

        Config(String apiBase, String repo, boolean allowInsecure, File dir, String currentVersion,
               Verifier verifier, Committer committer) {
            this.apiBase = apiBase;
            this.repo = repo;
            this.allowInsecure = allowInsecure;
            this.dir = dir;
            this.currentVersion = currentVersion;
            this.verifier = verifier;
            this.committer = committer;
        }
    }

    private static final long DAY_MS = 24L * 60 * 60 * 1000;
    private static final long HOUR_MS = 60L * 60 * 1000;

    // Holds the application context only, so it cannot leak an Activity.
    @android.annotation.SuppressLint("StaticFieldLeak")
    private static Updater instance;

    static synchronized Updater get(Context c) {
        if (instance == null) {
            Context app = c.getApplicationContext();
            instance = new Updater(app, defaultConfig(app));
        }
        return instance;
    }

    /** Lets tests use a differently configured updater. */
    static synchronized void replaceForTests(Updater updater) {
        instance = updater;
    }

    static Config defaultConfig(Context app) {
        return new Config("https://api.github.com", app.getString(R.string.update_repo), false,
                new File(app.getCacheDir(), "updates"), null, ApkVerifier::check, UpdateInstaller::commit);
    }

    private final Context app;
    private final Config config;
    private final Prefs prefs;
    private final String currentVersion;
    private final String userAgent;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<Listener> listeners = new ArrayList<>();
    private final AtomicBoolean cancel = new AtomicBoolean();

    private State state = State.IDLE;
    private Release release;
    private int percent;
    private int errorRes;
    private String errorDetail = "";
    private File apk;

    Updater(Context app, Config config) {
        this.app = app;
        this.config = config;
        this.prefs = Prefs.get(app);
        this.currentVersion = config.currentVersion != null ? config.currentVersion : installedVersion(app);
        this.userAgent = "MusicPlayer/" + currentVersion;
        restoreRemembered();
    }

    private static String installedVersion(Context c) {
        try {
            PackageInfo info = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return info.versionName == null ? "0" : info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "0";
        }
    }

    /** A newer release found on an earlier run is shown again straight away, even offline. */
    private void restoreRemembered() {
        String json = prefs.cachedReleaseJson();
        if (json == null) return;
        try {
            Release r = Release.parse(json);
            if (Release.isNewer(r.version, currentVersion)) {
                release = r;
                state = State.AVAILABLE;
            }
        } catch (JSONException | Release.ParseException ignored) {
            // A damaged remembered answer is simply forgotten.
        }
    }

    // ---- what the screens read ----------------------------------------------------------------

    State state() {
        return state;
    }

    /** The newest release we know of, which may be the one already installed. */
    Release release() {
        return release;
    }

    /** The newest release, only when it is newer than what is installed; otherwise null. */
    Release newerRelease() {
        return release != null && Release.isNewer(release.version, currentVersion) ? release : null;
    }

    String currentVersion() {
        return currentVersion;
    }

    int percent() {
        return percent;
    }

    /** A string resource describing the last error (valid while the state is ERROR). */
    int errorRes() {
        return errorRes;
    }

    String errorDetail() {
        return errorDetail;
    }

    long lastCheck() {
        return prefs.lastUpdateCheck();
    }

    boolean busy() {
        return state == State.CHECKING || state == State.DOWNLOADING
                || state == State.VERIFYING || state == State.INSTALLING;
    }

    /** Everything the Settings screen shows about updates, captured at one moment. */
    static final class Status {
        final State state;
        final Release release;
        final String currentVersion;
        final int percent;
        final int errorRes;
        final String errorDetail;
        final long lastCheck;

        Status(State state, Release release, String currentVersion, int percent, int errorRes,
               String errorDetail, long lastCheck) {
            this.state = state;
            this.release = release;
            this.currentVersion = currentVersion;
            this.percent = percent;
            this.errorRes = errorRes;
            this.errorDetail = errorDetail;
            this.lastCheck = lastCheck;
        }
    }

    Status status() {
        return new Status(state, release, currentVersion, percent, errorRes, errorDetail, lastCheck());
    }

    void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    void removeListener(Listener l) {
        listeners.remove(l);
    }

    // ---- checking ---------------------------------------------------------------------------------

    /** Checks at most once a day, and only if the user has not turned automatic checks off. */
    void autoCheckIfDue() {
        if (!prefs.autoUpdateCheck() || busy()) return;
        long now = System.currentTimeMillis();
        if (now - prefs.lastUpdateCheck() < DAY_MS) return;
        if (now - prefs.lastUpdateAttempt() < HOUR_MS) return;
        check(true);
    }

    /** Asks GitHub for the newest release. An automatic check stays silent when it fails. */
    void check(final boolean automatic) {
        if (busy()) return;
        final State before = state;
        setState(State.CHECKING);
        io.execute(() -> {
            final long now = System.currentTimeMillis();
            try {
                final Release r = ReleaseApi.fetchLatest(config.apiBase, config.repo, userAgent, config.allowInsecure);
                main.post(() -> {
                    prefs.saveUpdateCheck(now, r.raw);
                    release = r;
                    setState(Release.isNewer(r.version, currentVersion) ? State.AVAILABLE : State.UP_TO_DATE);
                });
            } catch (final ReleaseApi.ApiException e) {
                main.post(() -> {
                    prefs.saveUpdateAttempt(now);
                    if (e.kind == ReleaseApi.ApiException.NOT_FOUND) {
                        release = null;
                        prefs.saveUpdateCheck(now, null);
                        setState(State.NONE_PUBLISHED);
                    } else if (automatic) {
                        setState(before == State.CHECKING ? State.IDLE : before);
                    } else {
                        fail(errorFor(e.kind), e.getMessage());
                    }
                });
            }
        });
    }

    // ---- downloading ------------------------------------------------------------------------------

    void download() {
        final Release r = release;
        if (r == null || busy()) return;
        cancel.set(false);
        percent = 0;
        setState(State.DOWNLOADING);
        io.execute(() -> {
            final File part = new File(config.dir, "download.part");
            final File dest = new File(config.dir, "MusicPlayer-" + r.version + ".apk");
            try {
                if (!config.dir.isDirectory() && !config.dir.mkdirs()) {
                    throw new ReleaseApi.ApiException(ReleaseApi.ApiException.NETWORK, "no folder for the download");
                }
                deleteOldDownloads();
                String actual = ReleaseApi.download(r.apkUrl, part, r.apkSize, this::postProgress,
                        cancel, userAgent, config.allowInsecure);
                String expected = r.sha256;
                if (expected == null && r.sha256Url != null) {
                    expected = firstWord(ReleaseApi.fetchText(r.sha256Url, "text/plain", userAgent,
                            config.allowInsecure, false));
                }
                if (expected != null && !expected.equalsIgnoreCase(actual)) {
                    throw new ReleaseApi.ApiException(ReleaseApi.ApiException.CHECKSUM, "checksum differs");
                }
                if (!part.renameTo(dest)) {
                    throw new ReleaseApi.ApiException(ReleaseApi.ApiException.NETWORK, "could not save the file");
                }
                main.post(() -> setState(State.VERIFYING));
                final int problem = config.verifier.check(app, dest, currentVersion);
                if (problem != 0) {
                    dest.delete();
                    main.post(() -> fail(problem, ""));
                    return;
                }
                main.post(() -> {
                    apk = dest;
                    setState(State.READY);
                });
            } catch (final ReleaseApi.ApiException e) {
                part.delete();
                main.post(() -> {
                    if (e.kind == ReleaseApi.ApiException.CANCELLED) setState(State.AVAILABLE);
                    else fail(errorFor(e.kind), e.getMessage());
                });
            }
        });
    }

    void cancelDownload() {
        cancel.set(true);
    }

    private void postProgress(long done, long total) {
        final int p = total > 0 ? (int) Math.min(100, done * 100 / total) : 0;
        main.post(() -> {
            if (state == State.DOWNLOADING && p != percent) {
                percent = p;
                notifyListeners();
            }
        });
    }

    private void deleteOldDownloads() {
        File[] old = config.dir.listFiles();
        if (old == null) return;
        for (File f : old) {
            if (f.getName().endsWith(".apk") || f.getName().endsWith(".part")) f.delete();
        }
    }

    private static String firstWord(String text) {
        String t = text.trim();
        int space = t.indexOf(' ');
        return (space < 0 ? t : t.substring(0, space)).toLowerCase(java.util.Locale.ROOT);
    }

    // ---- installing -----------------------------------------------------------------------------------

    /** Android needs the user's one-time permission before an app may install packages. */
    static boolean canInstall(Context c) {
        return c.getPackageManager().canRequestPackageInstalls();
    }

    static void openInstallPermissionSettings(Activity a) {
        a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + a.getPackageName())));
    }

    void install(Activity a) {
        if (apk == null || (state != State.READY && state != State.NEEDS_PERMISSION)) return;
        if (!canInstall(a)) {
            setState(State.NEEDS_PERMISSION);
            return;
        }
        setState(State.INSTALLING);
        final File file = apk;
        io.execute(() -> {
            try {
                config.committer.commit(app, file);
            } catch (final IOException e) {
                main.post(() -> fail(R.string.upd_err_install, String.valueOf(e.getMessage())));
            }
        });
    }

    /** Called by {@link InstallReceiver} with Android's verdict. */
    void onInstallResult(int status, String message) {
        if (status == PackageInstaller.STATUS_SUCCESS) {
            apk = null;
            prefs.saveUpdateCheck(prefs.lastUpdateCheck(), null);
            release = null;
            setState(State.IDLE);
        } else if (status == PackageInstaller.STATUS_FAILURE_ABORTED) {
            setState(State.READY); // the user said no; they can try again
        } else if (status == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE) {
            fail(R.string.upd_err_signature, String.valueOf(message));
        } else {
            fail(R.string.upd_err_install, String.valueOf(message));
        }
    }

    // ---- state ---------------------------------------------------------------------------------------------

    private int errorFor(int kind) {
        switch (kind) {
            case ReleaseApi.ApiException.RATE_LIMIT:
                return R.string.upd_err_limit;
            case ReleaseApi.ApiException.BAD_RESPONSE:
                return R.string.upd_err_response;
            case ReleaseApi.ApiException.NO_FILE:
                return R.string.upd_err_no_file;
            case ReleaseApi.ApiException.INSECURE:
                return R.string.upd_err_insecure;
            case ReleaseApi.ApiException.CHECKSUM:
                return R.string.upd_err_checksum;
            case ReleaseApi.ApiException.SPACE:
                return R.string.upd_err_space;
            default:
                return R.string.upd_err_network;
        }
    }

    private void fail(int res, String detail) {
        errorRes = res;
        errorDetail = detail == null ? "" : detail;
        setState(State.ERROR);
    }

    private void setState(State s) {
        state = s;
        notifyListeners();
    }

    private void notifyListeners() {
        for (Listener l : new ArrayList<>(listeners)) l.onUpdaterChanged();
    }
}
