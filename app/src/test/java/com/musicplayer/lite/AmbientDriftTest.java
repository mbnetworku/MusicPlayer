package com.musicplayer.lite;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;

/** The ambient background drifts while the screen is in use and stands still otherwise. */
@RunWith(RobolectricTestRunner.class)
public class AmbientDriftTest {
    @Test
    public void theBackdropDriftsOnlyWhileTheScreenIsResumed() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        TestMedia.install(app);
        Prefs.get(app).setGlass(true);
        Prefs.get(app).setGlassBackground(Appearance.BG_COVER);
        ActivityController<LibraryActivity> c =
                Robolectric.buildActivity(LibraryActivity.class).create().start().resume().visible();
        android.graphics.drawable.Drawable d = c.get().getWindow().getDecorView().getBackground();
        assertTrue("the window shows the ambient backdrop", d instanceof BackdropDrawable);
        assertTrue("it drifts while in use", ((BackdropDrawable) d).isDrifting());
        c.pause();
        assertFalse("it stands still when the screen is paused", ((BackdropDrawable) d).isDrifting());
        c.stop().destroy();
    }

    @Test
    public void aPlainBlackOrWhitePageHasNothingToDrift() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        TestMedia.install(app);
        Prefs.get(app).setGlass(true);
        Prefs.get(app).setGlassBackground(Appearance.BG_PLAIN);
        ActivityController<LibraryActivity> c =
                Robolectric.buildActivity(LibraryActivity.class).create().start().resume().visible();
        assertFalse(c.get().getWindow().getDecorView().getBackground() instanceof BackdropDrawable);
        c.pause().stop().destroy();
    }
}
