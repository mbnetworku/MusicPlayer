package com.musicplayer.lite;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.DisplayCutout;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** An activity that is bound to the playback service while it is on screen. */
abstract class BoundActivity extends Activity implements PlaybackService.Listener {
    private static final int TICK_MS = 500;
    private static final int BACKDROP_ART_PX = 96;
    private static final int REQ_DELETE = 9001;
    private static final int REQ_WRITE = 9002;

    /** Non-null between {@link #onServiceReady()} and onStop. */
    protected PlaybackService service;

    private boolean bound;
    /** The look (theme mode and glass) this screen was built with. */
    private int look;
    /** The glass look's window background; null in the flat looks. */
    private BackdropDrawable backdrop;
    /** The glass look follows the cover that is playing (instead of a fixed picture or a plain page). */
    private boolean coverBackdrop;
    private String backdropKey = "";
    /** Songs waiting for the user's answer to a permission or system question before they are deleted. */
    private List<Track> pendingDelete = Collections.emptyList();
    private final Favorites.Listener favoritesListener = this::onFavoritesChanged;
    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (service != null) onTick();
            ticker.postDelayed(this, TICK_MS);
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            if (!(binder instanceof PlaybackService.LocalBinder)) return;
            service = ((PlaybackService.LocalBinder) binder).getService();
            service.addListener(BoundActivity.this);
            onServiceReady();
            onPlaybackChanged();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
        }
    };

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Appearance.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        look = Appearance.signature(this);
        super.onCreate(state);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The look was changed in Settings while this screen waited in the background.
        if (Appearance.signature(this) != look) recreate();
        if (backdrop != null && coverBackdrop) backdrop.setDrifting(true);   // the ambient colours move while the screen is in use
    }

    @Override
    protected void onPause() {
        if (backdrop != null) backdrop.setDrifting(false);
        super.onPause();
    }

    @Override
    protected void onStart() {
        super.onStart();
        bound = bindService(new Intent(this, PlaybackService.class), connection, BIND_AUTO_CREATE);
        ticker.postDelayed(tick, TICK_MS);
        Favorites.get(this).addListener(favoritesListener);
    }

    @Override
    protected void onStop() {
        ticker.removeCallbacks(tick);
        Favorites.get(this).removeListener(favoritesListener);
        if (service != null) {
            service.removeListener(this);
            service = null;
        }
        if (bound) {
            unbindService(connection);
            bound = false;
        }
        super.onStop();
    }

    /** The service is connected; {@link #service} can be used. */
    protected void onServiceReady() {
    }

    /** About twice a second while the screen is visible and the service is connected. */
    protected void onTick() {
    }

    /** A song was added to the favorites or taken off, here or on another screen. Main thread. */
    protected void onFavoritesChanged() {
    }

    /** Track, play state, queue or modes changed. Called on the main thread. */
    protected void onPlaybackUpdated() {
    }

    @Override
    public final void onPlaybackChanged() {
        if (backdrop != null && coverBackdrop) updateBackdrop();
        onPlaybackUpdated();
    }

    private void updateBackdrop() {
        final Track t = service == null ? null : service.currentTrack();
        final String key = t == null ? "" : t.artKey();
        if (key.equals(backdropKey)) return;
        backdropKey = key;
        if (t == null) {
            backdrop.setArt(null, "", true);
            return;
        }
        ArtLoader.get(this).loadAsync(t, BACKDROP_ART_PX, bitmap -> {
            if (backdrop != null && key.equals(backdropKey)) backdrop.setArt(bitmap, key, true);
        });
    }

    /**
     * Call right after setContentView. Keeps content clear of the status and navigation bars
     * when the system draws edge to edge (Android 15 and newer; older versions report zero),
     * styles the system bars for the light or dark look, and applies the liquid-glass look
     * when it is switched on.
     */
    protected void prepareWindow() {
        final View content = findViewById(android.R.id.content);
        content.setOnApplyWindowInsetsListener((v, insets) -> {
            int left = insets.getSystemWindowInsetLeft();
            int top = insets.getSystemWindowInsetTop();
            int right = insets.getSystemWindowInsetRight();
            int bottom = insets.getSystemWindowInsetBottom();
            if (Build.VERSION.SDK_INT >= 28) {
                DisplayCutout cut = insets.getDisplayCutout();
                if (cut != null) {
                    left = Math.max(left, cut.getSafeInsetLeft());
                    top = Math.max(top, cut.getSafeInsetTop());
                    right = Math.max(right, cut.getSafeInsetRight());
                    bottom = Math.max(bottom, cut.getSafeInsetBottom());
                }
            }
            v.setPadding(left, top, right, bottom);
            return insets;
        });

        final boolean glass = Appearance.glass(this);
        Appearance.styleSystemBars(this, glass);
        if (glass) {
            final int background = Appearance.glassBackground(this);
            if (background == Appearance.BG_COVER) {
                coverBackdrop = true;
                backdrop = new BackdropDrawable(this);
                backdrop.setArt(null, "", false);
                getWindow().setBackgroundDrawable(backdrop);
            } else if (background == Appearance.BG_PICTURE || background == Appearance.BG_WALLPAPER) {
                final int kind = BackgroundStore.kindFor(background);
                if (BackgroundStore.has(this, kind)) {
                    // The page is plain until the saved picture has been read.
                    backdrop = new BackdropDrawable(this);
                    backdrop.setPlain(false);
                    getWindow().setBackgroundDrawable(backdrop);
                    BackgroundStore.load(this, kind, bitmap -> {
                        if (backdrop != null && bitmap != null) backdrop.setPicture(bitmap, true);
                    });
                }
            }
            Glass.decorate(content);
        }
    }

    // ---- the "more" menu of songs, and deleting them ---------------------------------------------

    /**
     * The glass action sheet for one song, or for the songs of an album, artist or folder.
     * {@code current} is true for the song that is playing now (it cannot be queued after itself).
     */
    protected final void showSongMenu(final CharSequence title, final List<Track> tracks, final boolean single,
                                      boolean current) {
        if (tracks.isEmpty()) return;
        GlassDialog sheet = GlassDialog.sheet(this).title(title);
        if (!single) {
            sheet.item(R.drawable.ic_play, R.string.action_play, () -> playTracks(tracks, false));
            sheet.item(R.drawable.ic_shuffle, R.string.action_shuffle, () -> playTracks(tracks, true));
        }
        if (!current) {
            sheet.item(R.drawable.ic_playlist_play, R.string.action_play_next, () -> {
                if (service != null) service.playNext(tracks);
                toast(R.string.toast_play_next);
            });
            sheet.item(R.drawable.ic_playlist_add, R.string.action_add_queue, () -> {
                if (service != null) service.enqueue(tracks);
                toast(R.string.toast_added);
            });
        }
        final boolean allFavorite = Favorites.get(this).containsAll(tracks);
        sheet.item(allFavorite ? R.drawable.ic_favorite : R.drawable.ic_favorite_border,
                allFavorite ? (single ? R.string.action_favorite_remove : R.string.action_favorite_remove_all)
                        : (single ? R.string.action_favorite_add : R.string.action_favorite_add_all),
                () -> toggleFavorites(tracks, !allFavorite));
        if (single) {
            sheet.item(R.drawable.ic_info, R.string.action_info, () -> showSongInfo(tracks.get(0)));
        }
        sheet.destructiveItem(R.drawable.ic_delete, single ? R.string.action_delete : R.string.action_delete_many,
                () -> confirmDelete(tracks, title));
        sheet.show();
    }

    /** Adds the songs to the favorites, or takes them off, and says so. */
    protected final void toggleFavorites(List<Track> tracks, boolean add) {
        final Favorites favorites = Favorites.get(this);
        final int changed = add ? favorites.add(tracks) : favorites.remove(tracks);
        if (tracks.size() == 1) {
            toast(add ? R.string.toast_fav_added : R.string.toast_fav_removed);
        } else {
            Toast.makeText(getApplicationContext(), getResources().getQuantityString(
                    add ? R.plurals.toast_fav_added_many : R.plurals.toast_fav_removed_many, changed, changed),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /** Plays {@code tracks} from the top, in order or shuffled. */
    protected void playTracks(List<Track> tracks, boolean shuffle) {
        if (service == null || tracks.isEmpty()) return;
        service.playQueue(tracks, shuffle ? new java.util.Random().nextInt(tracks.size()) : 0, shuffle);
    }

    private void showSongInfo(Track t) {
        String text = getString(R.string.info_body, t.artist, t.album, Fmt.time(t.durationMs), t.path());
        GlassDialog.alert(this).icon(R.drawable.ic_info).title(t.title).message(text)
                .negative(R.string.dialog_close, null).show();
    }

    /** Asks "delete for good?" in a glass pop-up. Nothing is deleted until the user agrees. */
    protected final void confirmDelete(final List<Track> tracks, CharSequence name) {
        if (tracks.isEmpty()) return;
        final int n = tracks.size();
        GlassDialog.alert(this)
                .icon(R.drawable.ic_delete)
                .title(n == 1 ? getString(R.string.delete_title_one, name)
                        : getResources().getQuantityString(R.plurals.delete_title_many, n, n))
                .message(R.string.delete_body)
                .positive(R.string.delete_confirm, () -> startDelete(tracks))
                .negative(R.string.dialog_cancel, null)
                .show();
    }

    private void startDelete(List<Track> tracks) {
        switch (MediaDeleter.planFor(Build.VERSION.SDK_INT, Permissions.hasWrite(this))) {
            case MediaDeleter.SYSTEM:
                try {
                    IntentSender sender = MediaDeleter.systemRequest(getContentResolver(), tracks);
                    pendingDelete = tracks;
                    startIntentSenderForResult(sender, REQ_DELETE, null, 0, 0, 0);
                } catch (IntentSender.SendIntentException | RuntimeException e) {
                    pendingDelete = Collections.emptyList();
                    toast(R.string.delete_failed);
                }
                break;
            case MediaDeleter.NEEDS_PERMISSION:
                pendingDelete = tracks;
                Permissions.askForWrite(this, REQ_WRITE, () -> pendingDelete = Collections.emptyList());
                break;
            default:
                finishDelete(MediaDeleter.deleteDirect(this, tracks), tracks.size());
                break;
        }
    }

    private void finishDelete(Set<Long> gone, int asked) {
        pendingDelete = Collections.emptyList();
        if (!gone.isEmpty()) {
            final Library library = Library.get(this);
            // A deleted song is no longer a favorite. Looked up now, while the library still has it.
            final List<Track> deleted = new ArrayList<>();
            for (long id : gone) {
                Track t = library.byId(id);
                if (t != null) deleted.add(t);
            }
            Favorites.get(this).remove(deleted);
            if (service != null) service.removeTracks(gone);
            library.refresh();
        }
        if (gone.size() == asked) {
            Toast.makeText(getApplicationContext(),
                    getResources().getQuantityString(R.plurals.deleted_count, gone.size(), gone.size()),
                    Toast.LENGTH_SHORT).show();
        } else {
            toast(R.string.delete_failed);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_DELETE) return;
        List<Track> asked = pendingDelete;
        pendingDelete = Collections.emptyList();
        if (resultCode == RESULT_OK && !asked.isEmpty()) finishDelete(MediaDeleter.ids(asked), asked.size());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQ_WRITE) return;
        List<Track> asked = pendingDelete;
        pendingDelete = Collections.emptyList();
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED && !asked.isEmpty()) {
            finishDelete(MediaDeleter.deleteDirect(this, asked), asked.size());
        } else if (!asked.isEmpty() && !shouldShowRequestPermissionRationale(permissions.length > 0 ? permissions[0] : "")) {
            Permissions.askForSettings(this, R.drawable.ic_delete, R.string.perm_write_title,
                    R.string.perm_write_settings, null);
        }
    }

    protected final void toast(int res) {
        Toast.makeText(getApplicationContext(), res, Toast.LENGTH_SHORT).show();
    }
}
