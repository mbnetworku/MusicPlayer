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

/** Opens every screen in every look: follow device, light and dark, each flat and as liquid glass. */
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
            for (boolean glass : new boolean[]{false, true}) {
                TestMedia.install(app); // resets the singletons, grants the permission, fakes MediaStore
                FakePlayback.register();
                Prefs prefs = Prefs.get(app);
                prefs.setThemeMode(mode);
                prefs.setGlass(glass);

                Library library = Library.get(app);
                library.refresh();
                TestMedia.awaitLibrary(library);
                PlaybackService service = Robolectric.buildService(PlaybackService.class).create().get();
                TestMedia.exposeService(app, service);
                service.playQueue(new ArrayList<>(library.tracks()), 1, false);
                TestMedia.idle();

                for (Class<? extends Activity> screen : screens) {
                    String look = "mode=" + mode + " glass=" + glass + " " + screen.getSimpleName();
                    ActivityController<? extends Activity> c =
                            Robolectric.buildActivity(screen).create().start().resume().visible();
                    TestMedia.idle();
                    Activity a = c.get();
                    assertNotNull(look, a.findViewById(android.R.id.content));

                    Drawable window = a.getWindow().getDecorView().getBackground();
                    assertEquals(look, glass, window instanceof BackdropDrawable);
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
        assertEquals(30, opened);
    }
}
