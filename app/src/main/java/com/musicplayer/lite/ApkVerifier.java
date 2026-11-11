package com.musicplayer.lite;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.io.File;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Checks a downloaded file before Android is asked to install it: it must be this app, newer
 * than the installed version, and signed by the same key (Android refuses anything else, and
 * saying so here gives a clear message instead of a vague install failure).
 */
final class ApkVerifier {
    private ApkVerifier() {}

    /** Returns 0 when the file can be installed over this app, otherwise a string resource saying why not. */
    static int check(Context c, File apk, String installedVersion) {
        PackageManager pm = c.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28
                ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo archive = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        if (archive == null) return R.string.upd_err_invalid;
        Set<String> installedSigners;
        try {
            installedSigners = signers(pm.getPackageInfo(c.getPackageName(), flags));
        } catch (PackageManager.NameNotFoundException e) {
            return R.string.upd_err_invalid;
        }
        return judge(c.getPackageName(), archive.packageName, archive.versionName, installedVersion,
                signers(archive), installedSigners);
    }

    /** The decision itself, apart from reading the files, so it can be tried with any input. */
    static int judge(String ownPackage, String archivePackage, String archiveVersion, String installedVersion,
                     Set<String> archiveSigners, Set<String> installedSigners) {
        if (!ownPackage.equals(archivePackage)) return R.string.upd_err_wrong_app;
        if (!Release.isNewer(archiveVersion, installedVersion)) return R.string.upd_err_old;
        // Only a clear mismatch is reported here. If a device does not let us read one side's
        // certificates, Android's installer still makes the final call.
        if (!archiveSigners.isEmpty() && !installedSigners.isEmpty()
                && Collections.disjoint(archiveSigners, installedSigners)) {
            return R.string.upd_err_signature;
        }
        return 0;
    }

    private static Set<String> signers(PackageInfo info) {
        Set<String> out = new HashSet<>();
        Signature[] all = null;
        if (Build.VERSION.SDK_INT >= 28) {
            if (info.signingInfo != null) {
                all = info.signingInfo.hasMultipleSigners()
                        ? info.signingInfo.getApkContentsSigners()
                        : info.signingInfo.getSigningCertificateHistory();
            }
        } else {
            all = info.signatures;
        }
        if (all == null) return out;
        for (Signature s : all) out.add(sha256(s.toByteArray()));
        return out;
    }

    private static String sha256(byte[] data) {
        try {
            return ReleaseApi.hex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
