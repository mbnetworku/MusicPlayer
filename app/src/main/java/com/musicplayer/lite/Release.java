package com.musicplayer.lite;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/**
 * A published version of the app, read from GitHub's "latest release" answer.
 * This class only reads text and compares versions, so it runs on the plain JVM in tests.
 */
final class Release {
    /** Why a release could not be used. */
    static final class ParseException extends Exception {
        static final int BAD_RESPONSE = 1;
        static final int NO_FILE = 2;

        final int kind;

        ParseException(int kind, String message) {
            super(message);
            this.kind = kind;
        }
    }

    /** "1.2.0", without a leading "v". */
    final String version;
    final String notes;
    final String pageUrl;
    final String apkName;
    final String apkUrl;
    final long apkSize;
    /** Lower-case hex SHA-256 published by GitHub for the file, or null. */
    final String sha256;
    /** Address of a "&lt;file&gt;.sha256" text file in the same release, or null. */
    final String sha256Url;
    /** The answer this was read from, kept so it can be remembered without asking again. */
    final String raw;

    private Release(String version, String notes, String pageUrl, String apkName, String apkUrl,
                    long apkSize, String sha256, String sha256Url, String raw) {
        this.version = version;
        this.notes = notes;
        this.pageUrl = pageUrl;
        this.apkName = apkName;
        this.apkUrl = apkUrl;
        this.apkSize = apkSize;
        this.sha256 = sha256;
        this.sha256Url = sha256Url;
        this.raw = raw;
    }

    static Release parse(String json) throws JSONException, ParseException {
        JSONObject o = new JSONObject(json);
        String tag = text(o, "tag_name");
        if (tag.isEmpty()) throw new ParseException(ParseException.BAD_RESPONSE, "no tag");

        String apkName = null;
        String apkUrl = null;
        String sha = null;
        long size = 0;
        JSONArray assets = o.optJSONArray("assets");
        if (assets == null) assets = new JSONArray();
        for (int i = 0; i < assets.length() && apkUrl == null; i++) {
            JSONObject a = assets.optJSONObject(i);
            if (a == null) continue;
            String name = text(a, "name");
            if (name.toLowerCase(Locale.ROOT).endsWith(".apk") && !text(a, "browser_download_url").isEmpty()) {
                apkName = name;
                apkUrl = text(a, "browser_download_url");
                size = a.optLong("size", 0);
                String digest = text(a, "digest");
                if (digest.startsWith("sha256:")) sha = digest.substring(7).trim().toLowerCase(Locale.ROOT);
            }
        }
        if (apkUrl == null) throw new ParseException(ParseException.NO_FILE, "no apk");

        String shaUrl = "";
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.optJSONObject(i);
            if (a != null && (apkName + ".sha256").equals(text(a, "name"))) {
                shaUrl = text(a, "browser_download_url");
            }
        }
        return new Release(stripV(tag), text(o, "body"), text(o, "html_url"), apkName, apkUrl,
                size, sha, shaUrl.isEmpty() ? null : shaUrl, json);
    }

    /** org.json turns JSON null into the string "null"; treat it as missing instead. */
    private static String text(JSONObject o, String key) {
        return o.isNull(key) ? "" : o.optString(key, "");
    }

    private static String stripV(String tag) {
        String t = tag.trim();
        return t.startsWith("v") || t.startsWith("V") ? t.substring(1) : t;
    }

    // ---- versions ------------------------------------------------------------------------

    /** "v1.2.3-beta" becomes {1, 2, 3}; missing or unreadable parts are 0. */
    static int[] parseVersion(String version) {
        int[] out = new int[3];
        String[] parts = stripV(version == null ? "" : version).split("\\.");
        for (int i = 0; i < 3 && i < parts.length; i++) {
            int n = 0;
            for (int k = 0; k < parts[i].length(); k++) {
                char ch = parts[i].charAt(k);
                if (ch < '0' || ch > '9') break;
                n = Math.min(n * 10 + (ch - '0'), 1_000_000);
            }
            out[i] = n;
        }
        return out;
    }

    static int compare(String a, String b) {
        int[] x = parseVersion(a);
        int[] y = parseVersion(b);
        for (int i = 0; i < 3; i++) {
            if (x[i] != y[i]) return x[i] < y[i] ? -1 : 1;
        }
        return 0;
    }

    static boolean isNewer(String candidate, String current) {
        return compare(candidate, current) > 0;
    }
}
