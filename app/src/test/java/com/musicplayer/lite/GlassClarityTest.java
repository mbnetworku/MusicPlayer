package com.musicplayer.lite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.widget.RadioGroup;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;

/** The Glass transparency setting. */
@RunWith(RobolectricTestRunner.class)
public class GlassClarityTest {
    @Test
    public void clearGlassShowsLessFillThanFrostedGlass() {
        assertTrue(GlassDrawable.scaleFor(0) < GlassDrawable.scaleFor(1));
        assertTrue(GlassDrawable.scaleFor(1) < GlassDrawable.scaleFor(2));
        assertEquals(1f, GlassDrawable.scaleFor(2), 0f);
    }

    @Test
    public void clearIsTheDefaultAndBadValuesFallBack() {
        Application app = RuntimeEnvironment.getApplication();
        Prefs p = Prefs.get(app);
        assertEquals(0, p.glassClarity());
        p.setGlassClarity(2);
        assertEquals(2, p.glassClarity());
        p.setGlassClarity(9);
        assertEquals(0, p.glassClarity());
    }

    @Test
    public void theLookChangesWhenTheTransparencyChanges() {
        Application app = RuntimeEnvironment.getApplication();
        Prefs p = Prefs.get(app);
        p.setGlassClarity(0);
        int before = Appearance.signature(app);
        p.setGlassClarity(1);
        assertTrue("open screens notice and rebuild", before != Appearance.signature(app));
        p.setGlassClarity(0);
    }

    @Test
    public void settingsHasTheThreeLevelsAndSavesTheChoice() {
        Application app = RuntimeEnvironment.getApplication();
        Prefs.get(app).setGlassClarity(0);
        ActivityController<SettingsActivity> c = Robolectric.buildActivity(SettingsActivity.class).create().start().resume().visible();
        RadioGroup g = c.get().findViewById(R.id.clarity_group);
        assertEquals(3, g.getChildCount());
        assertEquals(R.id.clarity_clear, g.getCheckedRadioButtonId());
        g.check(R.id.clarity_frosted);
        assertEquals(2, Prefs.get(app).glassClarity());
        c.pause().stop().destroy();
        Prefs.get(app).setGlassClarity(0);
    }
}
