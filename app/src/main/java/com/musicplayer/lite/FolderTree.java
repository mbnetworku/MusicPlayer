package com.musicplayer.lite;

import android.annotation.SuppressLint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Directory tree built from the paths of all indexed tracks. Only folders that
 * contain music (directly or deeper) exist in the tree.
 */
final class FolderTree {
    private FolderTree() {}

    private static final String SEP = " › ";

    static final class Node {
        final String path;
        final String name;
        final Node parent;
        final List<Node> children = new ArrayList<>();
        final List<Track> tracks = new ArrayList<>();
        /** Number of tracks in this folder and everything below it. */
        int total;

        Node(String path, String name, Node parent) {
            this.path = path;
            this.name = name;
            this.parent = parent;
        }
    }

    /** A sub-folder as shown in a list: single-child chains are collapsed into one row. */
    static final class Entry {
        final Node node;
        final String label;

        Entry(Node node, String label) {
            this.node = node;
            this.label = label;
        }
    }

    static Node build(List<Track> all) {
        Node root = new Node("", "", null);
        Map<String, Node> map = new HashMap<>();
        map.put("", root);
        for (Track t : all) {
            ensure(map, t.dir).tracks.add(t);
        }
        finish(root);
        return root;
    }

    private static Node ensure(Map<String, Node> map, String dir) {
        Node n = map.get(dir);
        if (n != null) return n;
        int slash = dir.lastIndexOf('/');
        String parentPath = slash <= 0 ? "" : dir.substring(0, slash);
        String name = slash >= 0 ? dir.substring(slash + 1) : dir;
        Node parent = ensure(map, parentPath);
        n = new Node(dir, name, parent);
        parent.children.add(n);
        map.put(dir, n);
        return n;
    }

    private static final Comparator<Node> BY_NAME = (a, b) -> natural(a.name, b.name);
    private static final Comparator<Track> BY_FILE = (a, b) -> natural(a.fileName, b.fileName);

    private static void finish(Node n) {
        int sum = n.tracks.size();
        for (Node c : n.children) {
            finish(c);
            sum += c.total;
        }
        n.total = sum;
        Collections.sort(n.children, BY_NAME);
        Collections.sort(n.tracks, BY_FILE);
    }

    /** First folder that actually branches or holds tracks: skips "/storage/emulated" style prefixes. */
    static Node visibleRoot(Node root) {
        Node n = root;
        while (n.tracks.isEmpty() && n.children.size() == 1) n = n.children.get(0);
        return n;
    }

    /**
     * The folder shown one level above {@code n}, skipping the single-child chains that
     * {@link #entries} collapses. Null when {@code n} is already the top.
     */
    static Node parentInView(Node n, Node visibleRoot) {
        if (n == visibleRoot || n.parent == null) return null;
        Node p = n.parent;
        while (p != visibleRoot && p.parent != null && p.tracks.isEmpty() && p.children.size() == 1) {
            p = p.parent;
        }
        return p;
    }

    static List<Entry> entries(Node n) {
        List<Entry> out = new ArrayList<>(n.children.size());
        for (Node c : n.children) {
            Node cur = c;
            String label = step("", cur);
            while (cur.tracks.isEmpty() && cur.children.size() == 1) {
                cur = cur.children.get(0);
                label = step(label, cur);
            }
            out.add(new Entry(cur, label));
        }
        return out;
    }

    /** "Internal storage › Music › Rock". */
    static String breadcrumb(Node n) {
        List<Node> chain = new ArrayList<>();
        for (Node c = n; c != null && c.parent != null; c = c.parent) chain.add(c);
        Collections.reverse(chain);
        String label = "";
        for (Node c : chain) label = step(label, c);
        return label;
    }

    private static String step(String label, Node n) {
        String f = friendly(n.path);
        if (f != null) return f;
        return label.isEmpty() ? n.name : label + SEP + n.name;
    }

    /** All tracks in this folder, then in its sub-folders, in display order. */
    static List<Track> flatten(Node n) {
        List<Track> out = new ArrayList<>(n.total);
        collect(n, out);
        return out;
    }

    private static void collect(Node n, List<Track> out) {
        out.addAll(n.tracks);
        for (Node c : n.children) collect(c, out);
    }

    static Node find(Node root, String path) {
        if (path == null || path.isEmpty()) return null;
        Node n = root;
        outer:
        while (n != null && !n.path.equals(path)) {
            for (Node c : n.children) {
                if (path.equals(c.path) || path.startsWith(c.path + "/")) {
                    n = c;
                    continue outer;
                }
            }
            return null;
        }
        return n;
    }

    static int countFolders(Node n) {
        int c = n.tracks.isEmpty() ? 0 : 1;
        for (Node ch : n.children) c += countFolders(ch);
        return c;
    }

    // ---- storage volume names ------------------------------------------------

    private static final Pattern VOLUME_ID =
            Pattern.compile("^/(?:storage|mnt/media_rw)/([0-9A-Fa-f]{4}-[0-9A-Fa-f]{4})$");
    private static final Pattern USB_ROOT =
            Pattern.compile("(?i)^/(?:storage|mnt)/(?:usb[^/]*|udisk[^/]*|usb_storage/[^/]+(?:/udisk[^/]*)?)$");

    /** A readable name when {@code path} is the root of a storage volume, otherwise null. */
    @SuppressLint("SdCardPath")
    static String friendly(String path) {
        switch (path) {
            case "/storage/emulated/0":
            case "/storage/self/primary":
            case "/storage/sdcard0":
            case "/mnt/shell/emulated/0":
            case "/mnt/sdcard":
            case "/sdcard":
                return "Internal storage";
            case "/storage/sdcard1":
            case "/storage/extSdCard":
            case "/storage/external_sd":
            case "/mnt/extSdCard":
            case "/mnt/external_sd":
            case "/mnt/sdcard2":
                return "SD card";
            default:
                break;
        }
        java.util.regex.Matcher m = VOLUME_ID.matcher(path);
        if (m.matches()) return "SD card or USB (" + m.group(1).toUpperCase(java.util.Locale.ROOT) + ")";
        if (USB_ROOT.matcher(path).matches()) return "USB drive";
        return null;
    }

    // ---- natural ordering ("Track 2" before "Track 10") -----------------------

    static int natural(String a, String b) {
        int i = 0, j = 0;
        final int la = a.length(), lb = b.length();
        while (i < la && j < lb) {
            char ca = a.charAt(i), cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int si = i, sj = j;
                while (i < la && Character.isDigit(a.charAt(i))) i++;
                while (j < lb && Character.isDigit(b.charAt(j))) j++;
                while (si < i - 1 && a.charAt(si) == '0') si++;
                while (sj < j - 1 && b.charAt(sj) == '0') sj++;
                int lenA = i - si, lenB = j - sj;
                if (lenA != lenB) return lenA < lenB ? -1 : 1;
                for (int k = 0; k < lenA; k++) {
                    char x = a.charAt(si + k), y = b.charAt(sj + k);
                    if (x != y) return x < y ? -1 : 1;
                }
            } else {
                char x = Character.toLowerCase(ca), y = Character.toLowerCase(cb);
                if (x != y) return x < y ? -1 : 1;
                i++;
                j++;
            }
        }
        return Integer.compare(la - i, lb - j);
    }
}
