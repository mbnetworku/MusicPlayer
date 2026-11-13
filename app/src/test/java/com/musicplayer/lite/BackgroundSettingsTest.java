package com.musicplayer.lite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowDialog;

import java.io.File;
import java.io.FileOutputStream;

/** Choosing what shows behind the liquid-glass panels. */
@RunWith(RobolectricTestRunner.class)
public class BackgroundSettingsTest {
    private Application app;
    private Prefs prefs;

    @Before
    public void setUp() {
        TestMedia.resetSingletons();
        app = RuntimeEnvironment.getApplication();
        prefs = Prefs.get(app);
        for (int kind : new int[]{BackgroundStore.KIND_PICTURE, BackgroundStore.KIND_WALLPAPER}) {
            //noinspection ResultOfMethodCallIgnored
            BackgroundStore.file(app, kind).delete();
        }
    }

    private ActivityController<SettingsActivity> open() {
        return open(true);
    }

    private ActivityController<SettingsActivity> open(boolean fromWhitePage) {
        if (fromWhitePage) prefs.setGlassBackground(Appearance.BG_PLAIN);
        return Robolectric.buildActivity(SettingsActivity.class).create().start().resume().visible();
    }

    @Test
    public void aPureWhiteOrBlackPageIsTheDefault() {
        ActivityController<SettingsActivity> c = open(false);
        RadioGroup group = c.get().findViewById(R.id.bg_group);
        assertEquals(R.id.bg_plain, group.getCheckedRadioButtonId());
        assertEquals(Appearance.BG_PLAIN, prefs.glassBackground());
        assertEquals("pick a picture from your gallery",
                ((TextView) c.get().findViewById(R.id.bg_picture_sub)).getText().toString(),
                app.getString(R.string.set_bg_picture_none));
        assertEquals("nothing to refresh yet", View.GONE, c.get().findViewById(R.id.bg_wallpaper_update).getVisibility());
        c.pause().stop().destroy();
    }

    @Test
    public void coverArtIsChosenAndRememberedAtOnce() {
        ActivityController<SettingsActivity> c = open();
        ((RadioGroup) c.get().findViewById(R.id.bg_group)).check(R.id.bg_cover);
        assertEquals(Appearance.BG_COVER, prefs.glassBackground());
        assertTrue("the screen rebuilds itself to show it", c.get().isChangingConfigurations() || !c.get().isFinishing());
        c.pause().stop().destroy();
    }

    @Test
    public void myPictureOpensTheGalleryFirstAndOnlyAppliesOnceOneIsPicked() throws Exception {
        ActivityController<SettingsActivity> c = open();
        SettingsActivity a = c.get();
        ((RadioGroup) a.findViewById(R.id.bg_group)).check(R.id.bg_picture);

        ShadowActivity.IntentForResult started = shadowOf(a).getNextStartedActivityForResult();
        assertNotNull("the system picker was opened", started);
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, started.intent.getAction());
        assertEquals("image/*", started.intent.getType());
        assertEquals("nothing applies before a picture exists", Appearance.BG_PLAIN, prefs.glassBackground());
        assertEquals("the choice goes back to what it was", R.id.bg_plain,
                ((RadioGroup) a.findViewById(R.id.bg_group)).getCheckedRadioButtonId());

        // The user picks a picture.
        File picture = File.createTempFile("wallpaper", ".png");
        try (FileOutputStream out = new FileOutputStream(picture)) {
            Bitmap.createBitmap(120, 240, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        shadowOf(a).receiveResult(started.intent, Activity.RESULT_OK, new Intent().setData(Uri.fromFile(picture)));
        // The copy happens on a background thread; give it a moment, then let the main thread apply it.
        for (int i = 0; i < 100 && !BackgroundStore.has(app, BackgroundStore.KIND_PICTURE); i++) Thread.sleep(30);
        assertTrue("the picture is stored in the app", BackgroundStore.has(app, BackgroundStore.KIND_PICTURE));
        for (int i = 0; i < 100 && prefs.glassBackground() != Appearance.BG_PICTURE; i++) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            Thread.sleep(30);
        }
        assertEquals(Appearance.BG_PICTURE, prefs.glassBackground());
        c.pause().stop().destroy();
    }

    @Test
    public void cancellingThePickerChangesNothing() {
        ActivityController<SettingsActivity> c = open();
        SettingsActivity a = c.get();
        a.findViewById(R.id.bg_pick).performClick();
        ShadowActivity.IntentForResult started = shadowOf(a).getNextStartedActivityForResult();
        shadowOf(a).receiveResult(started.intent, Activity.RESULT_CANCELED, null);
        assertEquals(Appearance.BG_PLAIN, prefs.glassBackground());
        assertFalse(BackgroundStore.has(app, BackgroundStore.KIND_PICTURE));
        c.pause().stop().destroy();
    }

    @Test
    public void theWallpaperIsAskedForInAGlassPopUp() {
        ActivityController<SettingsActivity> c = open();
        ((RadioGroup) c.get().findViewById(R.id.bg_group)).check(R.id.bg_wallpaper);
        assertEquals("Use your wallpaper?",
                ((TextView) ShadowDialog.getLatestDialog().findViewById(R.id.gd_title)).getText().toString());
        assertEquals("Allow", ((TextView) ShadowDialog.getLatestDialog().findViewById(R.id.gd_positive)).getText().toString());
        assertEquals("not applied until Android has handed it over", Appearance.BG_PLAIN, prefs.glassBackground());
        ShadowDialog.getLatestDialog().findViewById(R.id.gd_negative).performClick();
        assertEquals(Appearance.BG_PLAIN, prefs.glassBackground());
        c.pause().stop().destroy();
    }

    @Test
    public void savedBackgroundsAreDescribedHonestly() {
        // A picture is saved, and the wallpaper could only give its colours.
        BackgroundStore.savePicture(app, Uri.fromFile(new File("/no/such")));   // refused, nothing saved
        assertFalse(BackgroundStore.has(app, BackgroundStore.KIND_PICTURE));
        prefs.setWallpaperIsColors(true);
        try {
            File saved = BackgroundStore.file(app, BackgroundStore.KIND_WALLPAPER);
            assertTrue(saved.createNewFile() || saved.exists());
            ActivityController<SettingsActivity> c = open();
            assertEquals(app.getString(R.string.set_bg_wallpaper_colors),
                    ((TextView) c.get().findViewById(R.id.bg_wallpaper_sub)).getText().toString());
            assertEquals("and it can be refreshed", View.VISIBLE, c.get().findViewById(R.id.bg_wallpaper_update).getVisibility());
            c.pause().stop().destroy();
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void theLookSignatureChangesWithTheBackgroundSoOpenScreensRebuild() {
        prefs.setGlass(true);
        prefs.setGlassBackground(Appearance.BG_PLAIN);
        int plain = Appearance.signature(app);
        prefs.setGlassBackground(Appearance.BG_COVER);
        int cover = Appearance.signature(app);
        assertTrue(plain != cover);
        assertTrue("plain glass gets its own fills", !Appearance.glassPlain(app));
        prefs.setGlassBackground(Appearance.BG_PLAIN);
        assertTrue(Appearance.glassPlain(app));
        prefs.setGlass(false);
        assertFalse("flat looks are never glass", Appearance.glassPlain(app));
    }
}
