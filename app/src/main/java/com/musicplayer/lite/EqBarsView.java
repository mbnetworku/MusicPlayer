package com.musicplayer.lite;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Bars that jump up and down while music plays and settle low when it is paused, like the
 * equalizer of a music app.
 *
 * <p>All bar views share one clock (about 30 frames a second). The clock only runs while at least
 * one view is on screen and moving, so a paused, hidden or stopped player costs nothing. With the
 * system's animations switched off the bars stand still instead of moving.
 */
public class EqBarsView extends View {
    /** The lowest a bar falls while playing, as a fraction of the view's height. */
    private static final float PLAYING_MIN = 0.18f;
    /** How high the bars stand when paused (repeated over the bars). */
    private static final float[] REST = {0.30f, 0.52f, 0.38f, 0.24f};
    /** The clock reading drawn when animations are off: a lively-looking moment of the wave. */
    static final long STILL_MS = 640;
    /** Time to go from moving to resting, and back. */
    private static final float EASE_MS = 280f;
    /** Gap between bars, as a fraction of a bar's width. */
    private static final float GAP = 0.55f;
    /** The delay between frames: a little under 1000 / 30, because frames are aligned to the screen. */
    private static final long FRAME_DELAY_MS = 24;

    // ---- the shared clock ------------------------------------------------------------------------

    private static final List<EqBarsView> RUNNING = new ArrayList<>();
    private static boolean scheduled;
    private static long lastFrame;
    /** When not negative: every bar view stands still, drawn at this moment of the wave (for pictures and tests). */
    private static long pinned = -1;
    private static final Choreographer.FrameCallback FRAME = nanos -> {
        scheduled = false;
        tickAll(nanos / 1_000_000L);
    };

    /** Moves every running view to the clock reading {@code nowMs}; asks for another frame if any still run. */
    static void tickAll(long nowMs) {
        final long dt = lastFrame == 0 ? 0 : nowMs - lastFrame;
        lastFrame = nowMs;
        // Backwards, because a view that has settled removes itself while it is stepped.
        for (int i = RUNNING.size() - 1; i >= 0; i--) {
            if (i < RUNNING.size()) RUNNING.get(i).step(nowMs, dt);
        }
        if (RUNNING.isEmpty()) lastFrame = 0;
        else schedule();
    }

    private static void schedule() {
        if (scheduled) return;
        scheduled = true;
        Choreographer.getInstance().postFrameCallbackDelayed(FRAME, FRAME_DELAY_MS);
    }

    /** How many bar views the clock is moving right now. */
    static int runningCount() {
        return RUNNING.size();
    }

    /**
     * Makes every bar view stand still and draw the wave as it is at {@code timeMs} (playing views
     * at full height, paused ones resting). A negative value goes back to the normal clock; views
     * that were standing still pick that up the next time they change (play, pause, hide, show).
     * Used to draw pictures of the bars, and by tests; the app itself never calls it.
     */
    static void pinTime(long timeMs) {
        pinned = timeMs;
        for (int i = RUNNING.size() - 1; i >= 0; i--) RUNNING.get(i).update();
    }

    /** Forgets every running view and the pending frame (the tests start each case from nothing). */
    static void resetClock() {
        RUNNING.clear();
        scheduled = false;
        lastFrame = 0;
        pinned = -1;
    }

    // ---- the motion itself (pure, so it can be tested) --------------------------------------------

    /** How far up bar {@code bar} is at {@code timeMs} while playing: 0 to 1. */
    static float wave(int bar, long timeMs) {
        final double t = timeMs / 1000.0;
        final double a = Math.sin(t * (6.0 + 1.3 * (bar % 5)) + bar * 1.7);
        final double b = Math.sin(t * (11.0 + 1.9 * (bar % 3)) + bar * 0.9 + 1.3);
        final double v = 0.5 + 0.30 * a + 0.20 * b;
        return (float) Math.max(0.0, Math.min(1.0, v));
    }

    /**
     * The height of a bar as a fraction of the view. {@code moving} runs from 0 (resting) to 1
     * (playing); in between, the bar is on its way from one to the other.
     */
    static float height(int bar, long timeMs, float moving) {
        return height(bar, timeMs, moving, 1f);
    }

    /** Like {@link #height(int, long, float)}, with the resting height scaled by {@code restScale}. */
    static float height(int bar, long timeMs, float moving, float restScale) {
        final float rest = REST[bar % REST.length] * restScale;
        final float playing = PLAYING_MIN + (1f - PLAYING_MIN) * wave(bar, timeMs);
        return rest + (playing - rest) * moving;
    }

    /** The lowest an equalizer's deepest cut lets the bars reach, as a fraction of the view. */
    static final float ENVELOPE_MIN = 0.35f;

    /**
     * An envelope for equalizer band levels (in millibels, between {@code min} and {@code max}):
     * the deepest cut keeps the bars low, the highest boost lets them reach the top, and a flat
     * equalizer (0 dB) sits in between.
     */
    static float[] envelopeFromLevels(int[] levels, int min, int max) {
        final float span = Math.max(1, max - min);
        final float[] out = new float[levels.length];
        for (int i = 0; i < out.length; i++) {
            final float position = Math.max(0f, Math.min(1f, (levels[i] - min) / span));
            out[i] = ENVELOPE_MIN + (1f - ENVELOPE_MIN) * position;
        }
        return out;
    }

    /**
     * The shape of a music spectrum for {@code n} bars: low at the far left, tallest a little way in
     * (the bass), then falling away to the right (the treble). Used while the bars move on their own.
     */
    static float[] spectrumEnvelope(int n) {
        final float peakAt = 0.16f;
        final float[] out = new float[Math.max(0, n)];
        for (int i = 0; i < out.length; i++) {
            final float x = out.length == 1 ? 0f : i / (float) (out.length - 1);
            out[i] = x <= peakAt
                    ? 0.40f + 0.60f * (float) Math.sin(Math.PI / 2 * x / peakAt)
                    : 0.12f + 0.88f * (float) Math.exp(-3.4 * (x - peakAt));
        }
        return out;
    }

    /** The envelope's value at bar {@code i} of {@code n}: the envelope is stretched across the bars. */
    static float envelopeAt(float[] envelope, int i, int n) {
        if (envelope == null || envelope.length == 0) return 1f;
        if (envelope.length == 1 || n < 2) return envelope[0];
        final float position = (envelope.length - 1) * (i / (float) (n - 1));
        final int low = (int) Math.floor(position);
        final int high = Math.min(envelope.length - 1, low + 1);
        final float f = position - low;
        return envelope[low] + (envelope[high] - envelope[low]) * f;
    }

    // ---- the view ---------------------------------------------------------------------------------

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int bars;
    private final float gap;
    /** How high the bars stand when paused, compared with the usual. */
    private final float restScale;
    /** How round a bar's ends are, as a fraction of its width. */
    private final float roundness;
    /** The shape the bars' own motion takes (null: no particular shape). */
    private final float[] ownShape;

    private boolean playing;
    /** 0 = resting, 1 = moving. Eases between them while the clock runs. */
    private float moving;
    private long clock;
    private boolean motion = true;
    /** Draw this moment of the wave and ignore the clock (for pictures and tests). */
    private boolean frozen;
    private long frozenAt;
    private float[] envelope;

    static final int STYLE_BARS = 0, STYLE_DOTS = 1, STYLE_MIRROR = 2, STYLE_WAVE = 3;
    /** -1 reads the setting; the tests and pictures set it. */
    private int style = -1;
    private float[] frames = new float[0];
    private final android.graphics.Path wave = new android.graphics.Path();

    void setStyle(int s) {
        style = s;
        invalidate();
    }

    public EqBarsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.EqBarsView);
        bars = Math.max(1, a.getInt(R.styleable.EqBarsView_bars, 4));
        paint.setColor(a.getColor(R.styleable.EqBarsView_barColor, context.getColor(R.color.accent)));
        gap = Math.max(0f, a.getFloat(R.styleable.EqBarsView_barGap, GAP));
        restScale = Math.max(0f, a.getFloat(R.styleable.EqBarsView_barRest, 1f));
        roundness = Math.max(0f, Math.min(0.5f, a.getFloat(R.styleable.EqBarsView_barRadius, 0.5f)));
        ownShape = a.getBoolean(R.styleable.EqBarsView_spectrum, false) ? spectrumEnvelope(bars) : null;
        a.recycle();
        paint.setStyle(Paint.Style.FILL);
    }

    /**
     * A plain View takes all the room it is offered when it is asked to wrap its content. The bars
     * ask for just what a few bars need, so that a wrap_content parent (the cover on the Now Playing
     * screen) is sized by its other children and not stretched by these.
     */
    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        final float density = getResources().getDisplayMetrics().density;
        final int wantedWidth = Math.round(density * 3f * (bars + gap * (bars - 1)))
                + getPaddingLeft() + getPaddingRight();
        final int wantedHeight = Math.round(density * 24f) + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(resolveSize(Math.max(wantedWidth, getSuggestedMinimumWidth()), widthSpec),
                resolveSize(Math.max(wantedHeight, getSuggestedMinimumHeight()), heightSpec));
    }

    /** True while the music plays: the bars move. False: they settle. */
    void setPlaying(boolean on) {
        if (playing == on) return;
        playing = on;
        update();
    }

    boolean isPlaying() {
        return playing;
    }

    /** True while the shared clock is moving this view. */
    boolean isRunning() {
        return RUNNING.contains(this);
    }

    /**
     * Stretches {@code levels} (each 0 to 1) across the bars as the most the bars may reach, so the
     * bars can follow an equalizer curve. Null: every bar may reach the top.
     */
    void setEnvelope(float[] levels) {
        envelope = levels == null ? null : levels.clone();
        invalidate();
    }

    /** The envelope last set (a copy), or null. */
    float[] envelope() {
        return envelope == null ? null : envelope.clone();
    }

    /** Draws the wave as it is at {@code timeMs} and stops following the clock. */
    void setFrameTime(long timeMs) {
        frozen = true;
        frozenAt = timeMs;
        update();
    }

    void setBarColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    /**
     * Attached and not hidden by itself or by any parent. A screen that goes to the background hides
     * its whole window content, so this is also false for the bars of a stopped screen.
     */
    private boolean onScreen() {
        return isAttachedToWindow() && isShown();
    }

    private void update() {
        if (!onScreen() || frozen || pinned >= 0) {
            moving = playing ? 1f : 0f;   // nobody is looking: no easing, no clock
            leaveClock();
            invalidate();
            return;
        }
        if (!motion) {
            moving = playing ? 1f : 0f;   // animations are off: a still picture
            leaveClock();
            invalidate();
            return;
        }
        if (playing || moving > 0f) joinClock();
        else leaveClock();
        invalidate();
    }

    private void joinClock() {
        if (!RUNNING.contains(this)) RUNNING.add(this);
        schedule();
    }

    private void leaveClock() {
        RUNNING.remove(this);
    }

    private void step(long nowMs, long dt) {
        clock = nowMs;
        final float target = playing ? 1f : 0f;
        if (moving != target) {
            final float delta = dt / EASE_MS;
            moving = target > moving ? Math.min(target, moving + delta) : Math.max(target, moving - delta);
        }
        invalidate();
        if (!playing && moving == 0f) leaveClock();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        motion = Settings.Global.getFloat(getContext().getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
        update();
    }

    @Override
    protected void onDetachedFromWindow() {
        leaveClock();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility);
        update();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final int n = bars;
        final float w = getWidth() - getPaddingLeft() - getPaddingRight();
        final float h = getHeight() - getPaddingTop() - getPaddingBottom();
        if (w <= 0 || h <= 0) return;
        final float barWidth = w / (n + gap * (n - 1));
        final float step = barWidth * (1f + gap);
        final float shortest = Math.min(h, barWidth);   // a bar is never thinner than a dot is wide
        final long t = frozen ? frozenAt : (pinned >= 0 ? pinned : (motion ? clock : STILL_MS));
        final float radius = barWidth * roundness;
        final float bottom = getPaddingTop() + h;
        final float[] shape = envelope != null ? envelope : ownShape;
        if (frames.length != n) frames = new float[n];
        final float[] fr = frames;
        for (int i = 0; i < n; i++) {
            final float base = height(i, t, moving, restScale);
            fr[i] = Math.max(0f, Math.min(1f, base * envelopeAt(shape, i, n)));
        }
        final int look = style >= 0 ? style : Prefs.get(getContext()).barStyle();
        if (look == STYLE_WAVE) {
            drawWave(canvas, fr, w, h, bottom);
            return;
        }
        for (int i = 0; i < n; i++) {
            final float x = getPaddingLeft() + i * step;
            if (look == STYLE_DOTS) {
                final float dot = Math.max(1f, Math.min(barWidth, h / 12f));
                final int cells = Math.max(1, (int) (h / (dot * 1.5f)));
                final int lit = Math.max(1, Math.round(fr[i] * cells));
                for (int k = 0; k < lit; k++) {
                    final float y = bottom - k * dot * 1.5f;
                    rect.set(x, y - dot, x + barWidth, y);
                    canvas.drawRoundRect(rect, dot / 2f, dot / 2f, paint);
                }
            } else if (look == STYLE_MIRROR) {
                final float half = Math.max(shortest / 2f, h * fr[i] / 2f);
                final float mid = getPaddingTop() + h / 2f;
                rect.set(x, mid - half, x + barWidth, mid + half);
                canvas.drawRoundRect(rect, radius, radius, paint);
            } else {
                final float barHeight = Math.max(shortest, h * fr[i]);
                rect.set(x, bottom - barHeight, x + barWidth, bottom);
                canvas.drawRoundRect(rect, radius, radius, paint);
            }
        }
    }

    /** A filled smooth line through the levels. */
    private void drawWave(Canvas canvas, float[] fr, float w, float h, float bottom) {
        final int n = fr.length;
        final float left = getPaddingLeft();
        final float dx = n > 1 ? w / (n - 1) : w;
        wave.reset();
        wave.moveTo(left, bottom);
        wave.lineTo(left, bottom - Math.max(1f, h * fr[0]));
        for (int i = 1; i < n; i++) {
            final float x0 = left + (i - 1) * dx;
            final float x1 = left + i * dx;
            final float y0 = bottom - Math.max(1f, h * fr[i - 1]);
            final float y1 = bottom - Math.max(1f, h * fr[i]);
            wave.cubicTo((x0 + x1) / 2f, y0, (x0 + x1) / 2f, y1, x1, y1);
        }
        wave.lineTo(left + (n - 1) * dx, bottom);
        wave.close();
        canvas.drawPath(wave, paint);
    }
}
