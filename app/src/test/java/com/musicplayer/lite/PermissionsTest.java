package com.musicplayer.lite;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.view.View;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/** Which permissions each Android version needs, and the glass question that comes before them. */
@RunWith(RobolectricTestRunner.class)
public class PermissionsTest {
    private Application app;

    @Before
    public void setUp() {
        TestMedia.resetSingletons();
        app = RuntimeEnvironment.getApplication();
    }

    @Test
    public void musicPermissionsDependOnTheAndroidVersion() {
        assertArrayEquals("Android 8 to 12: storage", new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                Permissions.musicPermissions(26));
        assertArrayEquals(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, Permissions.musicPermissions(32));
        assertArrayEquals("Android 13+: audio files and notifications",
                new String[]{Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS},
                Permissions.musicPermissions(33));
    }

    @Test
    public void writeAccessIsNeededOnOlderAndroidOnly() {
        assertFalse("Android 9 without the grant", Permissions.hasWrite(28, false));
        assertTrue("Android 9 with the grant", Permissions.hasWrite(28, true));
        assertFalse("Android 10 without the grant", Permissions.hasWrite(29, false));
        assertTrue("Android 11 asks per delete instead", Permissions.hasWrite(30, false));
        assertTrue(Permissions.hasWrite(35, false));
    }

    @Test
    public void thisPhoneNeedsNoWriteAccessBecauseTheTestRunsOnAndroid15() {
        assertTrue(Permissions.hasWrite(app));
    }

    @Test
    public void theGlassQuestionComesBeforeAndroidsOwn() {
        Activity a = Robolectric.buildActivity(SettingsActivity.class).create().start().resume().visible().get();
        final boolean[] asked = {false};
        Permissions.askForMusic(a, 7, () -> asked[0] = true);
        // Nothing has been requested yet: only the pop-up is on screen.
        assertTrue(shadowOf(a).getLastRequestedPermission() == null);
        View allow = org.robolectric.shadows.ShadowDialog.getLatestDialog().findViewById(R.id.gd_positive);
        View deny = org.robolectric.shadows.ShadowDialog.getLatestDialog().findViewById(R.id.gd_negative);
        assertEquals("Allow", ((android.widget.TextView) allow).getText().toString());
        assertEquals("Not allowed", ((android.widget.TextView) deny).getText().toString());

        allow.performClick();
        assertEquals("Allow shows Android's own question", Manifest.permission.READ_MEDIA_AUDIO,
                shadowOf(a).getLastRequestedPermission().requestedPermissions[0]);
        assertFalse(asked[0]);
    }

    @Test
    public void notAllowedLeavesTheFeatureOff() {
        Activity a = Robolectric.buildActivity(SettingsActivity.class).create().start().resume().visible().get();
        final boolean[] declined = {false};
        Permissions.askForMusic(a, 7, () -> declined[0] = true);
        org.robolectric.shadows.ShadowDialog.getLatestDialog().findViewById(R.id.gd_negative).performClick();
        assertTrue(declined[0]);
        assertTrue("Android was never asked", shadowOf(a).getLastRequestedPermission() == null);
    }
}
