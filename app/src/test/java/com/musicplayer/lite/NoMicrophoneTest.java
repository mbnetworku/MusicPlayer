package com.musicplayer.lite;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Arrays;

/** The app never asks for the microphone. */
@RunWith(RobolectricTestRunner.class)
public class NoMicrophoneTest {
    @Test
    public void theAppDoesNotRequestRecordAudio() throws Exception {
        android.app.Application app = RuntimeEnvironment.getApplication();
        PackageInfo info = app.getPackageManager().getPackageInfo(app.getPackageName(),
                PackageManager.GET_PERMISSIONS);
        assertNotNull(info.requestedPermissions);
        assertFalse(Arrays.asList(info.requestedPermissions).contains("android.permission.RECORD_AUDIO"));
    }
}
