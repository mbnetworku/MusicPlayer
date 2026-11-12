package com.musicplayer.lite;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

/**
 * Every permission question starts with a liquid-glass pop-up that says why the permission is
 * needed. "Allow" then shows Android's own question; "Not allowed" leaves the feature off.
 * What each Android version needs differs, so it is decided here in one place.
 */
final class Permissions {
    private Permissions() {}

    /** Reading music, and showing the playback controls in the notification shade on Android 13+. */
    @SuppressLint("InlinedApi")   // the newer permissions are only listed on the versions that have them
    static String[] musicPermissions(int sdk) {
        if (sdk >= 33) return new String[]{Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS};
        return new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
    }

    /** Asks in a glass pop-up, then Android. {@code denied} runs when the user picks "Not allowed". */
    static void askForMusic(final Activity a, final int requestCode, Runnable denied) {
        GlassDialog.alert(a)
                .icon(R.drawable.ic_music_note)
                .title(R.string.perm_music_title)
                .message(Build.VERSION.SDK_INT >= 33 ? R.string.perm_music_body_33 : R.string.perm_music_body)
                .positive(R.string.perm_allow,
                        () -> a.requestPermissions(musicPermissions(Build.VERSION.SDK_INT), requestCode))
                .negative(R.string.perm_deny, denied)
                .show();
    }

    /** Android 10 and older need write access to delete a file the app did not create. */
    static boolean hasWrite(Context c) {
        return hasWrite(Build.VERSION.SDK_INT,
                c.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
    }

    /** Android 11 and newer never need it (the system asks per delete); older versions need the grant. */
    static boolean hasWrite(int sdk, boolean granted) {
        return sdk >= 30 || granted;
    }

    static void askForWrite(final Activity a, final int requestCode, Runnable denied) {
        GlassDialog.alert(a)
                .icon(R.drawable.ic_delete)
                .title(R.string.perm_write_title)
                .message(R.string.perm_write_body)
                .positive(R.string.perm_allow,
                        () -> a.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, requestCode))
                .negative(R.string.perm_deny, denied)
                .show();
    }

    /** Android will not ask again: explain, and offer this app's page in Android settings. */
    static void askForSettings(final Activity a, int icon, int title, int body, Runnable declined) {
        GlassDialog.alert(a)
                .icon(icon)
                .title(title)
                .message(body)
                .positive(R.string.perm_open_settings, () -> openAppSettings(a))
                .negative(R.string.perm_not_now, declined)
                .show();
    }

    /** Before sending the user to Android's "install unknown apps" page for updates. */
    static void askForInstall(final Activity a, final Runnable proceed) {
        GlassDialog.alert(a)
                .icon(R.drawable.ic_system_update)
                .title(R.string.perm_install_title)
                .message(R.string.perm_install_body)
                .positive(R.string.perm_allow, proceed)
                .negative(R.string.perm_deny, null)
                .show();
    }

    /** Before reading the device wallpaper for the glass background. */
    static void askForWallpaper(final Activity a, final Runnable proceed, Runnable declined) {
        GlassDialog.alert(a)
                .icon(R.drawable.ic_image)
                .title(R.string.perm_wallpaper_title)
                .message(R.string.perm_wallpaper_body)
                .positive(R.string.perm_allow, proceed)
                .negative(R.string.perm_deny, declined)
                .show();
    }

    static void openAppSettings(Activity a) {
        a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", a.getPackageName(), null)));
    }
}
