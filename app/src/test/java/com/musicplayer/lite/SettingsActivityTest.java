package com.musicplayer.lite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.nio.file.Files;

@RunWith(RobolectricTestRunner.class)
public class SettingsActivityTest {
    private Application app;
    private Prefs prefs;
    private TestServer server;

    @Before
    public void setUp() throws Exception {
        TestMedia.resetSingletons();
        app = RuntimeEnvironment.getApplication();
        prefs = Prefs.get(app);
        server = new TestServer();
        PlaybackService service = Robolectric.buildService(PlaybackService.class).create().get();
        TestMedia.exposeService(app, service);
    }

    @After
    public void tearDown() {
        server.close();
    }

    private Updater useUpdater(String current) throws Exception {
        File dir = Files.createTempDirectory("settings-updates").toFile();
        Updater.Config config = new Updater.Config(server.base(), "o/r", true, dir, current,
                (c, f, v) -> 0, (c, f) -> { });
        Updater u = new Updater(app, config);
        Updater.replaceForTests(u);
        return u;
    }

    private ActivityController<SettingsActivity> open() {
        return Robolectric.buildActivity(SettingsActivity.class).create().start().resume().visible();
    }

    private static Switch switchIn(SettingsActivity a, int rowId) {
        return a.findViewById(rowId).findViewById(R.id.set_switch);
    }

    private static String text(SettingsActivity a, int id) {
        return ((TextView) a.findViewById(id)).getText().toString();
    }

    private static String sub(SettingsActivity a, int rowId) {
        return ((TextView) a.findViewById(rowId).findViewById(R.id.set_sub)).getText().toString();
    }

    @Test
    public void startsOnTheDefaults() throws Exception {
        useUpdater("1.1.0");
        SettingsActivity a = open().get();
        RadioGroup group = a.findViewById(R.id.theme_group);
        assertEquals(R.id.theme_system, group.getCheckedRadioButtonId());
        assertTrue("says what the device is using", text(a, R.id.theme_system_now).contains("mode"));
        assertFalse(switchIn(a, R.id.row_glass).isChecked());
        assertFalse(switchIn(a, R.id.row_show_all).isChecked());
        assertTrue("automatic update checks start on", switchIn(a, R.id.row_auto).isChecked());
        assertEquals("Version 1.1.0", ((TextView) a.findViewById(R.id.row_version).findViewById(R.id.set_title)).getText().toString());
        assertEquals("Check for updates", text(a, R.id.upd_action));
    }

    @Test
    public void choosingAThemeIsSaved() throws Exception {
        useUpdater("1.1.0");
        SettingsActivity a = open().get();
        RadioGroup group = a.findViewById(R.id.theme_group);
        group.check(R.id.theme_dark);
        assertEquals(Appearance.DARK, prefs.themeMode());
        group.check(R.id.theme_light);
        assertEquals(Appearance.LIGHT, prefs.themeMode());
        group.check(R.id.theme_system);
        assertEquals(Appearance.FOLLOW_DEVICE, prefs.themeMode());
    }

    @Test
    public void theGlassSwitchIsSaved() throws Exception {
        useUpdater("1.1.0");
        SettingsActivity a = open().get();
        switchIn(a, R.id.row_glass).setChecked(true);
        assertTrue(prefs.glass());
    }

    @Test
    public void libraryAndUpdateSwitchesAreSaved() throws Exception {
        useUpdater("1.1.0");
        SettingsActivity a = open().get();
        switchIn(a, R.id.row_show_all).setChecked(true);
        assertTrue(prefs.showAllAudio());
        switchIn(a, R.id.row_auto).setChecked(false);
        assertFalse(prefs.autoUpdateCheck());
    }

    @Test
    public void checkingForUpdatesShowsTheResult() throws Exception {
        server.text("/repos/o/r/releases/latest", 200, "{\"tag_name\":\"v1.1.0\",\"assets\":[{\"name\":\"a.apk\","
                + "\"browser_download_url\":\"" + server.base() + "/a.apk\",\"size\":10}]}");
        Updater u = useUpdater("1.1.0");
        SettingsActivity a = open().get();
        a.findViewById(R.id.upd_action).performClick();
        long end = System.currentTimeMillis() + 10_000;
        while (u.state() != Updater.State.UP_TO_DATE && System.currentTimeMillis() < end) {
            ShadowLooper.idleMainLooper();
            Thread.sleep(10);
        }
        assertTrue(sub(a, R.id.row_version).startsWith("You have the latest version."));
    }

    @Test
    public void aNewerVersionOffersTheDownloadWithItsNotes() throws Exception {
        String json = "{\"tag_name\":\"v9.9.9\",\"html_url\":\"https://example.org\",\"body\":\"## New\\n- Faster\","
                + "\"assets\":[{\"name\":\"a.apk\",\"browser_download_url\":\"" + server.base()
                + "/a.apk\",\"size\":2202009}]}";
        prefs.saveUpdateCheck(System.currentTimeMillis(), json);
        useUpdater("1.1.0");
        SettingsActivity a = open().get();
        assertEquals("Download and install", text(a, R.id.upd_action));
        assertTrue(sub(a, R.id.row_version).contains("9.9.9"));
        assertTrue(sub(a, R.id.row_version).contains("2.1 MB"));
        assertEquals(View.VISIBLE, a.findViewById(R.id.upd_notes).getVisibility());
        assertTrue(text(a, R.id.upd_notes).contains("Faster"));
        assertFalse("markdown marks are removed", text(a, R.id.upd_notes).contains("##"));
        assertEquals(View.VISIBLE, a.findViewById(R.id.upd_secondary).getVisibility());
    }

    @Test
    public void errorsAreShownWithATryAgainButton() throws Exception {
        server.text("/repos/o/r/releases/latest", 403, "{}");
        Updater u = useUpdater("1.1.0");
        SettingsActivity a = open().get();
        a.findViewById(R.id.upd_action).performClick();
        long end = System.currentTimeMillis() + 10_000;
        while (u.state() != Updater.State.ERROR && System.currentTimeMillis() < end) {
            ShadowLooper.idleMainLooper();
            Thread.sleep(10);
        }
        assertTrue(sub(a, R.id.row_version).contains("limiting requests"));
        assertEquals("Try again", text(a, R.id.upd_action));
    }
}
