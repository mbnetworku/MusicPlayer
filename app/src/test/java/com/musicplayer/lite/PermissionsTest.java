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
        assertArrayEquals("Android 13+: audio files only, no notification question",
                new String[]{Manifest.permission.READ_MEDIA_AUDIO},
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
    public void musicAccessIsAskedForDirectlyWithoutAPopUpOfOurOwn() {
        Activity a = Robolectric.buildActivity(SettingsActivity.class).create().start().resume().visible().get();
        Permissions.askForMusic(a, 7);
        assertEquals("Android's own question comes straight away", Manifest.permission.READ_MEDIA_AUDIO,
                shadowOf(a).getLastRequestedPermission().requestedPermissions[0]);
        org.robolectric.shadows.ShadowDialog d = null;
        assertTrue("no pop-up of ours", org.robolectric.shadows.ShadowDialog.getLatestDialog() == null);
    }
}
