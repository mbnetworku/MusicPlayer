package com.musicplayer.lite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.net.Uri;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.io.FileOutputStream;

/** The picture behind the glass: how it is stored, cropped and chosen. */
@RunWith(RobolectricTestRunner.class)
public class BackgroundTest {
    private Application app;

    @Before
    public void setUp() {
        TestMedia.resetSingletons();
        app = RuntimeEnvironment.getApplication();
        for (int kind : new int[]{BackgroundStore.KIND_PICTURE, BackgroundStore.KIND_WALLPAPER}) {
            //noinspection ResultOfMethodCallIgnored
            BackgroundStore.file(app, kind).delete();
        }
    }

    @Test
    public void thePictureAndTheWallpaperAreKeptSeparately() {
        assertFalse(BackgroundStore.file(app, BackgroundStore.KIND_PICTURE)
                .equals(BackgroundStore.file(app, BackgroundStore.KIND_WALLPAPER)));
        assertEquals(BackgroundStore.KIND_PICTURE, BackgroundStore.kindFor(Appearance.BG_PICTURE));
        assertEquals(BackgroundStore.KIND_WALLPAPER, BackgroundStore.kindFor(Appearance.BG_WALLPAPER));
        assertFalse(BackgroundStore.has(app, BackgroundStore.KIND_PICTURE));
        assertNull("nothing saved, nothing loaded", BackgroundStore.loadNow(app, BackgroundStore.KIND_PICTURE));
    }

    @Test
    public void bigPicturesAreReadAtAReducedSize() {
        assertEquals(1, BackgroundStore.sampleSize(1000, 1280));
        assertEquals(1, BackgroundStore.sampleSize(1500, 1280));
        assertEquals(2, BackgroundStore.sampleSize(2600, 1280));
        assertEquals(4, BackgroundStore.sampleSize(5200, 1280));
        assertEquals("never below what is needed", 8, BackgroundStore.sampleSize(10_240, 1280));
    }

    @Test
    public void aPictureThatCannotBeReadIsRefused() {
        assertFalse(BackgroundStore.savePicture(app, Uri.fromFile(new File("/no/such/picture.jpg"))));
        assertFalse(BackgroundStore.has(app, BackgroundStore.KIND_PICTURE));
    }

    @Test
    public void aChosenPictureIsStoredAndLoaded() throws Exception {
        File source = File.createTempFile("picked", ".png");
        Bitmap original = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888);
        try (FileOutputStream out = new FileOutputStream(source)) {
            original.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        boolean saved = BackgroundStore.savePicture(app, Uri.fromFile(source));
        assertTrue(saved);
        assertTrue(BackgroundStore.has(app, BackgroundStore.KIND_PICTURE));
        assertNotNull(BackgroundStore.loadNow(app, BackgroundStore.KIND_PICTURE));
        assertFalse("the wallpaper slot is untouched", BackgroundStore.has(app, BackgroundStore.KIND_WALLPAPER));
    }

    @Test
    public void theWallpaperColoursMakeATallGradient() {
        Bitmap g = BackgroundStore.gradient(0xFF336699, 0xFF99CC33, 0xFFCC3366);
        assertTrue("taller than wide, like a phone screen", g.getHeight() > g.getWidth());
    }

    @Test
    public void aPictureIsCroppedToTheWindowNotStretched() {
        Rect r = new Rect();
        // A wide 4:2 picture into a tall 1:2 window keeps the middle and uses the full height.
        BackdropDrawable.cropTo(400, 200, 100, 200, r);
        assertEquals(100, r.width());
        assertEquals(200, r.height());
        assertEquals("centred", 150, r.left);
        // A tall picture into a wide window keeps the middle band.
        BackdropDrawable.cropTo(200, 400, 200, 100, r);
        assertEquals(200, r.width());
        assertEquals(100, r.height());
        assertEquals("centred", 150, r.top);
        // Same shape: the whole picture.
        BackdropDrawable.cropTo(300, 600, 150, 300, r);
        assertEquals(new Rect(0, 0, 300, 600), r);
    }

    @Test
    public void thePlainPageIsTheThemeBackgroundAndAPictureIsSoftened() {
        BackdropDrawable plain = new BackdropDrawable(app);
        plain.setPlain(false);
        plain.setBounds(0, 0, 200, 400);
        plain.draw(new Canvas(Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)));
        assertNull("a plain page has no picture", plain.shown());

        Bitmap picture = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888);
        picture.eraseColor(0xFF8899AA);
        Bitmap soft = BackdropDrawable.soften(picture);
        assertEquals("the short side becomes a few dozen pixels", 72, Math.min(soft.getWidth(), soft.getHeight()));
        assertTrue("and the proportions are kept", soft.getWidth() > soft.getHeight());

        BackdropDrawable backdrop = new BackdropDrawable(app);
        backdrop.setPicture(picture, false);
        assertNotNull(backdrop.shown());
        backdrop.setPicture(null, false);
        assertNull("no picture falls back to the plain page", backdrop.shown());
    }
}
