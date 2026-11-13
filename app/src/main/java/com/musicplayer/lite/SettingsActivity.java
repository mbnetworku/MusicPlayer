package com.musicplayer.lite;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Appearance, library options, updates and a short About. */
public class SettingsActivity extends BoundActivity implements Updater.Listener {
    private Prefs prefs;
    private Library library;
    private Updater updater;
    private boolean scanning;

    private static final int REQ_PICK = 9100;
    private static final int REQ_WALLPAPER = 9101;

    private TextView action;
    private TextView secondary;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_settings);
        prepareWindow();
        prefs = Prefs.get(this);
        library = Library.get(this);
        updater = Updater.get(this);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        bindAppearance();
        bindBackground();
        bindClarity();
        final Switch ambient = setupRow(R.id.row_ambient, R.string.set_ambient, R.string.set_ambient_sub, true);
        ambient.setSaveEnabled(false);   // the setting is read again below, never restored from an old state
        ambient.setChecked(prefs.glassBackground() == Appearance.BG_COVER);
        ambient.setOnCheckedChangeListener((b, on) ->
                applyBackground(on ? Appearance.BG_COVER : Appearance.BG_PLAIN));
        bindLibrary();
        bindUpdates();
        ((TextView) findViewById(R.id.about_text)).setText(R.string.about_body);
        findViewById(R.id.about_source).setOnClickListener(v -> openUrl(repoUrl()));
    }

    @Override
    protected void onStart() {
        super.onStart();
        updater.addListener(this);
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Back from Android's "install unknown apps" screen with the permission granted: carry on.
        if (updater.state() == Updater.State.NEEDS_PERMISSION && Updater.canInstall(this)) {
            updater.install(this);
        }
    }

    @Override
    protected void onStop() {
        updater.removeListener(this);
        super.onStop();
    }

    @Override
    public void onUpdaterChanged() {
        render();
    }

    // ---- appearance --------------------------------------------------------------------------------

    private void bindAppearance() {
        RadioGroup group = findViewById(R.id.theme_group);
        int mode = prefs.themeMode();
        group.check(mode == Appearance.LIGHT ? R.id.theme_light
                : mode == Appearance.DARK ? R.id.theme_dark : R.id.theme_system);
        group.setOnCheckedChangeListener((g, id) -> {
            int chosen = id == R.id.theme_light ? Appearance.LIGHT
                    : id == R.id.theme_dark ? Appearance.DARK : Appearance.FOLLOW_DEVICE;
            if (chosen != prefs.themeMode()) {
                prefs.setThemeMode(chosen);
                recreate();
            }
        });

        // The device's own setting, not this app's override.
        Configuration device = Resources.getSystem().getConfiguration();
        boolean deviceDark = (device.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        ((TextView) findViewById(R.id.theme_system_now)).setText(getString(R.string.theme_system_now,
                getString(deviceDark ? R.string.mode_dark : R.string.mode_light)));

        Switch glass = setupRow(R.id.row_glass, R.string.set_glass, R.string.set_glass_sub, true);
        glass.setChecked(prefs.glass());
        glass.setOnCheckedChangeListener((b, on) -> {
            prefs.setGlass(on);
            recreate();
        });

        Switch vibrate = setupRow(R.id.row_vibrate, R.string.set_vibrate, R.string.set_vibrate_sub, true);
        vibrate.setChecked(prefs.vibrate());
        vibrate.setOnCheckedChangeListener((b, on) -> {
            prefs.setVibrate(on);
            if (on) Haptics.tap(b);
        });
        Switch buzz = setupRow(R.id.row_vibrate_playing, R.string.set_vibrate_playing,
                R.string.set_vibrate_playing_sub, true);
        buzz.setChecked(prefs.vibratePlaying());
        buzz.setOnCheckedChangeListener((b, on) -> {
            prefs.setVibratePlaying(on);
            if (on) Haptics.tap(b);
        });
        Switch tapSkip = setupRow(R.id.row_cover_tap, R.string.set_cover_tap, R.string.set_cover_tap_sub, true);
        tapSkip.setChecked(prefs.coverSwipe());
        tapSkip.setOnCheckedChangeListener((b, on) -> prefs.setCoverSwipe(on));
        bindBarStyle();
    }

    /** How see-through the glass panels are: Clear, Soft or Frosted. */
    private void bindClarity() {
        final int[] ids = {R.id.clarity_clear, R.id.clarity_soft, R.id.clarity_frosted};
        final RadioGroup g = findViewById(R.id.clarity_group);
        g.check(ids[prefs.glassClarity()]);
        g.setOnCheckedChangeListener((group, id) -> {
            for (int i = 0; i < ids.length; i++) {
                if (ids[i] == id && i != prefs.glassClarity()) {
                    group.setOnCheckedChangeListener(null);
                    prefs.setGlassClarity(i);
                    recreate();   // every panel is drawn again with the new transparency
                }
            }
        });
    }

    private void bindBarStyle() {
        final int[] ids = {R.id.bar_style_bars, R.id.bar_style_dots, R.id.bar_style_mirror, R.id.bar_style_wave};
        RadioGroup g = findViewById(R.id.bar_style_group);
        g.check(ids[prefs.barStyle()]);
        g.setOnCheckedChangeListener((group, id) -> {
            for (int i = 0; i < ids.length; i++) if (ids[i] == id) prefs.setBarStyle(i);
        });
    }

    // ---- glass background --------------------------------------------------------------------------

    private static int backgroundForId(int id) {
        if (id == R.id.bg_cover) return Appearance.BG_COVER;
        if (id == R.id.bg_picture) return Appearance.BG_PICTURE;
        if (id == R.id.bg_wallpaper) return Appearance.BG_WALLPAPER;
        return Appearance.BG_PLAIN;
    }

    private static int idForBackground(int background) {
        switch (background) {
            case Appearance.BG_COVER:
                return R.id.bg_cover;
            case Appearance.BG_PICTURE:
                return R.id.bg_picture;
            case Appearance.BG_WALLPAPER:
                return R.id.bg_wallpaper;
            default:
                return R.id.bg_plain;
        }
    }

    private void bindBackground() {
        final RadioGroup group = findViewById(R.id.bg_group);
        group.check(idForBackground(prefs.glassBackground()));
        refreshBackgroundTexts();
        group.setOnCheckedChangeListener((g, id) -> {
            final int chosen = backgroundForId(id);
            if (chosen == prefs.glassBackground()) return;
            if (chosen == Appearance.BG_PICTURE && !BackgroundStore.has(this, BackgroundStore.KIND_PICTURE)) {
                group.check(idForBackground(prefs.glassBackground()));   // applied once a picture is picked
                pickPicture();
            } else if (chosen == Appearance.BG_WALLPAPER && !BackgroundStore.has(this, BackgroundStore.KIND_WALLPAPER)) {
                group.check(idForBackground(prefs.glassBackground()));   // applied once the wallpaper is read
                useWallpaper();
            } else {
                applyBackground(chosen);
            }
        });
        findViewById(R.id.bg_pick).setOnClickListener(v -> pickPicture());
        findViewById(R.id.bg_wallpaper_update).setOnClickListener(v -> useWallpaper());
    }

    private void refreshBackgroundTexts() {
        final boolean hasPicture = BackgroundStore.has(this, BackgroundStore.KIND_PICTURE);
        final boolean hasWallpaper = BackgroundStore.has(this, BackgroundStore.KIND_WALLPAPER);
        ((TextView) findViewById(R.id.bg_picture_sub)).setText(
                hasPicture ? R.string.set_bg_picture_set : R.string.set_bg_picture_none);
        ((TextView) findViewById(R.id.bg_pick)).setText(hasPicture ? R.string.set_bg_change : R.string.set_bg_choose);
        ((TextView) findViewById(R.id.bg_wallpaper_sub)).setText(!hasWallpaper ? R.string.set_bg_wallpaper_none
                : prefs.wallpaperIsColors() ? R.string.set_bg_wallpaper_colors : R.string.set_bg_wallpaper_picture);
        findViewById(R.id.bg_wallpaper_update).setVisibility(hasWallpaper ? View.VISIBLE : View.GONE);
    }

    private void applyBackground(int background) {
        // Show the new choice as selected before the screen is rebuilt. Android saves that state and puts
        // it back in the new screen; if it still showed the old choice, restoring it would undo this one.
        RadioGroup group = findViewById(R.id.bg_group);
        group.setOnCheckedChangeListener(null);
        group.check(idForBackground(background));
        prefs.setGlassBackground(background);
        recreate();
    }

    private void pickPicture() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("image/*");
        try {
            startActivityForResult(pick, REQ_PICK);
        } catch (ActivityNotFoundException e) {
            toast(R.string.bg_pick_failed);
        }
    }

    private void useWallpaper() {
        Permissions.askForWallpaper(this, this::readWallpaper, null);
    }

    private void readWallpaper() {
        // Up to Android 12 the wallpaper picture needs the storage permission; later versions give
        // apps only its colours, which readWallpaper falls back to.
        if (Build.VERSION.SDK_INT <= 32
                && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_WALLPAPER);
            return;
        }
        readWallpaperNow();
    }

    private void readWallpaperNow() {
        final Context app = getApplicationContext();
        new Thread(() -> {
            final int result = BackgroundStore.saveWallpaper(app);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (result == BackgroundStore.FAILED) {
                    toast(R.string.bg_wallpaper_failed);
                    return;
                }
                prefs.setWallpaperIsColors(result == BackgroundStore.COLORS);
                applyBackground(Appearance.BG_WALLPAPER);
            });
        }).start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        final Context app = getApplicationContext();
        new Thread(() -> {
            final boolean ok = BackgroundStore.savePicture(app, uri);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (ok) applyBackground(Appearance.BG_PICTURE);
                else toast(R.string.bg_pick_failed);
            });
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQ_WALLPAPER) return;
        // Granted: read the picture. Refused: the wallpaper's colours are still available.
        readWallpaperNow();
    }

    // ---- library ------------------------------------------------------------------------------------

    private void bindLibrary() {
        Switch showAll = setupRow(R.id.row_show_all, R.string.set_show_all, R.string.set_show_all_sub, true);
        showAll.setChecked(prefs.showAllAudio());
        showAll.setOnCheckedChangeListener((b, on) -> {
            prefs.setShowAllAudio(on);
            library.refresh();
        });

        View scan = findViewById(R.id.row_scan);
        setupRow(R.id.row_scan, R.string.set_scan, R.string.set_scan_sub, false);
        scan.setOnClickListener(v -> startScan());
    }

    private void startScan() {
        if (scanning) return;
        if (!Library.hasPermission(this)) {
            Toast.makeText(getApplicationContext(), R.string.empty_permission_title, Toast.LENGTH_SHORT).show();
            return;
        }
        scanning = true;
        Toast.makeText(getApplicationContext(), R.string.scanning, Toast.LENGTH_SHORT).show();
        final Library lib = library;
        StorageScanner.scan(this, lib.knownPaths(), requested -> {
            scanning = false;
            Toast.makeText(getApplicationContext(),
                    requested == 0 ? getString(R.string.scan_nothing_new)
                            : getResources().getQuantityString(R.plurals.scan_added, requested, requested),
                    Toast.LENGTH_SHORT).show();
            lib.refresh();
        });
    }

    // ---- updates ------------------------------------------------------------------------------------

    private void bindUpdates() {
        setupRow(R.id.row_version, 0, 0, false);
        action = findViewById(R.id.upd_action);
        secondary = findViewById(R.id.upd_secondary);

        Switch auto = setupRow(R.id.row_auto, R.string.upd_auto, R.string.upd_auto_sub, true);
        auto.setChecked(prefs.autoUpdateCheck());
        auto.setOnCheckedChangeListener((b, on) -> prefs.setAutoUpdateCheck(on));

        if (Appearance.glass(this)) {
            action.setBackground(Glass.pill(this, true));
            secondary.setBackground(Glass.pill(this, false));
        }
        action.setOnClickListener(v -> onActionClicked());
        secondary.setOnClickListener(v -> {
            Release r = updater.release();
            openUrl(r != null && !r.pageUrl.isEmpty() ? r.pageUrl : repoUrl() + "/releases");
        });
    }

    private void onActionClicked() {
        switch (updater.state()) {
            case AVAILABLE:
                updater.download();
                break;
            case DOWNLOADING:
                updater.cancelDownload();
                break;
            case READY:
                updater.install(this);
                break;
            case NEEDS_PERMISSION:
                Permissions.askForInstall(this, () -> Updater.openInstallPermissionSettings(this));
                break;
            case ERROR:
                if (updater.newerRelease() != null) updater.download();
                else updater.check(false);
                break;
            case CHECKING:
            case VERIFYING:
            case INSTALLING:
                break;
            default:
                updater.check(false);
                break;
        }
    }

    private void render() {
        if (action == null) return;
        renderUpdate(this, findViewById(android.R.id.content), updater.status());
    }

    /** Fills the update card from a snapshot. Static, so it can be tried with any state. */
    static void renderUpdate(Context c, View root, Updater.Status s) {
        final Release r = s.release;
        final View versionRow = root.findViewById(R.id.row_version);
        final TextView action = root.findViewById(R.id.upd_action);
        final TextView secondary = root.findViewById(R.id.upd_secondary);
        final TextView notesTitle = root.findViewById(R.id.upd_notes_title);
        final TextView notes = root.findViewById(R.id.upd_notes);
        final TextView hint = root.findViewById(R.id.upd_hint);
        final ProgressBar progress = root.findViewById(R.id.upd_progress);
        ((TextView) versionRow.findViewById(R.id.set_title)).setText(c.getString(R.string.upd_version, s.currentVersion));

        String status;
        int actionText = R.string.upd_check;
        boolean actionEnabled = true;
        boolean showNotes = false;
        boolean showProgress = false;
        boolean indeterminate = false;
        String hintText = null;
        boolean showSecondary = false;

        switch (s.state) {
            case CHECKING:
                status = c.getString(R.string.upd_checking);
                actionEnabled = false;
                break;
            case UP_TO_DATE:
                status = c.getString(R.string.upd_up_to_date) + checkedSuffix(c, s.lastCheck);
                break;
            case NONE_PUBLISHED:
                status = c.getString(R.string.upd_none);
                break;
            case AVAILABLE:
                status = availableText(c, r);
                actionText = R.string.upd_download;
                showNotes = r != null && !cleanNotes(r).isEmpty();
                showSecondary = r != null;
                break;
            case DOWNLOADING:
                status = c.getString(R.string.upd_downloading, s.percent);
                actionText = R.string.upd_cancel;
                showProgress = true;
                break;
            case VERIFYING:
                status = c.getString(R.string.upd_verifying);
                actionText = R.string.upd_download;
                actionEnabled = false;
                showProgress = true;
                indeterminate = true;
                break;
            case READY:
                status = c.getString(R.string.upd_ready);
                actionText = R.string.upd_install;
                break;
            case NEEDS_PERMISSION:
                status = c.getString(R.string.upd_allow_title);
                hintText = c.getString(R.string.upd_allow_body);
                actionText = R.string.upd_allow_action;
                break;
            case INSTALLING:
                status = c.getString(R.string.upd_installing);
                actionText = R.string.upd_install;
                actionEnabled = false;
                break;
            case ERROR:
                status = s.errorRes == R.string.upd_err_install
                        ? c.getString(R.string.upd_err_install, s.errorDetail)
                        : c.getString(s.errorRes);
                actionText = R.string.upd_retry;
                showSecondary = r != null;
                break;
            default:
                status = s.lastCheck == 0 ? c.getString(R.string.upd_never) : checkedSuffix(c, s.lastCheck).trim();
                break;
        }

        TextView sub = versionRow.findViewById(R.id.set_sub);
        sub.setText(status);
        sub.setVisibility(View.VISIBLE);
        action.setText(actionText);
        action.setEnabled(actionEnabled);
        action.setAlpha(actionEnabled ? 1f : 0.5f);
        secondary.setText(R.string.upd_view);
        secondary.setVisibility(showSecondary ? View.VISIBLE : View.GONE);

        notesTitle.setVisibility(showNotes ? View.VISIBLE : View.GONE);
        notes.setVisibility(showNotes ? View.VISIBLE : View.GONE);
        if (showNotes) notes.setText(cleanNotes(r));

        progress.setVisibility(showProgress ? View.VISIBLE : View.GONE);
        progress.setIndeterminate(indeterminate);
        if (showProgress && !indeterminate) progress.setProgress(s.percent);

        hint.setVisibility(hintText == null ? View.GONE : View.VISIBLE);
        if (hintText != null) hint.setText(hintText);
    }

    private static String availableText(Context c, Release r) {
        if (r == null) return "";
        return r.apkSize > 0
                ? c.getString(R.string.upd_available_size, r.version, Fmt.size(r.apkSize))
                : c.getString(R.string.upd_available, r.version);
    }

    private static String checkedSuffix(Context c, long lastCheck) {
        if (lastCheck == 0) return "";
        CharSequence when = DateUtils.getRelativeTimeSpanString(lastCheck, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
        return "\n" + c.getString(R.string.upd_checked, when);
    }

    /** Release notes are Markdown; show them as plain text. */
    private static String cleanNotes(Release r) {
        return r.notes.replaceAll("(?m)^#+\\s*", "").replace("**", "").replace("`", "").trim();
    }

    // ---- helpers ------------------------------------------------------------------------------------

    private Switch setupRow(int rowId, int title, int sub, boolean withSwitch) {
        return setupRow(findViewById(android.R.id.content), rowId, title, sub, withSwitch);
    }

    /** Sets a settings row's texts and shows its switch when it has one. */
    static Switch setupRow(View root, int rowId, int title, int sub, boolean withSwitch) {
        View row = root.findViewById(rowId);
        badge(row, rowId);
        TextView titleView = row.findViewById(R.id.set_title);
        if (title != 0) titleView.setText(title);
        TextView subView = row.findViewById(R.id.set_sub);
        if (sub != 0) {
            subView.setText(sub);
            subView.setVisibility(View.VISIBLE);
        }
        final Switch toggle = row.findViewById(R.id.set_switch);
        // Every row's switch has the same id, so Android would copy one switch's saved position onto the
        // others when the screen is rebuilt. Each switch is set from its own saved setting instead.
        toggle.setSaveEnabled(false);
        if (withSwitch) {
            toggle.setVisibility(View.VISIBLE);
            row.setOnClickListener(v -> toggle.toggle());
        }
        return toggle;
    }

    /** A small coloured icon in front of a row, so each option is easy to find. */
    private static void badge(View row, int rowId) {
        final int icon;
        final int color;
        if (rowId == R.id.row_ambient) {
            icon = R.drawable.ic_music_note;
            color = 0xFFAF52DE;
        } else if (rowId == R.id.row_glass) {
            icon = R.drawable.ic_image;
            color = 0xFF5856D6;
        } else if (rowId == R.id.row_vibrate) {
            icon = R.drawable.ic_more_horiz;
            color = 0xFFFF9500;
        } else if (rowId == R.id.row_vibrate_playing) {
            icon = R.drawable.ic_music_note;
            color = 0xFFFF2D55;
        } else if (rowId == R.id.row_cover_tap) {
            icon = R.drawable.ic_skip_next;
            color = 0xFF32ADE6;
        } else if (rowId == R.id.row_show_all) {
            icon = R.drawable.ic_queue_music;
            color = 0xFF8E8E93;
        } else if (rowId == R.id.row_scan) {
            icon = R.drawable.ic_refresh;
            color = 0xFF34C759;
        } else if (rowId == R.id.row_auto) {
            icon = R.drawable.ic_system_update;
            color = 0xFFFF3B30;
        } else if (rowId == R.id.row_version) {
            icon = R.drawable.ic_info;
            color = 0xFF8E8E93;
        } else {
            return;
        }
        final ImageView view = row.findViewById(R.id.set_icon);
        if (view == null) return;
        view.setImageResource(icon);
        view.getBackground().mutate().setTint(color);
        view.setVisibility(View.VISIBLE);
    }

    private String repoUrl() {
        return "https://github.com/" + getString(R.string.update_repo);
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException ignored) {
            // No browser on this device.
        }
    }
}
