package com.musicplayer.lite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RadioGroup;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.List;

/** The four equalizer designs and the setting that chooses between them. */
@RunWith(RobolectricTestRunner.class)
public class BarStyleTest {
    private static final class Recorder extends Canvas {
        final List<RectF> shapes = new ArrayList<>();
        int paths;

        @Override
        public void drawRoundRect(RectF rect, float rx, float ry, Paint paint) {
            shapes.add(new RectF(rect));
        }

        @Override
        public void drawPath(Path path, Paint paint) {
            paths++;
        }
    }

    private Recorder draw(int style) {
        Activity a = Robolectric.buildActivity(Activity.class).create().start().resume().visible().get();
        FrameLayout root = new FrameLayout(a);
        a.setContentView(root);
        EqBarsView v = new EqBarsView(a, null);
        root.addView(v, new ViewGroup.LayoutParams(200, 100));
        v.measure(View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, 200, 100);
        v.setStyle(style);
        Recorder r = new Recorder();
        v.draw(r);
        return r;
    }

    @Before
    public void setUp() {
        EqBarsView.resetClock();
        Prefs.get(RuntimeEnvironment.getApplication()).setBarStyle(0);
    }

    @Test
    public void barsStandOnTheFloor() {
        Recorder r = draw(EqBarsView.STYLE_BARS);
        assertTrue(r.shapes.size() > 3);
        for (RectF s : r.shapes) assertEquals(100f, s.bottom, 0.01f);
    }

    @Test
    public void dotsAreStackedSmallCells() {
        Recorder r = draw(EqBarsView.STYLE_DOTS);
        assertTrue("more shapes than bars: " + r.shapes.size(), r.shapes.size() > draw(EqBarsView.STYLE_BARS).shapes.size());
        for (RectF s : r.shapes) assertTrue("each dot is small", s.height() < 15f);
    }

    @Test
    public void mirrorBarsGrowFromTheMiddle() {
        Recorder r = draw(EqBarsView.STYLE_MIRROR);
        assertTrue(r.shapes.size() > 3);
        for (RectF s : r.shapes) assertEquals(50f, (s.top + s.bottom) / 2f, 0.01f);
    }

    @Test
    public void theWaveIsOneFilledShape() {
        Recorder r = draw(EqBarsView.STYLE_WAVE);
        assertEquals(1, r.paths);
        assertEquals(0, r.shapes.size());
    }

    @Test
    public void defaultIsBarsAndTheChoiceIsKept() {
        Prefs p = Prefs.get(RuntimeEnvironment.getApplication());
        assertEquals(0, p.barStyle());
        p.setBarStyle(2);
        assertEquals(2, p.barStyle());
        p.setBarStyle(99);
        assertEquals("a bad value falls back to bars", 0, p.barStyle());
    }

    @Test
    public void settingsOffersTheDesignsAndSavesTheChoice() throws Exception {
        Prefs p = Prefs.get(RuntimeEnvironment.getApplication());
        SettingsActivity a = Robolectric.buildActivity(SettingsActivity.class).create().start().resume().get();
        RadioGroup g = a.findViewById(R.id.bar_style_group);
        assertEquals(4, g.getChildCount());
        assertEquals(R.id.bar_style_bars, g.getCheckedRadioButtonId());
        g.check(R.id.bar_style_dots);
        assertEquals(1, p.barStyle());
        g.check(R.id.bar_style_wave);
        assertEquals(3, p.barStyle());
    }
}
