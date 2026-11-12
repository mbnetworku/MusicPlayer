package com.musicplayer.lite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;

/** The glass question before the bars may listen to the sound, and the Settings switch for it. */
@RunWith(RobolectricTestRunner.class)
public class BarsPermissionTest {
    private static final int REQ_BARS = 9003;

    private Application app;
    private PlaybackService service;
    private SoundBarsTest.FakeSource source;
    private SoundBars sound;

    @Before
    public void setUp() throws Exception {
        app = RuntimeEnvironment.getApplication();
        TestMedia.install(app);
        FakePlayback.register();
        ShadowDialog.reset();
        source = new SoundBarsTest.FakeSource();
        sound = new SoundBars(app, source, System::currentTimeMillis);
        sound.setSession(5);
        SoundBars.replaceForTests(sound);
        Library library = Library.get(app);
        library.refresh();
        TestMedia.awaitLibrary(library);
        service = Robolectric.buildService(PlaybackService.class).create().get();
        TestMedia.exposeService(app, service);
    }

    private void startPlaying() {
        service.playQueue(Library.get(app).tracks(), 0, false);
        TestMedia.idle();
    }

    private ActivityController<PlayerActivity> openPlayer() {
        ActivityController<PlayerActivity> c =
                Robolectric.buildActivity(PlayerActivity.class).create().start().resume().visible();
        TestMedia.idle();
        return c;
    }

    private static Dialog shown() {
        Dialog d = ShadowDialog.getLatestDialog();
        return d != null && d.isShowing() ? d : null;
    }

    private static String title(Dialog d) {
        return ((TextView) d.findViewById(R.id.gd_title)).getText().toString();
    }

    // ---- the first time ------------------------------------------------------------------------------

    @Test
    public void theFirstTimeMusicPlaysOnNowPlayingAGlassQuestionIsShown() {
        startPlaying();
        ActivityController<PlayerActivity> c = openPlayer();
        Dialog d = shown();
        assertNotNull("the question is on screen", d);
        assertEquals(app.getString(R.string.perm_bars_title), title(d));
        String body = ((TextView) d.findViewById(R.id.gd_message)).getText().toString();
        assertTrue("it says why", body.contains("microphone"));
        assertTrue("and that the microphone itself is not used", body.contains("never uses the microphone"));
        assertEquals(app.getString(R.string.perm_allow), ((TextView) d.findViewById(R.id.gd_positive)).getText().toString());
        assertEquals(app.getString(R.string.perm_deny), ((TextView) d.findViewById(R.id.gd_negative)).getText().toString());
        assertTrue(Prefs.get(app).barsAsked());
        c.pause().stop().destroy();
    }

    @Test
    public void allowShowsAndroidsOwnQuestionForTheMicrophone() {
        startPlaying();
        ActivityController<PlayerActivity> c = openPlayer();
        shown().findViewById(R.id.gd_positive).performClick();
        assertFalse("the glass question closed", shown() != null);
        android.content.pm.PackageManager unused = app.getPackageManager();
        org.robolectric.shadows.ShadowActivity.PermissionsRequest request = shadowOf(c.get()).getLastRequestedPermission();
        assertNotNull(request);
        assertEquals(Manifest.permission.RECORD_AUDIO, request.requestedPermissions[0]);
        assertEquals(REQ_BARS, request.requestCode);
        c.pause().stop().destroy();
    }

    @Test
    public void notAllowedLeavesTheBarsMovingOnTheirOwnAndIsNeverAskedAgain() {
        startPlaying();
        ActivityController<PlayerActivity> c = openPlayer();
        shown().findViewById(R.id.gd_negative).performClick();
        assertNull("Android was not asked", shadowOf(c.get()).getLastRequestedPermission());
        EqBarsView bars = c.get().findViewById(R.id.np_bars);
        assertTrue("the bars still move", bars.isRunning());
        assertEquals("and do not listen", 0, source.opened);
        c.pause().stop().destroy();

        ShadowDialog.reset();
        ActivityController<PlayerActivity> again = openPlayer();
        assertNull("the question does not come back", shown());
        again.pause().stop().destroy();
    }

    @Test
    public void whenAndroidSaysYesTheBarsStartFollowingTheSound() {
        startPlaying();
        ActivityController<PlayerActivity> c = openPlayer();
        shown().findViewById(R.id.gd_positive).performClick();
        assertEquals(0, source.opened);

        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO);
        c.get().onRequestPermissionsResult(REQ_BARS, new String[]{Manifest.permission.RECORD_AUDIO},
                new int[]{PackageManager.PERMISSION_GRANTED});
        assertEquals("listening to the player's own session", 1, source.opened);
        assertTrue(sound.listening());
        c.pause().stop().destroy();
    }

    @Test
    public void whenAndroidSaysNoNothingListensAndNothingBreaks() {
        startPlaying();
        ActivityController<PlayerActivity> c = openPlayer();
        shown().findViewById(R.id.gd_positive).performClick();
        c.get().onRequestPermissionsResult(REQ_BARS, new String[]{Manifest.permission.RECORD_AUDIO},
                new int[]{PackageManager.PERMISSION_DENIED});
        assertEquals(0, source.opened);
        assertTrue("the bars carry on", ((EqBarsView) c.get().findViewById(R.id.np_bars)).isRunning());
        c.pause().stop().destroy();
    }

    @Test
    public void noQuestionWhenTheMicrophoneIsAlreadyAllowed() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO);
        startPlaying();
        ActivityController<PlayerActivity> c = openPlayer();
        assertNull(shown());
        assertEquals("it listens right away", 1, source.opened);
        c.pause().stop().destroy();
    }

    @Test
    public void noQuestionWhenTheFeatureIsSwitchedOff() {
        Prefs.get(app).setBarsFollow(false);
        startPlaying();
        ActivityController<PlayerActivity> c = openPlayer();
        assertNull(shown());
        assertFalse(Prefs.get(app).barsAsked());
        c.pause().stop().destroy();
    }

    @Test
    public void noQuestionWhileNothingPlaysButItComesWhenMusicStarts() {
        ActivityController<PlayerActivity> c = openPlayer();
        assertNull("nothing is playing yet", shown());
        startPlaying();
        assertNotNull("music started: now it is asked", shown());
        c.pause().stop().destroy();
    }

    @Test
    @Config(qualifiers = "w900dp-h500dp-land")
    public void theCarScreenAsksToo() {
        startPlaying();
        ActivityController<LibraryActivity> c =
                Robolectric.buildActivity(LibraryActivity.class).create().start().resume().visible();
        TestMedia.idle();
        Dialog d = shown();
        assertNotNull(d);
        assertEquals(app.getString(R.string.perm_bars_title), title(d));
        c.pause().stop().destroy();
    }

    @Test
    public void thePhoneLibraryScreenDoesNotAskBecauseItHasNoBigBars() {
        startPlaying();
        ActivityController<LibraryActivity> c =
                Robolectric.buildActivity(LibraryActivity.class).create().start().resume().visible();
        TestMedia.idle();
        assertNull(shown());
        assertFalse(Prefs.get(app).barsAsked());
        c.pause().stop().destroy();
    }

    // ---- the Settings switch -------------------------------------------------------------------------------

    private static Switch barsSwitch(SettingsActivity a) {
        return a.findViewById(R.id.row_bars).findViewById(R.id.set_switch);
    }

    private ActivityController<SettingsActivity> openSettings() {
        ActivityController<SettingsActivity> c =
                Robolectric.buildActivity(SettingsActivity.class).create().start().resume().visible();
        TestMedia.idle();
        return c;
    }

    @Test
    public void theSwitchIsOffUntilTheMicrophoneIsAllowed() {
        ActivityController<SettingsActivity> c = openSettings();
        SettingsActivity a = c.get();
        Switch s = barsSwitch(a);
        assertFalse(s.isChecked());
        assertEquals(app.getString(R.string.set_bars),
                ((TextView) a.findViewById(R.id.row_bars).findViewById(R.id.set_title)).getText().toString());
        assertTrue(((TextView) a.findViewById(R.id.row_bars).findViewById(R.id.set_sub)).getText().toString().contains("microphone"));
        c.pause().stop().destroy();
    }

    @Test
    public void turningItOnExplainsFirstAndNotAllowedPutsTheSwitchBack() {
        ActivityController<SettingsActivity> c = openSettings();
        SettingsActivity a = c.get();
        barsSwitch(a).performClick();
        Dialog d = shown();
        assertNotNull(d);
        assertEquals(app.getString(R.string.perm_bars_title), title(d));
        d.findViewById(R.id.gd_negative).performClick();
        assertFalse(barsSwitch(a).isChecked());
        assertFalse(Prefs.get(app).barsFollow());
        c.pause().stop().destroy();
    }

    @Test
    public void turningItOnAndAllowingSwitchesItOn() {
        ActivityController<SettingsActivity> c = openSettings();
        SettingsActivity a = c.get();
        barsSwitch(a).performClick();
        shown().findViewById(R.id.gd_positive).performClick();
        assertEquals(Manifest.permission.RECORD_AUDIO,
                shadowOf(a).getLastRequestedPermission().requestedPermissions[0]);

        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO);
        a.onRequestPermissionsResult(REQ_BARS, new String[]{Manifest.permission.RECORD_AUDIO},
                new int[]{PackageManager.PERMISSION_GRANTED});
        assertTrue(barsSwitch(a).isChecked());
        assertTrue(Prefs.get(app).barsFollow());
        c.pause().stop().destroy();
    }

    @Test
    public void ifAndroidWillNotAskAgainTheGlassMessageOffersTheAppSettings() {
        ActivityController<SettingsActivity> c = openSettings();
        SettingsActivity a = c.get();
        barsSwitch(a).performClick();
        shown().findViewById(R.id.gd_positive).performClick();
        a.onRequestPermissionsResult(REQ_BARS, new String[]{Manifest.permission.RECORD_AUDIO},
                new int[]{PackageManager.PERMISSION_DENIED});
        Dialog d = shown();
        assertNotNull("explains where to change it", d);
        assertEquals(app.getString(R.string.perm_bars_settings),
                ((TextView) d.findViewById(R.id.gd_message)).getText().toString());
        assertEquals(app.getString(R.string.perm_open_settings), ((TextView) d.findViewById(R.id.gd_positive)).getText().toString());
        assertFalse(barsSwitch(a).isChecked());
        c.pause().stop().destroy();
    }

    @Test
    public void withTheMicrophoneAlreadyAllowedTheSwitchWorksWithoutAQuestion() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO);
        ActivityController<SettingsActivity> c = openSettings();
        SettingsActivity a = c.get();
        assertTrue("on by itself: allowed and not switched off", barsSwitch(a).isChecked());
        barsSwitch(a).performClick();
        assertFalse(Prefs.get(app).barsFollow());
        assertNull(shown());
        barsSwitch(a).performClick();
        assertTrue(Prefs.get(app).barsFollow());
        assertNull("no question: Android already said yes", shown());
        c.pause().stop().destroy();
    }

    @Test
    public void switchingItOffStopsTheListening() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO);
        sound.acquire();                                  // some bars are on screen and playing
        assertEquals(1, source.opened);
        ActivityController<SettingsActivity> c = openSettings();
        barsSwitch(c.get()).performClick();
        assertEquals(1, source.closed);
        assertFalse(sound.listening());
        sound.release();
        c.pause().stop().destroy();
    }

    @Test
    public void theSwitchFollowsAChangeMadeInAndroidsOwnSettings() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO);
        ActivityController<SettingsActivity> c = openSettings();
        assertTrue(barsSwitch(c.get()).isChecked());
        c.pause();
        shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO);
        c.resume();
        assertFalse("the permission was taken away meanwhile", barsSwitch(c.get()).isChecked());
        c.pause().stop().destroy();
    }

    @Test
    public void everyPermissionTheAppAsksForIsListedOnceInTheManifest() throws Exception {
        android.content.pm.PackageInfo info = app.getPackageManager()
                .getPackageInfo(app.getPackageName(), PackageManager.GET_PERMISSIONS);
        int count = 0;
        for (String p : info.requestedPermissions) if (p.equals(Manifest.permission.RECORD_AUDIO)) count++;
        assertEquals(1, count);
    }
}
