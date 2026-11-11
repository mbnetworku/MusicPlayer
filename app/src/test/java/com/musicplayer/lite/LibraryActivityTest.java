package com.musicplayer.lite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;

@RunWith(RobolectricTestRunner.class)
public class LibraryActivityTest {

    private static ActivityController<LibraryActivity> launch() {
        TestMedia.resetSingletons();
        return Robolectric.buildActivity(LibraryActivity.class).create().start().resume().visible();
    }

    @Test
    public void withoutPermissionItAsksForAccess() {
        ActivityController<LibraryActivity> c = launch();
        LibraryActivity a = c.get();
        assertEquals(View.VISIBLE, a.findViewById(R.id.empty).getVisibility());
        assertEquals(a.getString(R.string.empty_permission_title),
                ((TextView) a.findViewById(R.id.empty_title)).getText().toString());
        assertEquals(View.GONE, a.findViewById(R.id.list).getVisibility());
        c.pause().stop().destroy();
    }

    @Test
    public void showsSongsFolderTreeAlbumsAndArtists() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        TestMedia.install(app);
        ActivityController<LibraryActivity> c = launch();
        LibraryActivity a = c.get();
        TestMedia.awaitLibrary(Library.get(app));

        ListView list = a.findViewById(R.id.list);
        assertEquals(View.VISIBLE, list.getVisibility());
        assertEquals(View.GONE, a.findViewById(R.id.empty).getVisibility());
        assertEquals("all five songs are listed", 5, list.getAdapter().getCount());
        assertEquals("3 folders on internal storage + 1 on the SD card = 4",
                "5 songs · 4 folders", ((TextView) a.findViewById(R.id.subtitle)).getText().toString());
        RowAdapter.Row first = (RowAdapter.Row) list.getAdapter().getItem(0);
        assertEquals("sorted by title", "Ember", first.title);

        // Folders tab: a header, then the two storage volumes. Internal storage has only a
        // "Music" folder, so the single-child chain collapses into one row.
        a.findViewById(R.id.tab_folders).performClick();
        RowAdapter.Row header = (RowAdapter.Row) list.getAdapter().getItem(0);
        assertEquals(RowAdapter.HEADER, header.type);
        assertEquals(a.getString(R.string.all_folders), header.path);
        int internal = indexOfTitle(list, "Internal storage \u203A Music");
        assertTrue("internal storage appears as one collapsed row", internal > 0);
        assertTrue(indexOfTitle(list, "SD card or USB (1234-ABCD) \u203A Songs") > 0);

        // Open it: header, an Up row, then the three album folders.
        list.performItemClick(null, internal, 0);
        assertEquals("Internal storage \u203A Music", ((RowAdapter.Row) list.getAdapter().getItem(0)).path);
        assertEquals(a.getString(R.string.folder_up), ((RowAdapter.Row) list.getAdapter().getItem(1)).title);
        assertEquals(dump(list), 5, list.getAdapter().getCount());
        assertEquals("Afterglow", ((RowAdapter.Row) list.getAdapter().getItem(2)).title);

        // Open "Afterglow": its two songs, and Back returns to "Music".
        list.performItemClick(null, 2, 0);
        assertEquals("Internal storage \u203A Music \u203A Afterglow",
                ((RowAdapter.Row) list.getAdapter().getItem(0)).path);
        assertEquals(1 + 1 + 2, list.getAdapter().getCount());
        a.onBackPressed();
        assertEquals("Internal storage \u203A Music", ((RowAdapter.Row) list.getAdapter().getItem(0)).path);
        a.onBackPressed();
        assertEquals(a.getString(R.string.all_folders), ((RowAdapter.Row) list.getAdapter().getItem(0)).path);

        // Albums and Artists tabs.
        a.findViewById(R.id.tab_albums).performClick();
        assertEquals(4, list.getAdapter().getCount());
        a.findViewById(R.id.tab_artists).performClick();
        assertEquals(4, list.getAdapter().getCount());

        // Search narrows the song list (searching always looks through songs).
        a.findViewById(R.id.tab_songs).performClick();
        a.findViewById(R.id.btn_search).performClick();
        ((android.widget.EditText) a.findViewById(R.id.search_input)).setText("glass");
        assertEquals(1, list.getAdapter().getCount());
        a.findViewById(R.id.search_close).performClick();
        assertEquals(5, list.getAdapter().getCount());
        c.pause().stop().destroy();
    }

    private static String dump(ListView list) {
        StringBuilder sb = new StringBuilder("rows:");
        for (int i = 0; i < list.getAdapter().getCount(); i++) {
            RowAdapter.Row r = (RowAdapter.Row) list.getAdapter().getItem(i);
            sb.append(" [").append(r.type).append(':').append(r.title.isEmpty() ? r.path : r.title).append(']');
        }
        return sb.toString();
    }

    private static int indexOfTitle(ListView list, String title) {
        for (int i = 0; i < list.getAdapter().getCount(); i++) {
            if (title.equals(((RowAdapter.Row) list.getAdapter().getItem(i)).title)) return i;
        }
        return -1;
    }

    @Test
    public void showsAnUpdateBannerThatCanBeDismissed() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        TestMedia.resetSingletons();
        String json = "{\"tag_name\":\"v9.9.9\",\"assets\":[{\"name\":\"a.apk\","
                + "\"browser_download_url\":\"https://example.org/a.apk\",\"size\":10}]}";
        Prefs.get(app).saveUpdateCheck(System.currentTimeMillis(), json);
        Updater.replaceForTests(new Updater(app, new Updater.Config("http://127.0.0.1:1", "o/r", true,
                java.nio.file.Files.createTempDirectory("banner").toFile(), "1.1.0", (c, f, v) -> 0, (c, f) -> { })));

        ActivityController<LibraryActivity> c =
                Robolectric.buildActivity(LibraryActivity.class).create().start().resume().visible();
        LibraryActivity a = c.get();
        View banner = a.findViewById(R.id.update_banner);
        assertEquals(View.VISIBLE, banner.getVisibility());
        assertTrue(((TextView) a.findViewById(R.id.update_banner_text)).getText().toString().contains("9.9.9"));

        a.findViewById(R.id.update_banner_close).performClick();
        assertEquals(View.GONE, banner.getVisibility());
        assertEquals("9.9.9", Prefs.get(app).dismissedUpdate());
        c.pause().stop().destroy();
    }

    @Test
    public void noBannerWhenThereIsNothingNewer() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        TestMedia.resetSingletons();
        Updater.replaceForTests(new Updater(app, new Updater.Config("http://127.0.0.1:1", "o/r", true,
                java.nio.file.Files.createTempDirectory("nobanner").toFile(), "1.1.0", (c, f, v) -> 0, (c, f) -> { })));
        ActivityController<LibraryActivity> c =
                Robolectric.buildActivity(LibraryActivity.class).create().start().resume().visible();
        assertEquals(View.GONE, c.get().findViewById(R.id.update_banner).getVisibility());
        c.pause().stop().destroy();
    }

    @Test
    public void menuOpensSettings() {
        ActivityController<LibraryActivity> c = launch();
        LibraryActivity a = c.get();
        a.findViewById(R.id.btn_more).performClick();
        c.pause().stop().destroy();
    }

    @Test
    public void tappingASongStartsPlaybackAndShowsTheMiniPlayer() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        TestMedia.install(app);
        PlaybackService service = Robolectric.buildService(PlaybackService.class).create().get();
        TestMedia.exposeService(app, service);
        FakePlayback.register();

        ActivityController<LibraryActivity> c = launch();
        LibraryActivity a = c.get();
        TestMedia.awaitLibrary(Library.get(app));
        ListView list = a.findViewById(R.id.list);

        list.performItemClick(null, 0, 0);
        TestMedia.idle();

        assertNotNull(service.currentTrack());
        assertEquals("Ember", service.currentTrack().title);
        assertTrue(service.isPlaying());
        MiniPlayerView mini = a.findViewById(R.id.mini);
        assertEquals(View.VISIBLE, mini.getVisibility());
        c.pause().stop().destroy();
    }
}
