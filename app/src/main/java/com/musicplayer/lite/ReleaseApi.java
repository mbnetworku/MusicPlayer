package com.musicplayer.lite;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Talks to GitHub: asks for the latest release and downloads its app file. Everything here
 * blocks, so call it from a background thread. Only https addresses are used.
 */
final class ReleaseApi {
    private ReleaseApi() {}

    static final class ApiException extends Exception {
        static final int NETWORK = 1;
        static final int RATE_LIMIT = 2;
        static final int NOT_FOUND = 3;
        static final int BAD_RESPONSE = 4;
        static final int INSECURE = 5;
        static final int NO_FILE = 6;
        static final int CHECKSUM = 7;
        static final int SPACE = 8;
        static final int CANCELLED = 9;

        final int kind;

        ApiException(int kind, String message) {
            super(message);
            this.kind = kind;
        }
    }

    interface Progress {
        void onProgress(long done, long total);
    }

    private static final int CONNECT_MS = 10_000;
    private static final int READ_MS = 20_000;
    private static final int MAX_TEXT = 512 * 1024;

    /** Asks GitHub for the newest published release of {@code repo} ("owner/name"). */
    static Release fetchLatest(String apiBase, String repo, String userAgent, boolean allowInsecure)
            throws ApiException {
        String url = apiBase + "/repos/" + repo + "/releases/latest";
        String json = fetchText(url, "application/vnd.github+json", userAgent, allowInsecure, true);
        try {
            return Release.parse(json);
        } catch (Release.ParseException e) {
            throw new ApiException(e.kind == Release.ParseException.NO_FILE
                    ? ApiException.NO_FILE : ApiException.BAD_RESPONSE, e.getMessage());
        } catch (JSONException e) {
            throw new ApiException(ApiException.BAD_RESPONSE, e.getMessage());
        }
    }

    /** Downloads a small text file such as a ".sha256" asset. */
    static String fetchText(String url, String accept, String userAgent, boolean allowInsecure,
                            boolean isApi) throws ApiException {
        requireHttps(url, allowInsecure);
        HttpURLConnection c = null;
        try {
            c = open(url, accept, userAgent);
            int code = c.getResponseCode();
            if (isApi && code == 404) throw new ApiException(ApiException.NOT_FOUND, "no release");
            if (code == 403 || code == 429) throw new ApiException(ApiException.RATE_LIMIT, "HTTP " + code);
            if (code != 200) throw new ApiException(ApiException.BAD_RESPONSE, "HTTP " + code);
            return readText(c.getInputStream());
        } catch (IOException e) {
            throw new ApiException(ApiException.NETWORK, String.valueOf(e.getMessage()));
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /**
     * Downloads {@code url} into {@code dest} and returns the SHA-256 (lower-case hex) of what
     * was written. Stops quietly with CANCELLED when {@code cancel} is set.
     */
    @android.annotation.SuppressLint("UsableSpace")
    static String download(String url, File dest, long expectedSize, Progress progress,
                           AtomicBoolean cancel, String userAgent, boolean allowInsecure)
            throws ApiException {
        requireHttps(url, allowInsecure);
        HttpURLConnection c = null;
        try {
            c = open(url, "application/octet-stream", userAgent);
            int code = c.getResponseCode();
            if (code == 403 || code == 429) throw new ApiException(ApiException.RATE_LIMIT, "HTTP " + code);
            if (code != 200) throw new ApiException(ApiException.BAD_RESPONSE, "HTTP " + code);
            long total = c.getContentLengthLong();
            if (total <= 0) total = expectedSize;
            File dir = dest.getParentFile();
            if (dir != null && total > 0 && dir.getUsableSpace() < total + 5L * 1024 * 1024) {
                throw new ApiException(ApiException.SPACE, "low space");
            }
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[32 * 1024];
                long done = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (cancel.get()) throw new ApiException(ApiException.CANCELLED, "cancelled");
                    out.write(buf, 0, n);
                    md.update(buf, 0, n);
                    done += n;
                    if (progress != null) progress.onProgress(done, total);
                }
                out.getFD().sync();
            }
            if (expectedSize > 0 && dest.length() != expectedSize) {
                throw new ApiException(ApiException.CHECKSUM, "size " + dest.length() + " != " + expectedSize);
            }
            return hex(md.digest());
        } catch (IOException e) {
            throw new ApiException(ApiException.NETWORK, String.valueOf(e.getMessage()));
        } catch (NoSuchAlgorithmException e) {
            throw new ApiException(ApiException.BAD_RESPONSE, "no SHA-256");
        } finally {
            if (c != null) c.disconnect();
        }
    }

    // ---- helpers -----------------------------------------------------------------------------

    private static void requireHttps(String url, boolean allowInsecure) throws ApiException {
        if (!allowInsecure && !url.toLowerCase(Locale.ROOT).startsWith("https://")) {
            throw new ApiException(ApiException.INSECURE, url);
        }
    }

    private static HttpURLConnection open(String url, String accept, String userAgent) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(CONNECT_MS);
        c.setReadTimeout(READ_MS);
        c.setRequestProperty("User-Agent", userAgent);
        c.setRequestProperty("Accept", accept);
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        return c;
    }

    private static String readText(InputStream in) throws IOException {
        try (InputStream is = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > MAX_TEXT) throw new IOException("answer too large");
            }
            return out.toString("UTF-8");
        }
    }

    static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format(Locale.ROOT, "%02x", b));
        return sb.toString();
    }
}
