package com.musicplayer.lite;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import java.util.ArrayList;
import java.util.List;

/** The moving equalizer bars: the motion, when the shared clock runs, and what gets drawn. */
@RunWith(RobolectricTestRunner.class)
public class EqBarsViewTest {
    // Narrow and tall, so that the bars are taller than they are wide even when resting.
    private static final int W = 40;
    private static final int H = 100;

    /** Remembers the bars that were drawn (the test runtime does not draw real pixels). */
    private static final class Recorder extends Canvas {
        final List<RectF> bars = new ArrayList<>();

        @Override
        public void drawRoundRect(RectF rect, float rx, float ry, Paint paint) {
            bars.add(new RectF(rect));
        }
    }

    private ActivityController<Activity> controller;
    private Activity activity;
    private FrameLayout root;

    @Before
    public void setUp() {
        EqBarsView.resetClock();
        controller = Robolectric.buildActivity(Activity.class).create().start().resume().visible();
        activity = controller.get();
        root = new FrameLayout(activity);
        activity.setContentView(root);
    }

    @After
    public void tearDown() {
        controller.pause().stop().destroy();
        EqBarsView.resetClock();
    }

    private EqBarsView add() {
        EqBarsView v = new EqBarsView(activity, null);
        root.addView(v, new ViewGroup.LayoutParams(W, H));
        v.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, W, H);
        return v;
    }

    private static List<RectF> draw(EqBarsView v) {
        Recorder canvas = new Recorder();
        v.draw(canvas);
        return canvas.bars;
    }

    // ---- the motion -----------------------------------------------------------------------------

    @Test
    public void everyBarMovesAlotButStaysInsideTheView() {
        for (int bar = 0; bar < 24; bar++) {
            float low = 1f;
            float high = 0f;
            for (long t = 0; t < 6000; t += 11) {
                float w = EqBarsView.wave(bar, t);
                assertTrue("wave " + w, w >= 0f && w <= 1f);
                low = Math.min(low, w);
                high = Math.max(high, w);
            }
            assertTrue("bar " + bar + " swings " + (high - low), high - low > 0.5f);
        }
    }

    @Test
    public void neighbouringBarsDoNotMoveInStep() {
        float apart = 0f;
        int samples = 0;
        for (long t = 0; t < 5000; t += 13) {
            apart += Math.abs(EqBarsView.wave(0, t) - EqBarsView.wave(1, t));
            samples++;
        }
        assertTrue("average gap " + apart / samples, apart / samples > 0.1f);
    }

    @Test
    public void restingBarsStandStillAtDifferentHeights() {
        float[] first = new float[4];
        for (int bar = 0; bar < 4; bar++) {
            first[bar] = EqBarsView.height(bar, 0, 0f);
            assertEquals("rest does not depend on the time", first[bar], EqBarsView.height(bar, 7777, 0f), 0f);
            assertTrue(first[bar] > 0.1f && first[bar] < 0.6f);
        }
        assertTrue("not all the same height", first[0] != first[1] && first[1] != first[2]);
    }

    @Test
    public void movingBarsStayBetweenTheLowestAndTheTop() {
        for (int bar = 0; bar < 24; bar++) {
            for (long t = 0; t < 4000; t += 17) {
                float h = EqBarsView.height(bar, t, 1f);
                assertTrue("height " + h, h >= 0.18f - 1e-6f && h <= 1f + 1e-6f);
            }
        }
    }

    @Test
    public void inBetweenIsHalfwayFromRestToMoving() {
        for (int bar = 0; bar < 8; bar++) {
            float rest = EqBarsView.height(bar, 1234, 0f);
            float moving = EqBarsView.height(bar, 1234, 1f);
            assertEquals((rest + moving) / 2f, EqBarsView.height(bar, 1234, 0.5f), 1e-5f);
        }
    }

    @Test
    public void anEnvelopeIsStretchedAcrossTheBars() {
        assertEquals("no envelope: nothing held back", 1f, EqBarsView.envelopeAt(null, 3, 8), 0f);
        float[] e = {0.2f, 1f};
        float[] expected = {0.2f, 0.4f, 0.6f, 0.8f, 1f};
        for (int i = 0; i < 5; i++) assertEquals(expected[i], EqBarsView.envelopeAt(e, i, 5), 1e-5f);
        assertEquals("one value holds for every bar", 0.5f, EqBarsView.envelopeAt(new float[]{0.5f}, 2, 6), 0f);
        assertEquals("a single bar takes the first value", 0.3f, EqBarsView.envelopeAt(new float[]{0.3f, 0.9f}, 0, 1), 0f);
        float[] three = {0f, 1f, 0f};
        assertEquals(1f, EqBarsView.envelopeAt(three, 2, 5), 1e-5f);
    }

    @Test
    public void equalizerLevelsBecomeBarLimits() {
        // Five bands from -1500 to +1500 millibels: a cut, flat, flat, a small boost, the top boost.
        float[] env = EqBarsView.envelopeFromLevels(new int[]{-1500, 0, 0, 750, 1500}, -1500, 1500);
        assertEquals(EqBarsView.ENVELOPE_MIN, env[0], 1e-5f);
        assertEquals("flat sits halfway", EqBarsView.ENVELOPE_MIN + (1f - EqBarsView.ENVELOPE_MIN) * 0.5f, env[1], 1e-5f);
        assertEquals(env[1], env[2], 0f);
        assertTrue(env[3] > env[2]);
        assertEquals(1f, env[4], 1e-5f);
        // Out-of-range levels are held at the ends.
        float[] clamped = EqBarsView.envelopeFromLevels(new int[]{-9000, 9000}, -1500, 1500);
        assertEquals(EqBarsView.ENVELOPE_MIN, clamped[0], 1e-5f);
        assertEquals(1f, clamped[1], 1e-5f);
        assertEquals(0, EqBarsView.envelopeFromLevels(new int[0], -1500, 1500).length);
    }

    // ---- the shared clock -----------------------------------------------------------------------------

    @Test
    public void aNewViewIsAtRestAndCostsNothing() {
        EqBarsView v = add();
        assertFalse(v.isPlaying());
        assertFalse(v.isRunning());
        assertEquals(0, EqBarsView.runningCount());
    }

    @Test
    public void playingStartsTheClockAndPausingEasesOutThenStopsIt() {
        EqBarsView v = add();
        v.setPlaying(true);
        assertTrue(v.isRunning());
        assertEquals(1, EqBarsView.runningCount());
        EqBarsView.tickAll(9_000);
        EqBarsView.tickAll(9_300);                 // long enough to be fully moving

        v.setPlaying(false);
        assertTrue("still easing down", v.isRunning());
        EqBarsView.tickAll(9_400);                 // 100 ms after the last frame
        EqBarsView.tickAll(9_500);
        assertTrue("200 ms is not long enough to settle", v.isRunning());
        EqBarsView.tickAll(9_700);
        assertFalse("settled: no more frames needed", v.isRunning());
        assertEquals(0, EqBarsView.runningCount());
    }

    @Test
    public void aMovingViewKeepsTheClockGoing() {
        EqBarsView v = add();
        v.setPlaying(true);
        for (long t = 0; t < 3000; t += 33) EqBarsView.tickAll(5_000 + t);
        assertTrue(v.isRunning());
    }

    @Test
    public void severalViewsShareOneClock() {
        EqBarsView a = add();
        EqBarsView b = add();
        a.setPlaying(true);
        b.setPlaying(true);
        assertEquals(2, EqBarsView.runningCount());
        a.setPlaying(false);
        EqBarsView.tickAll(1_000);
        EqBarsView.tickAll(1_500);
        assertFalse(a.isRunning());
        assertTrue("the other one is still playing", b.isRunning());
    }

    @Test
    public void aHiddenViewDoesNotRunAndRunsAgainWhenShown() {
        EqBarsView v = add();
        v.setPlaying(true);
        v.setVisibility(View.GONE);
        assertFalse(v.isRunning());
        v.setVisibility(View.VISIBLE);
        assertTrue(v.isRunning());
    }

    @Test
    public void aViewInsideAHiddenParentDoesNotRun() {
        FrameLayout holder = new FrameLayout(activity);
        root.addView(holder);
        EqBarsView v = new EqBarsView(activity, null);
        holder.addView(v, new ViewGroup.LayoutParams(W, H));
        v.setPlaying(true);
        assertTrue(v.isRunning());
        holder.setVisibility(View.GONE);
        assertFalse("its parent is hidden, so nobody sees it", v.isRunning());
        holder.setVisibility(View.VISIBLE);
        assertTrue(v.isRunning());
    }

    @Test
    public void aViewThatLeavesTheScreenLeavesTheClock() {
        EqBarsView v = add();
        v.setPlaying(true);
        root.removeView(v);
        assertFalse(v.isRunning());
        assertEquals("nothing is kept alive by the clock", 0, EqBarsView.runningCount());
        root.addView(v);
        assertTrue(v.isRunning());
    }

    @Test
    public void whenTheSystemAnimationsAreOffTheBarsStandStill() {
        Settings.Global.putFloat(activity.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
        EqBarsView v = add();
        v.setPlaying(true);
        assertFalse("no clock at all", v.isRunning());
        // A fixed, lively picture instead of motion.
        List<RectF> bars = draw(v);
        assertEquals(4, bars.size());
        for (int i = 0; i < 4; i++) {
            float expected = H * EqBarsView.height(i, EqBarsView.STILL_MS, 1f);
            assertEquals(expected, bars.get(i).height(), 0.5f);
        }
        v.setPlaying(false);
        float rest = H * EqBarsView.height(0, 0, 0f);
        assertEquals("paused bars rest", rest, draw(v).get(0).height(), 0.5f);
    }

    // ---- what is drawn --------------------------------------------------------------------------------

    @Test
    public void restingBarsAreDrawnAtTheirRestingHeightsOnTheBottomLine() {
        EqBarsView v = add();
        List<RectF> bars = draw(v);
        assertEquals(4, bars.size());
        for (int i = 0; i < 4; i++) {
            assertEquals("bars stand on the bottom", H, bars.get(i).bottom, 0.01f);
            assertEquals(H * EqBarsView.height(i, 0, 0f), bars.get(i).height(), 0.5f);
        }
        assertTrue("left to right, without overlap", bars.get(1).left >= bars.get(0).right);
        assertTrue(bars.get(3).right <= W + 0.01f);
        assertEquals("all the same width", bars.get(0).width(), bars.get(3).width(), 0.01f);
    }

    @Test
    public void aPlayingFrameIsDrawnAtTheGivenMoment() {
        EqBarsView v = add();
        v.setPlaying(true);
        v.setFrameTime(1234);
        assertFalse("a still frame does not use the clock", v.isRunning());
        List<RectF> bars = draw(v);
        for (int i = 0; i < 4; i++) {
            assertEquals(H * EqBarsView.height(i, 1234, 1f), bars.get(i).height(), 0.5f);
        }
        v.setFrameTime(1900);
        List<RectF> later = draw(v);
        boolean changed = false;
        for (int i = 0; i < 4; i++) changed |= Math.abs(later.get(i).height() - bars.get(i).height()) > 0.5f;
        assertTrue("another moment, another picture", changed);
    }

    @Test
    public void aPinnedMomentIsDrawnByEveryViewWithoutTheClock() {
        EqBarsView a = add();
        EqBarsView b = add();
        a.setPlaying(true);
        assertTrue(a.isRunning());
        EqBarsView.pinTime(900);
        assertFalse("pinned views do not use the clock", a.isRunning());
        assertEquals(0, EqBarsView.runningCount());
        b.setPlaying(true);
        assertFalse("and neither do views that start playing later", b.isRunning());
        for (EqBarsView v : new EqBarsView[]{a, b}) {
            List<RectF> bars = draw(v);
            for (int i = 0; i < 4; i++) assertEquals(H * EqBarsView.height(i, 900, 1f), bars.get(i).height(), 0.5f);
        }
        EqBarsView.pinTime(-1);
        EqBarsView c = add();
        c.setPlaying(true);
        assertTrue("a new view is back on the clock", c.isRunning());
        a.setPlaying(false);
        a.setPlaying(true);
        assertTrue("and so is one that is started again", a.isRunning());
    }

    @Test
    public void anEnvelopeLimitsTheBars() {
        EqBarsView v = add();
        v.setPlaying(true);
        v.setFrameTime(500);
        List<RectF> full = draw(v);
        v.setEnvelope(new float[]{0.5f});
        List<RectF> half = draw(v);
        for (int i = 0; i < 4; i++) {
            assertEquals(full.get(i).height() / 2f, half.get(i).height(), 0.6f);
        }
        assertArrayEquals(new float[]{0.5f}, v.envelope(), 0f);
        v.setEnvelope(null);
        assertNull(v.envelope());
    }

    @Test
    public void aBarIsNeverThinnerThanItIsWide() {
        EqBarsView v = add();
        v.setPlaying(false);
        v.setEnvelope(new float[]{0f});
        for (RectF bar : draw(v)) assertTrue(bar.height() >= bar.width() - 0.01f);
    }

    @Test
    public void paddingKeepsTheBarsInside() {
        EqBarsView v = add();
        v.setPadding(6, 8, 6, 4);
        for (RectF bar : draw(v)) {
            assertTrue(bar.left >= 6f - 0.01f && bar.right <= W - 6f + 0.01f);
            assertEquals("the bottom padding is respected", H - 4f, bar.bottom, 0.01f);
            assertTrue(bar.top >= 8f - 0.01f);
        }
    }

    @Test
    public void wrappingTheBarsDoesNotTakeAllTheRoomOffered() {
        EqBarsView v = new EqBarsView(activity, null);
        v.measure(View.MeasureSpec.makeMeasureSpec(5000, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(5000, View.MeasureSpec.AT_MOST));
        assertTrue("width " + v.getMeasuredWidth(), v.getMeasuredWidth() > 0 && v.getMeasuredWidth() < 200);
        assertTrue("height " + v.getMeasuredHeight(), v.getMeasuredHeight() > 0 && v.getMeasuredHeight() < 200);
        v.measure(View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(70, View.MeasureSpec.EXACTLY));
        assertEquals("an exact size is obeyed", 300, v.getMeasuredWidth());
        assertEquals(70, v.getMeasuredHeight());
        v.setPadding(10, 5, 10, 5);
        v.measure(View.MeasureSpec.makeMeasureSpec(30, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(5000, View.MeasureSpec.AT_MOST));
        assertEquals("never more than offered", 30, v.getMeasuredWidth());
    }

    @Test
    public void anEmptyViewDrawsNothing() {
        EqBarsView v = new EqBarsView(activity, null);
        assertTrue(draw(v).isEmpty());
    }
}
