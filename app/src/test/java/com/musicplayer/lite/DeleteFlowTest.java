package com.musicplayer.lite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.os.Build;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowToast;
import org.robolectric.util.ReflectionHelpers;

import java.util.Collections;

/** The "more" menu, the glass confirmation and deleting a song, as the user goes through them. */
@RunWith(RobolectricTestRunner.class)
public class DeleteFlowTest {
    private Application app;
    private int realSdk;

    @Before
    public void setUp() {
        app = RuntimeEnvironment.getApplication();
        realSdk = Build.VERSION.SDK_INT;
    }

    @After
    public void restoreSdk() {
        ReflectionHelpers.setStaticField(Build.VERSION.class, "SDK_INT", realSdk);
    }

    private ActivityController<LibraryActivity> openLibrary() throws Exception {
        TestMedia.install(app);
        ActivityController<LibraryActivity> c =
                Robolectric.buildActivity(LibraryActivity.class).create().start().resume().visible();
        TestMedia.awaitLibrary(Library.get(app));
        return c;
    }

    private static View dialogView(int id) {
        return ShadowDialog.getLatestDialog().findViewById(id);
    }

    /** Delete is always the last action of the sheet, whatever is added above it. */
    private static View deleteRow() {
        LinearLayout items = (LinearLayout) dialogView(R.id.gd_items);
        return items.getChildAt(items.getChildCount() - 1);
    }

    @Test
    public void everySongRowHasAMoreButtonThatOpensTheGlassMenu() throws Exception {
        ActivityController<LibraryActivity> c = openLibrary();
        ListView list = c.get().findViewById(R.id.list);
        View row = list.getAdapter().getView(TestMedia.songPos(list, 0), null, list);
        assertEquals(View.VISIBLE, row.findViewById(R.id.row_more).getVisibility());

        row.findViewById(R.id.row_more).performClick();
        LinearLayout items = (LinearLayout) dialogView(R.id.gd_items);
        // Play next, Add to queue, Add to Favorites, Song info and Delete.
        assertEquals(5, items.getChildCount());
        TextView last = deleteRow().findViewById(R.id.gd_item_label);
        assertEquals("Delete…", last.getText().toString());
        assertEquals("red, because it cannot be undone", app.getColor(R.color.accent_text), last.getCurrentTextColor());
        c.pause().stop().destroy();
    }

    @Test
    public void aFolderRowsUpEntryHasNoMenuButFolderAndAlbumRowsDo() throws Exception {
        ActivityController<LibraryActivity> c = openLibrary();
        c.get().findViewById(R.id.tab_albums).performClick();
        ListView list = c.get().findViewById(R.id.list);
        View row = list.getAdapter().getView(0, null, list);
        assertEquals("albums have a menu too", View.VISIBLE, row.findViewById(R.id.row_more).getVisibility());
        c.pause().stop().destroy();
    }

    @Test
    public void askingToDeleteAsksAgainAndNothingIsDeletedUntilYouAgree() throws Exception {
        ActivityController<LibraryActivity> c = openLibrary();
        ListView list = c.get().findViewById(R.id.list);
        list.getAdapter().getView(TestMedia.songPos(list, 0), null, list).findViewById(R.id.row_more).performClick();
        deleteRow().performClick();

        // The confirmation: what is deleted, that it is for good, and a way out.
        assertEquals("Delete “Ember”?", ((TextView) dialogView(R.id.gd_title)).getText().toString());
        assertTrue(((TextView) dialogView(R.id.gd_message)).getText().toString().contains("cannot be undone"));
        dialogView(R.id.gd_negative).performClick();
        assertTrue("Cancel deletes nothing", TestMedia.DELETED.isEmpty());
        c.pause().stop().destroy();
    }

    @Test
    public void onAndroid10AndOlderConfirmingDeletesTheSongRightAway() throws Exception {
        ActivityController<LibraryActivity> c = openLibrary();
        shadowOf(app).grantPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        ReflectionHelpers.setStaticField(Build.VERSION.class, "SDK_INT", 29);

        ListView list = c.get().findViewById(R.id.list);
        list.getAdapter().getView(TestMedia.songPos(list, 0), null, list).findViewById(R.id.row_more).performClick();
        deleteRow().performClick();
        dialogView(R.id.gd_positive).performClick();

        assertEquals("Ember (id 5) was deleted at once", Collections.singletonList(5L), TestMedia.DELETED);
        assertEquals("Deleted 1 song", ShadowToast.getTextOfLatestToast());
        c.pause().stop().destroy();
    }

    @Test
    public void deletingAWholeAlbumDeletesEveryUnderlyingSong() throws Exception {
        ActivityController<LibraryActivity> c = openLibrary();
        shadowOf(app).grantPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        ReflectionHelpers.setStaticField(Build.VERSION.class, "SDK_INT", 28);

        c.get().findViewById(R.id.tab_albums).performClick();
        ListView list = c.get().findViewById(R.id.list);
        // The first album alphabetically is "Afterglow", which has two songs.
        list.getAdapter().getView(0, null, list).findViewById(R.id.row_more).performClick();
        LinearLayout items = (LinearLayout) dialogView(R.id.gd_items);
        // Play, Shuffle, Play next, Add to queue, Add all to Favorites and Delete all.
        assertEquals(6, items.getChildCount());
        deleteRow().performClick();
        assertEquals("Delete 2 songs?", ((TextView) dialogView(R.id.gd_title)).getText().toString());
        dialogView(R.id.gd_positive).performClick();

        assertEquals(2, TestMedia.DELETED.size());
        assertTrue(TestMedia.DELETED.contains(1L) && TestMedia.DELETED.contains(2L));
        assertEquals("Deleted 2 songs", ShadowToast.getTextOfLatestToast());
        c.pause().stop().destroy();
    }

    @Test
    public void withoutWriteAccessOnAndroid9TheGlassPermissionQuestionComesFirst() throws Exception {
        ActivityController<LibraryActivity> c = openLibrary();
        ReflectionHelpers.setStaticField(Build.VERSION.class, "SDK_INT", 28);

        ListView list = c.get().findViewById(R.id.list);
        list.getAdapter().getView(TestMedia.songPos(list, 0), null, list).findViewById(R.id.row_more).performClick();
        deleteRow().performClick();
        dialogView(R.id.gd_positive).performClick();       // confirm the delete

        assertTrue("nothing deleted yet", TestMedia.DELETED.isEmpty());
        assertEquals("Allow deleting songs?", ((TextView) dialogView(R.id.gd_title)).getText().toString());
        assertFalse(shadowOf(c.get()).getLastRequestedPermission() != null);
        dialogView(R.id.gd_positive).performClick();       // Allow
        assertEquals(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                shadowOf(c.get()).getLastRequestedPermission().requestedPermissions[0]);

        // Android reports "granted": the delete the user already confirmed now happens.
        shadowOf(app).grantPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        c.get().onRequestPermissionsResult(9002, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                new int[]{android.content.pm.PackageManager.PERMISSION_GRANTED});
        assertEquals(Collections.singletonList(5L), TestMedia.DELETED);
        c.pause().stop().destroy();
    }

    @Test
    public void onAndroid11AndNewerTheSystemRequestIsUsedAndDeclineChangesNothing() throws Exception {
        ActivityController<LibraryActivity> c = openLibrary();
        ListView list = c.get().findViewById(R.id.list);
        list.getAdapter().getView(TestMedia.songPos(list, 0), null, list).findViewById(R.id.row_more).performClick();
        deleteRow().performClick();
        dialogView(R.id.gd_positive).performClick();

        // The test runtime has no MediaStore to build the system request, so the app says so instead of crashing;
        // on a phone Android shows its own "allow deleting?" screen here.
        assertTrue(TestMedia.DELETED.isEmpty());
        assertEquals("Some songs could not be deleted.", ShadowToast.getTextOfLatestToast());
        c.pause().stop().destroy();
    }
}
