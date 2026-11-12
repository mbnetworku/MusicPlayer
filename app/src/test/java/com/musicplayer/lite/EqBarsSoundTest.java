package com.musicplayer.lite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
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

/** The bars when the app may listen to its own sound, and the music-spectrum look of their own motion. */
@RunWith(RobolectricTestRunner.class)
public class EqBarsSoundTest {
    private static final int W = 40;
    private static final int H = 100;

    private static final class Recorder extends Canvas {
        final List<RectF> bars = new ArrayList<>();

        @Override
        public void drawRoundRect(RectF rect, float rx, float ry, Paint paint) {
            bars.add(new RectF(rect));
        }
    }

    private ActivityController<Activity> controller;
    private Activity activity;
    private Application app;
    private FrameLayout root;
    private SoundBarsTest.FakeSource source;
    private SoundBars sound;
    private final long[] now = {1000};

    @Before
    public void setUp() {
        TestMedia.resetSingletons();
        controller = Robolectric.buildActivity(Activity.class).create().start().resume().visible();
        activity = controller.get();
        app = activity.getApplication();
        root = new FrameLayout(activity);
        activity.setContentView(root);
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO);
        source = new SoundBarsTest.FakeSource();
        sound = new SoundBars(app, source, () -> now[0]);
        sound.setSession(5);
        SoundBars.replaceForTests(sound);
    }

    @After
    public void tearDown() {
        controller.pause().stop().destroy();
        EqBarsView.resetClock();
        SoundBars.replaceForTests(null);
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

    /** Every bin of a frame as loud as {@code amplitude}: the whole row of bars goes up. */
    private void broadband(int amplitude) {
        byte[] fft = new byte[1024];
        for (int k = 1; k < 512; k++) fft[2 * k] = (byte) amplitude;
        source.frames.onFft(fft, 44_100_000);
    }

    /** Lets the view finish easing up to full motion. */
    private static void settleMoving(long from) {
        EqBarsView.tickAll(from);
        EqBarsView.tickAll(from + 300);
    }

    // ---- following the real sound ---------------------------------------------------------------------

    @Test
    public void aPlayingViewAsksForTheSoundAndAPausedOneLetsGo() {
        EqBarsView v = add();
        assertEquals("a view at rest does not listen", 0, source.opened);
        v.setPlaying(true);
        assertEquals(1, source.opened);
        settleMoving(5000);
        v.setPlaying(false);
        assertEquals("still easing down: still listening", 0, source.closed);
        EqBarsView.tickAll(5400);
        EqBarsView.tickAll(5700);
        assertFalse(v.isRunning());
        assertEquals("settled: listening stops", 1, source.closed);
    }

    @Test
    public void severalPlayingViewsShareOneListener() {
        EqBarsView a = add();
        EqBarsView b = add();
        a.setPlaying(true);
        b.setPlaying(true);
        assertEquals(1, source.opened);
        root.removeView(a);
        assertEquals("the other one still wants it", 0, source.closed);
        root.removeView(b);
        assertEquals(1, source.closed);
    }

    @Test
    public void theBarsRiseWithTheRealSound() {
        EqBarsView v = add();
        v.setPlaying(true);
        settleMoving(5000);
        broadband(100);
        assertTrue(sound.following());
        List<RectF> bars = draw(v);
        assertEquals(4, bars.size());
        for (RectF bar : bars) assertEquals("every bar is up", H, bar.height(), 1f);

        for (int i = 0; i < 40; i++) source.frames.onFft(new byte[1024], 44_100_000);   // the music stops
        bars = draw(v);
        for (RectF bar : bars) assertTrue("and every bar is back down: " + bar.height(), bar.height() < 12f);
    }

    @Test
    public void eachBarFollowsItsOwnPartOfTheSound() {
        EqBarsView v = add();
        v.setPlaying(true);
        settleMoving(5000);
        byte[] bass = new byte[1024];
        for (int k = 1; k < 12; k++) bass[2 * k] = 100;                  // only the lowest bins
        source.frames.onFft(bass, 44_100_000);
        List<RectF> bars = draw(v);
        assertTrue("the left bar is up: " + bars.get(0).height(), bars.get(0).height() > 0.3f * H);
        assertTrue("the right bar is not: " + bars.get(3).height(), bars.get(3).height() < 0.1f * H);
    }

    @Test
    public void untilTheFirstFrameArrivesTheBarsMoveOnTheirOwn() {
        EqBarsView v = add();
        v.setPlaying(true);
        settleMoving(5000);
        assertFalse(sound.following());
        List<RectF> bars = draw(v);
        for (int i = 0; i < 4; i++) {
            assertEquals(H * EqBarsView.height(i, 5300, 1f), bars.get(i).height(), 0.6f);
        }
    }

    @Test
    public void whenTheSoundStopsComingTheBarsGoBackToTheirOwnMotion() {
        EqBarsView v = add();
        v.setPlaying(true);
        settleMoving(5000);
        broadband(100);
        assertTrue(sound.following());
        now[0] += 700;                                                    // no frame for 700 ms
        assertFalse(sound.following());
        List<RectF> bars = draw(v);
        for (int i = 0; i < 4; i++) {
            assertEquals(H * EqBarsView.height(i, 5300, 1f), bars.get(i).height(), 0.6f);
        }
    }

    @Test
    public void picturesAndTestsThatPinTheMomentNeverListen() {
        EqBarsView.pinTime(500);
        EqBarsView v = add();
        v.setPlaying(true);
        assertEquals(0, source.opened);
        EqBarsView.pinTime(-1);
        EqBarsView f = add();
        f.setFrameTime(300);
        f.setPlaying(true);
        assertEquals(0, source.opened);
    }

    @Test
    public void withoutThePermissionTheBarsJustMoveOnTheirOwn() {
        shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO);
        EqBarsView v = add();
        v.setPlaying(true);
        assertEquals(0, source.opened);
        assertTrue(v.isRunning());
        assertFalse(sound.listening());
    }

    @Test
    public void anEnvelopeStillLimitsTheBarsWhileFollowingTheSound() {
        EqBarsView v = add();
        v.setPlaying(true);
        settleMoving(5000);
        v.setEnvelope(new float[]{0.5f});
        broadband(100);
        for (RectF bar : draw(v)) assertEquals(H / 2f, bar.height(), 1f);
    }

    // ---- the look of their own motion ---------------------------------------------------------------------

    @Test
    public void theSpectrumShapeIsLowAtTheEdgeTallInTheBassAndFallsAway() {
        float[] shape = EqBarsView.spectrumEnvelope(48);
        assertEquals(48, shape.length);
        int peak = 0;
        for (int i = 1; i < shape.length; i++) if (shape[i] > shape[peak]) peak = i;
        assertTrue("the top of the shape: " + shape[peak], shape[peak] > 0.99f && shape[peak] <= 1f);
        assertTrue("the peak is in the left fifth: " + peak, peak >= 4 && peak <= 10);
        assertTrue("the far left is lower than the peak", shape[0] < 0.5f);
        for (int i = peak; i < shape.length - 1; i++) assertTrue("falling at " + i, shape[i + 1] < shape[i]);
        assertTrue("treble is short", shape[47] < 0.2f);
        assertEquals(1, EqBarsView.spectrumEnvelope(1).length);
        assertEquals(0, EqBarsView.spectrumEnvelope(0).length);
        for (float v : shape) assertTrue(v > 0f && v <= 1f);
    }
}
