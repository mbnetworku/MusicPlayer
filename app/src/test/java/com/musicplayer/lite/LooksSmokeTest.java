package com.musicplayer.lite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.graphics.drawable.Drawable;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;

import java.util.ArrayList;
import java.util.List;

/** Opens every screen in every look: follow device, light and dark, each flat and as liquid glass over every background. */
@RunWith(RobolectricTestRunner.class)
public class LooksSmokeTest {

    @Test
    public void everyScreenOpensInEveryLook() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        List<Class<? extends Activity>> screens = new ArrayList<>();
        screens.add(LibraryActivity.class);
        screens.add(PlayerActivity.class);
        screens.add(QueueActivity.class);
        screens.add(EqualizerActivity.class);
        screens.add(SettingsActivity.class);

        int opened = 0;
        for (int mode : new int[]{Appearance.FOLLOW_DEVICE, Appearance.LIGHT, Appearance.DARK}) {
            // Flat, then liquid glass over each kind of background.
            for (int background = -1; background <= Appearance.BG_WALLPAPER; background++) {
                final boolean glass = background >= 0;
                TestMedia.install(app); // resets the singletons, grants the permission, fakes MediaStore
                FakePlayback.register();
                Prefs prefs = Prefs.get(app);
                prefs.setThemeMode(mode);
                prefs.setGlass(glass);
                if (glass) prefs.setGlassBackground(background);

                Library library = Library.get(app);
                library.refresh();
                TestMedia.awaitLibrary(library);
                PlaybackService service = Robolectric.buildService(PlaybackService.class).create().get();
                TestMedia.exposeService(app, service);
                service.playQueue(new ArrayList<>(library.tracks()), 1, false);
                TestMedia.idle();

                for (Class<? extends Activity> screen : screens) {
                    String look = "mode=" + mode + " glass=" + glass + " background=" + background + " "
                            + screen.getSimpleName();
                    ActivityController<? extends Activity> c =
                            Robolectric.buildActivity(screen).create().start().resume().visible();
                    TestMedia.idle();
                    Activity a = c.get();
                    assertNotNull(look, a.findViewById(android.R.id.content));

                    // Only the cover backdrop is a window background of its own: a plain page is the theme's
                    // colour, and a picture needs a saved file (there is none in this test).
                    Drawable window = a.getWindow().getDecorView().getBackground();
                    assertEquals(look, background == Appearance.BG_COVER, window instanceof BackdropDrawable);
                    if (screen == LibraryActivity.class) {
                        View tabs = a.findViewById(R.id.tabs);
                        assertEquals(look, glass, tabs.getBackground() instanceof GlassDrawable);
                    }
                    if (mode == Appearance.LIGHT) assertFalse(look, Appearance.isNight(a));
                    if (mode == Appearance.DARK) assertTrue(look, Appearance.isNight(a));
                    c.pause().stop().destroy();
                    opened++;
                }
            }
        }
        assertEquals(75, opened);
    }
}
