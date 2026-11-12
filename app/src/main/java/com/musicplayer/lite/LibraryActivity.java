package com.musicplayer.lite;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The home screen: Songs, Folders, Albums and Artists, with search and a mini player.
 * On car head units and tablets in landscape it also hosts the player on the left.
 */
public class LibraryActivity extends BoundActivity
        implements Library.Listener, RowAdapter.HeaderListener, Updater.Listener {

    private static final int TAB_SONGS = 0;
    private static final int TAB_FOLDERS = 1;
    private static final int TAB_ALBUMS = 2;
    private static final int TAB_ARTISTS = 3;
    private static final int TAB_FAVORITES = 4;
    private static final int REQ_PERMISSIONS = 1;
    private static final String SEP = " · ";
    private static final int[] TAB_TITLES = {R.string.tab_songs, R.string.tab_folders, R.string.tab_albums,
            R.string.tab_artists, R.string.tab_favorites};
    private static final String STATE_TAB = "tab";
    private static final String STATE_FOLDER = "folder";
    private static final String STATE_GROUP = "group";
    private static final String STATE_QUERY = "query";

    private interface ServiceAction {
        void run(PlaybackService s);
    }

    /** The tag of the header above the favorites: the songs that Play and Shuffle start. */
    private static final class TrackList {
        final List<Track> tracks;

        TrackList(List<Track> tracks) {
            this.tracks = tracks;
        }
    }

    private Library library;
    private Prefs prefs;
    private RowAdapter adapter;
    private ListView list;
    private View emptyView;
    private TextView emptyTitle;
    private TextView emptyBody;
    private TextView emptyAction;
    private ImageView emptyIcon;
    private EqBarsView eqBars;
    private TextView subtitle;
    private View searchBar;
    private EditText searchInput;
    private final View[] tabViews = new View[5];
    private TextView titleView;
    private MiniPlayerView mini;
    private NowPlayingView panel;
    private View updateBanner;
    private TextView updateBannerText;
    private Updater updater;

    private int tab;
    /** Null means the top of the folder tree. */
    private FolderTree.Node folder;
    private Library.Group openGroup;
    private String query = "";
    /** The tracks a tap on a song row plays from. */
    private List<Track> context = Collections.emptyList();
    private String restoreFolder;
    private String restoreGroup;
    private boolean permissionDenied;
    private boolean scanning;
    private ServiceAction pending;

    // ---- lifecycle ------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_library);
        prepareWindow();
        library = Library.get(this);
        prefs = Prefs.get(this);

        list = findViewById(R.id.list);
        emptyView = findViewById(R.id.empty);
        emptyTitle = findViewById(R.id.empty_title);
        emptyBody = findViewById(R.id.empty_body);
        emptyAction = findViewById(R.id.empty_action);
        emptyIcon = findViewById(R.id.empty_icon);
        eqBars = findViewById(R.id.btn_eq);
        subtitle = findViewById(R.id.subtitle);
        titleView = findViewById(R.id.title);
        searchBar = findViewById(R.id.search_bar);
        searchInput = findViewById(R.id.search_input);
        mini = findViewById(R.id.mini);
        panel = findViewById(R.id.np);
        if (panel != null) {
            panel.setMoreListener(t -> showSongMenu(t.title, Collections.singletonList(t), true, true));
        }
        updater = Updater.get(this);
        updateBanner = findViewById(R.id.update_banner);
        updateBannerText = findViewById(R.id.update_banner_text);
        findViewById(R.id.update_banner_action).setOnClickListener(
                v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.update_banner_close).setOnClickListener(v -> {
            Release newer = updater.newerRelease();
            if (newer != null) prefs.dismissUpdate(newer.version);
            refreshBanner();
        });
        tabViews[TAB_SONGS] = findViewById(R.id.tab_songs);
        tabViews[TAB_FOLDERS] = findViewById(R.id.tab_folders);
        tabViews[TAB_ALBUMS] = findViewById(R.id.tab_albums);
        tabViews[TAB_ARTISTS] = findViewById(R.id.tab_artists);
        tabViews[TAB_FAVORITES] = findViewById(R.id.tab_favorites);

        adapter = new RowAdapter(this, this);
        adapter.setMoreListener((row, anchor) -> onRowLongClick(row));
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, position, id) -> onRowClick(adapter.getItem(position)));
        list.setOnItemLongClickListener((p, v, position, id) -> onRowLongClick(adapter.getItem(position)));

        for (int i = 0; i < tabViews.length; i++) {
            final int t = i;
            tabViews[i].setOnClickListener(v -> selectTab(t));
        }
        findViewById(R.id.btn_search).setOnClickListener(v -> toggleSearch());
        findViewById(R.id.search_close).setOnClickListener(v -> closeSearch());
        findViewById(R.id.btn_eq).setOnClickListener(v -> startActivity(new Intent(this, EqualizerActivity.class)));
        findViewById(R.id.btn_more).setOnClickListener(this::showMenu);
        View queueButton = findViewById(R.id.btn_queue);
        if (queueButton != null) {
            queueButton.setOnClickListener(v -> startActivity(new Intent(this, QueueActivity.class)));
        }
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s.toString();
                rebuild();
                list.setSelection(0);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        if (state != null) {
            tab = state.getInt(STATE_TAB, 0);
            restoreFolder = state.getString(STATE_FOLDER);
            restoreGroup = state.getString(STATE_GROUP);
            query = state.getString(STATE_QUERY, "");
            if (!query.isEmpty()) {
                searchBar.setVisibility(View.VISIBLE);
            }
        } else {
            tab = Fmt.clamp(prefs.lastTab(), 0, tabViews.length - 1);
        }

        library.addListener(this);
        if (Library.hasPermission(this)) {
            library.ensureLoaded();
        } else if (state == null) {
            requestPermission();
        }
        rebuild();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Back from the system settings screen with the permission granted, or a new USB stick.
        if (Library.hasPermission(this)) {
            permissionDenied = false;
            library.ensureLoaded();
        }
        rebuild();
    }

    @Override
    protected void onStart() {
        super.onStart();
        updater.addListener(this);
        updater.autoCheckIfDue();
        refreshBanner();
    }

    @Override
    protected void onStop() {
        updater.removeListener(this);
        super.onStop();
    }

    @Override
    public void onUpdaterChanged() {
        refreshBanner();
    }

    /** Shows "Version X is available" when a newer version is known and has not been dismissed. */
    private void refreshBanner() {
        Release newer = updater.newerRelease();
        boolean show = newer != null && !newer.version.equals(prefs.dismissedUpdate());
        updateBanner.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) updateBannerText.setText(getString(R.string.upd_banner_text, newer.version));
    }

    @Override
    protected void onDestroy() {
        library.removeListener(this);
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(STATE_TAB, tab);
        out.putString(STATE_QUERY, query);
        if (folder != null) out.putString(STATE_FOLDER, folder.path);
        if (openGroup != null) out.putString(STATE_GROUP, openGroup.key);
    }

    @Override
    protected void onServiceReady() {
        if (mini != null) mini.bind(service);
        if (panel != null) panel.bind(service);
        if (pending != null) {
            ServiceAction a = pending;
            pending = null;
            a.run(service);
        }
    }

    @Override
    protected void onPlaybackUpdated() {
        if (service == null) return;
        Track t = service.currentTrack();
        adapter.setCurrentTrack(t == null ? -1 : t.id);
        adapter.setPlaying(service.isPlaying());
        eqBars.setPlaying(service.isPlaying());
        if (panel != null && service.isPlaying()) offerSoundBars();   // the car layout has the big bars here
        if (mini != null) mini.refresh();
        if (panel != null) panel.refresh();
    }

    @Override
    protected void onTick() {
        if (mini != null) mini.tick();
        if (panel != null) panel.tick();
    }

    @Override
    protected void onFavoritesChanged() {
        rebuild();
    }

    @Override
    public void onLibraryChanged() {
        if (folder != null) folder = FolderTree.find(library.folderRoot(), folder.path);
        if (openGroup != null) openGroup = library.findGroup(tab == TAB_ALBUMS, openGroup.key);
        rebuild();
    }

    @Override
    public void onBackPressed() {
        if (searchBar.getVisibility() == View.VISIBLE) {
            closeSearch();
            return;
        }
        if (tab == TAB_FOLDERS && FolderTree.parentInView(currentFolder(), visibleRoot()) != null) {
            goUp();
            return;
        }
        if ((tab == TAB_ALBUMS || tab == TAB_ARTISTS) && openGroup != null) {
            openGroup = null;
            rebuild();
            return;
        }
        super.onBackPressed();
    }

    // ---- permissions -------------------------------------------------------------------------

    private void requestPermission() {
        Permissions.askForMusic(this, REQ_PERMISSIONS, this::rebuild);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQ_PERMISSIONS) return;
        if (Library.hasPermission(this)) {
            permissionDenied = false;
            library.refresh();
        } else {
            permissionDenied = !shouldShowRequestPermissionRationale(Library.audioPermission());
        }
        rebuild();
    }

    private void openAppSettings() {
        Permissions.askForSettings(this, R.drawable.ic_music_note, R.string.perm_music_off_title,
                R.string.perm_music_off_body, null);
    }

    // ---- building the list ----------------------------------------------------------------------

    private FolderTree.Node visibleRoot() {
        return FolderTree.visibleRoot(library.folderRoot());
    }

    private FolderTree.Node currentFolder() {
        return folder != null ? folder : visibleRoot();
    }

    private String plural(int res, int n) {
        return getResources().getQuantityString(res, n, n);
    }

    private void rebuild() {
        if (library.isLoaded()) {
            if (restoreFolder != null) {
                folder = FolderTree.find(library.folderRoot(), restoreFolder);
                restoreFolder = null;
            }
            if (restoreGroup != null) {
                openGroup = library.findGroup(tab == TAB_ALBUMS, restoreGroup);
                restoreGroup = null;
            }
        }
        final boolean searching = !query.trim().isEmpty();
        final int shown = searching ? TAB_SONGS : tab;
        final List<RowAdapter.Row> rows = new ArrayList<>();
        boolean indexed = false;
        switch (shown) {
            case TAB_FOLDERS:
                addFolderRows(rows);
                break;
            case TAB_ALBUMS:
                addGroupRows(rows, true);
                break;
            case TAB_ARTISTS:
                addGroupRows(rows, false);
                break;
            case TAB_FAVORITES:
                addFavoriteRows(rows);
                break;
            default: {
                List<Track> src = searching ? library.search(query) : library.tracks();
                context = src;
                for (int i = 0; i < src.size(); i++) {
                    rows.add(RowAdapter.Row.track(src.get(i), RowAdapter.SUB_ARTIST_ALBUM, i));
                }
                indexed = !searching;
                break;
            }
        }
        adapter.setRows(rows, indexed);
        list.setFastScrollEnabled(rows.size() > 40);
        for (int i = 0; i < tabViews.length; i++) tabViews[i].setSelected(i == shown);
        titleView.setText(searching ? R.string.search_title : TAB_TITLES[shown]);
        updateSubtitle(shown);
        updateEmpty(searching, shown, rows.isEmpty());
        if (service != null) adapter.setCurrentTrack(currentId());
    }

    private long currentId() {
        Track t = service == null ? null : service.currentTrack();
        return t == null ? -1 : t.id;
    }

    private void addFolderRows(List<RowAdapter.Row> rows) {
        final FolderTree.Node vroot = visibleRoot();
        final FolderTree.Node node = currentFolder();
        context = node.tracks;
        if (node.total > 0) {
            String sub = plural(R.plurals.songs_count, node.total);
            if (!node.children.isEmpty()) sub += SEP + plural(R.plurals.folders_count, node.children.size());
            String crumb = node == vroot && node.tracks.isEmpty()
                    ? getString(R.string.all_folders) : FolderTree.breadcrumb(node);
            rows.add(RowAdapter.Row.header(crumb, "", sub, true, node));
        }
        FolderTree.Node up = FolderTree.parentInView(node, vroot);
        if (up != null) {
            String where = up == vroot && up.tracks.isEmpty()
                    ? getString(R.string.all_folders) : FolderTree.breadcrumb(up);
            rows.add(RowAdapter.Row.folder(getString(R.string.folder_up), where, R.drawable.ic_arrow_upward, null));
        }
        for (FolderTree.Entry e : FolderTree.entries(node)) {
            rows.add(RowAdapter.Row.folder(e.label, plural(R.plurals.songs_count, e.node.total),
                    R.drawable.ic_folder, e));
        }
        for (int i = 0; i < node.tracks.size(); i++) {
            rows.add(RowAdapter.Row.track(node.tracks.get(i), RowAdapter.SUB_ARTIST_ALBUM, i));
        }
    }

    private void addGroupRows(List<RowAdapter.Row> rows, boolean albums) {
        if (openGroup == null) {
            context = Collections.emptyList();
            for (Library.Group g : albums ? library.albums() : library.artists()) {
                rows.add(RowAdapter.Row.group(g.name, g.subtitle, albums ? g.artTrack() : null,
                        albums ? R.drawable.ic_album : R.drawable.ic_person, g));
            }
        } else {
            context = openGroup.tracks;
            rows.add(RowAdapter.Row.header(getString(albums ? R.string.label_album : R.string.label_artist),
                    openGroup.name, openGroup.subtitle, true, openGroup));
            for (int i = 0; i < openGroup.tracks.size(); i++) {
                rows.add(RowAdapter.Row.track(openGroup.tracks.get(i),
                        albums ? RowAdapter.SUB_ARTIST : RowAdapter.SUB_ALBUM, i));
            }
        }
    }

    /** The favorites, newest first, under a header with Play and Shuffle. */
    private void addFavoriteRows(List<RowAdapter.Row> rows) {
        final List<Track> tracks = Favorites.get(this).tracks(library);
        context = tracks;
        if (tracks.isEmpty()) return;
        rows.add(RowAdapter.Row.header(null, "", plural(R.plurals.songs_count, tracks.size()), true,
                new TrackList(tracks)));
        for (int i = 0; i < tracks.size(); i++) {
            rows.add(RowAdapter.Row.track(tracks.get(i), RowAdapter.SUB_ARTIST_ALBUM, i));
        }
    }

    private void updateSubtitle(int shown) {
        if (shown == TAB_FAVORITES) {
            subtitle.setText("");   // the header row above the list says how many songs there are
            return;
        }
        int songs = library.tracks().size();
        if (songs == 0) {
            subtitle.setText("");
            return;
        }
        subtitle.setText(getString(R.string.pair, plural(R.plurals.songs_count, songs),
                plural(R.plurals.folders_count, library.folderCount())));
    }

    private void updateEmpty(boolean searching, int shown, boolean rowsEmpty) {
        final boolean permitted = Library.hasPermission(this);
        boolean show = true;
        emptyAction.setVisibility(View.VISIBLE);
        emptyIcon.setImageResource(R.drawable.ic_music_note);
        if (!permitted) {
            emptyTitle.setText(R.string.empty_permission_title);
            emptyBody.setText(R.string.empty_permission_body);
            emptyAction.setText(permissionDenied ? R.string.empty_permission_settings : R.string.empty_permission_action);
            emptyAction.setOnClickListener(v -> {
                if (permissionDenied) openAppSettings();
                else requestPermission();
            });
        } else if (library.isLoaded() && library.tracks().isEmpty()) {
            emptyTitle.setText(R.string.empty_none_title);
            emptyBody.setText(R.string.empty_none_body);
            emptyAction.setText(R.string.empty_none_action);
            emptyAction.setOnClickListener(v -> startScan());
        } else if (rowsEmpty && searching) {
            emptyTitle.setText(R.string.empty_search_title);
            emptyBody.setText(R.string.empty_search_body);
            emptyAction.setVisibility(View.GONE);
        } else if (rowsEmpty && shown == TAB_FAVORITES && library.isLoaded()) {
            emptyIcon.setImageResource(R.drawable.ic_favorite_border);
            emptyTitle.setText(R.string.empty_fav_title);
            emptyBody.setText(R.string.empty_fav_body);
            emptyAction.setVisibility(View.GONE);
        } else {
            show = false;
        }
        emptyView.setVisibility(show ? View.VISIBLE : View.GONE);
        list.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    // ---- navigation ---------------------------------------------------------------------------------

    private void selectTab(int t) {
        if (!query.isEmpty()) {
            searchInput.setText("");   // triggers a rebuild through the text watcher
            hideSearchBar();
        }
        tab = t;
        openGroup = null;
        prefs.setLastTab(t);
        rebuild();
        list.setSelection(0);
    }

    private void openFolder(FolderTree.Node node) {
        folder = node;
        rebuild();
        list.setSelection(0);
    }

    private void goUp() {
        FolderTree.Node up = FolderTree.parentInView(currentFolder(), visibleRoot());
        if (up == null) return;
        folder = up;
        rebuild();
        list.setSelection(0);
    }

    private void toggleSearch() {
        if (searchBar.getVisibility() == View.VISIBLE) {
            closeSearch();
            return;
        }
        searchBar.setVisibility(View.VISIBLE);
        searchInput.requestFocus();
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
    }

    private void closeSearch() {
        searchInput.setText("");
        hideSearchBar();
    }

    private void hideSearchBar() {
        searchBar.setVisibility(View.GONE);
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
    }

    // ---- row actions ---------------------------------------------------------------------------------

    private void onRowClick(RowAdapter.Row r) {
        switch (r.type) {
            case RowAdapter.TRACK:
                play(context, r.index, false);
                break;
            case RowAdapter.FOLDER:
                if (r.tag instanceof FolderTree.Entry) openFolder(((FolderTree.Entry) r.tag).node);
                else goUp();
                break;
            case RowAdapter.GROUP:
                openGroup = (Library.Group) r.tag;
                rebuild();
                list.setSelection(0);
                break;
            default:
                break;
        }
    }

    @Override
    public void onHeaderAction(RowAdapter.Row r, boolean shuffle) {
        List<Track> tracks = tracksOf(r);
        if (tracks != null) play(tracks, 0, shuffle);
    }

    private static List<Track> tracksOf(RowAdapter.Row r) {
        if (r.type == RowAdapter.TRACK) return Collections.singletonList(r.track);
        if (r.tag instanceof FolderTree.Node) return FolderTree.flatten((FolderTree.Node) r.tag);
        if (r.tag instanceof FolderTree.Entry) return FolderTree.flatten(((FolderTree.Entry) r.tag).node);
        if (r.tag instanceof Library.Group) return ((Library.Group) r.tag).tracks;
        if (r.tag instanceof TrackList) return ((TrackList) r.tag).tracks;
        return null;
    }

    private boolean onRowLongClick(final RowAdapter.Row r) {
        final List<Track> tracks = tracksOf(r);
        if (tracks == null || tracks.isEmpty()) return false;
        showSongMenu(r.title, tracks, r.type == RowAdapter.TRACK, false);
        return true;
    }

    private void play(final List<Track> tracks, final int index, final boolean shuffle) {
        if (tracks.isEmpty()) return;
        final int start = shuffle ? new Random().nextInt(tracks.size()) : index;
        withService(s -> s.playQueue(tracks, start, shuffle));
    }

    private void withService(ServiceAction a) {
        if (service != null) a.run(service);
        else pending = a;
    }

    // ---- overflow menu and scan ------------------------------------------------------------------

    private void showMenu(View anchor) {
        GlassDialog.sheet(this)
                .item(R.drawable.ic_settings, R.string.menu_settings,
                        () -> startActivity(new Intent(this, SettingsActivity.class)))
                .item(R.drawable.ic_refresh, R.string.menu_scan, this::startScan)
                .show();
    }

    private void startScan() {
        if (scanning) return;
        if (!Library.hasPermission(this)) {
            requestPermission();
            return;
        }
        scanning = true;
        toast(R.string.scanning);
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
}
