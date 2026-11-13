package com.musicplayer.lite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/** The big bars over the cover on the Now Playing screen: thin, many, and shaped like a music spectrum. */
@RunWith(RobolectricTestRunner.class)
public class BigBarsTest {
    private Application app;
    private Track track;

    private static final class Recorder extends Canvas {
        final List<RectF> bars = new ArrayList<>();

        @Override
        public void drawRoundRect(RectF rect, float rx, float ry, Paint paint) {
            bars.add(new RectF(rect));
        }
    }

    @Before
    public void setUp() throws Exception {
        TestMedia.install(app = RuntimeEnvironment.getApplication());
        Prefs.get(app).setGlass(false);   // these sizes were worked out for the flat look: the glass look is shorter
        Library library = Library.get(app);
        library.refresh();
        TestMedia.awaitLibrary(library);
        track = library.tracks().get(0);
    }

    /** Opens the screen, shows the song as playing and lays the screen out at {@code w} x {@code h} pixels. */
    private List<RectF> bigBarsOnPlayer(int w, int h, long frame, boolean playing) {
        ActivityController<PlayerActivity> c =
                Robolectric.buildActivity(PlayerActivity.class).create().start().resume().visible();
        PlayerActivity a = c.get();
        NowPlayingView np = a.findViewById(R.id.np);
        np.render(track, playing, 0, 1000, false, PlayQueue.REPEAT_OFF);
        EqBarsView bars = a.findViewById(R.id.np_bars);
        bars.setFrameTime(frame);
        View content = a.findViewById(android.R.id.content);
        content.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        content.layout(0, 0, w, h);
        Recorder canvas = new Recorder();
        bars.draw(canvas);
        c.pause().stop().destroy();
        return canvas.bars;
    }

    @Test
    public void manyThinBarsStandOnTheCoverLikeASpectrum() {
        List<RectF> bars = bigBarsOnPlayer(360, 640, 500, true);
        assertEquals("one bar for each of the " + app.getResources().getInteger(R.integer.np_bars),
                app.getResources().getInteger(R.integer.np_bars), bars.size());
        assertEquals(48, bars.size());

        RectF first = bars.get(0);
        RectF second = bars.get(1);
        assertEquals("bars and gaps are about as wide as each other: the pitch is " + (second.left - first.left),
                1.9f, (second.left - first.left) / first.width(), 0.02f);
        assertTrue("thin: " + first.width(), first.width() < 6f);
        float spread = bars.get(47).right - first.left;
        assertTrue("across the whole cover: " + spread, spread > 200 && spread < 360);
    }

    @Test
    public void theOwnMotionIsTallAtTheBassEndAndShortAtTheTrebleEnd() {
        List<RectF> bars = bigBarsOnPlayer(360, 640, 500, true);
        float bass = 0;
        float treble = 0;
        for (int i = 0; i < 16; i++) bass += bars.get(i).height();
        for (int i = 32; i < 48; i++) treble += bars.get(i).height();
        assertTrue("bass " + bass + " against treble " + treble, bass > treble * 1.8f);
        float tallest = 0;
        for (RectF bar : bars) tallest = Math.max(tallest, bar.height());
        assertTrue("tall: " + tallest, tallest > 60f);
    }

    @Test
    public void pausedTheBarsRestLow() {
        List<RectF> bars = bigBarsOnPlayer(360, 640, 500, false);
        for (RectF bar : bars) assertTrue("resting bar " + bar.height(), bar.height() < 40f);
    }

    @Test
    @Config(qualifiers = "w900dp-h500dp-land")
    public void aCarScreenHasEvenMoreBars() {
        List<RectF> bars = bigBarsOnPlayerInSplit(1280, 720);
        assertEquals(56, bars.size());
    }

    /** In the car layout the player is part of the library screen. */
    private List<RectF> bigBarsOnPlayerInSplit(int w, int h) {
        ActivityController<LibraryActivity> c =
                Robolectric.buildActivity(LibraryActivity.class).create().start().resume().visible();
        LibraryActivity a = c.get();
        NowPlayingView np = a.findViewById(R.id.np);
        np.render(track, true, 0, 1000, false, PlayQueue.REPEAT_OFF);
        EqBarsView bars = a.findViewById(R.id.np_bars);
        bars.setFrameTime(500);
        View content = a.findViewById(android.R.id.content);
        content.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        content.layout(0, 0, w, h);
        Recorder canvas = new Recorder();
        bars.draw(canvas);
        c.pause().stop().destroy();
        return canvas.bars;
    }

    @Test
    public void theFadeBehindTheBarsIsTallEnoughForThem() {
        ActivityController<PlayerActivity> c =
                Robolectric.buildActivity(PlayerActivity.class).create().start().resume().visible();
        View bars = c.get().findViewById(R.id.np_bars);
        assertEquals((int) c.get().getResources().getDimension(R.dimen.np_bars_height), (int) (160 * c.get().getResources().getDisplayMetrics().density));
        assertTrue("a real backdrop", bars.getBackground() != null);
        c.pause().stop().destroy();
    }
}
