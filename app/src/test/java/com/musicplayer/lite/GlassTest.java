package com.musicplayer.lite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
public class GlassTest {
    private Application app;
    private Context ctx;
    private float density;

    @Before
    public void setUp() {
        TestMedia.resetSingletons();
        app = RuntimeEnvironment.getApplication();
        ctx = new ContextThemeWrapper(app, R.style.AppTheme);
        density = ctx.getResources().getDisplayMetrics().density;
    }

    private View tagged(LinearLayout parent, String tag) {
        View v = new View(ctx);
        v.setTag(tag);
        v.setBackgroundColor(0xFF123456);
        parent.addView(v, new LinearLayout.LayoutParams(100, 100));
        return v;
    }

    @Test
    public void taggedViewsBecomeGlass() {
        LinearLayout root = new LinearLayout(ctx);
        View card = tagged(root, "glass:card,margin");
        View pill = tagged(root, "glass:pill");
        View accent = tagged(root, "glass:accent-pill");
        View hide = tagged(root, "glass:hide");
        View clear = tagged(root, "glass:clear");
        Glass.decorate(root);

        assertTrue(card.getBackground() instanceof GlassDrawable);
        assertTrue(pill.getBackground() instanceof GlassDrawable);
        assertTrue(accent.getBackground() instanceof GlassDrawable);
        assertEquals(View.GONE, hide.getVisibility());
        assertNull(clear.getBackground());
        assertTrue("glass panels clip their children to the rounded shape", card.getClipToOutline());
    }

    @Test
    public void marginsAndPaddingAreAdded() {
        LinearLayout root = new LinearLayout(ctx);
        View margin = tagged(root, "glass:card,margin");
        View inset = tagged(root, "glass:inset,lift");
        View padded = tagged(root, "glass:card,padded");
        Glass.decorate(root);

        LinearLayout.LayoutParams m = (LinearLayout.LayoutParams) margin.getLayoutParams();
        assertEquals(Math.round(12 * density), m.leftMargin);
        assertEquals(Math.round(12 * density), m.rightMargin);
        assertEquals(Math.round(6 * density), m.topMargin);
        LinearLayout.LayoutParams i = (LinearLayout.LayoutParams) inset.getLayoutParams();
        assertEquals(Math.round(16 * density), i.leftMargin);
        assertEquals(Math.round(8 * density), i.bottomMargin);
        assertEquals(Math.round(12 * density), padded.getPaddingLeft());
    }

    @Test
    public void tabsBecomeASegmentedGlassControl() {
        LinearLayout tabs = new LinearLayout(ctx);
        tabs.setTag("glass:tabs");
        for (int i = 0; i < 4; i++) tabs.addView(new TextView(ctx));
        Glass.decorate(tabs);

        assertTrue(tabs.getBackground() instanceof GlassDrawable);
        for (int i = 0; i < 4; i++) assertTrue(tabs.getChildAt(i).getBackground() instanceof StateListDrawable);
        tabs.getChildAt(1).setSelected(true);
        tabs.getChildAt(1).getBackground().setState(new int[]{android.R.attr.state_selected});
        Drawable current = tabs.getChildAt(1).getBackground().getCurrent();
        assertTrue("the selected tab is a glass segment", current instanceof GlassDrawable);
        Drawable idle = tabs.getChildAt(2).getBackground().getCurrent();
        assertTrue("the others are see-through", idle instanceof ColorDrawable);
    }

    @Test
    public void viewsWithoutAGlassTagAreLeftAlone() {
        LinearLayout root = new LinearLayout(ctx);
        View plain = new View(ctx);
        plain.setBackgroundColor(0xFF654321);
        root.addView(plain, new LinearLayout.LayoutParams(50, 50));
        View other = tagged(root, "something else");
        Drawable before = plain.getBackground();
        Drawable otherBefore = other.getBackground();
        Glass.decorate(root);
        assertSame(before, plain.getBackground());
        assertSame(otherBefore, other.getBackground());
    }

    @Test
    public void glassHasARoundedOutlineAndIsTranslucent() {
        GlassDrawable g = GlassDrawable.create(ctx, GlassDrawable.CARD, false, false);
        g.setBounds(0, 0, 400, 200);
        Outline outline = new Outline();
        g.getOutline(outline);
        assertFalse(outline.isEmpty());
        assertEquals(PixelFormat.TRANSLUCENT, g.getOpacity());

        // Every variant draws without trouble, including at a tiny size and with an alpha.
        Canvas canvas = new Canvas(Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888));
        for (int shape : new int[]{GlassDrawable.CARD, GlassDrawable.PILL}) {
            for (boolean accent : new boolean[]{false, true}) {
                for (boolean strong : new boolean[]{false, true}) {
                    GlassDrawable d = GlassDrawable.create(ctx, shape, accent, strong);
                    d.setBounds(0, 0, 3, 3);
                    d.draw(canvas);
                    d.setBounds(0, 0, 400, 200);
                    d.setAlpha(128);
                    d.draw(canvas);
                }
            }
        }
        assertNotNull(g.getConstantState().newDrawable());
    }

    @Test
    public void theBackdropBlursArtToATinyBitmapAndDraws() {
        Bitmap art = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        Bitmap blurred = BackdropDrawable.blur(art);
        assertEquals(24, blurred.getWidth());
        assertEquals(24, blurred.getHeight());

        BackdropDrawable backdrop = new BackdropDrawable(ctx);
        backdrop.setBounds(0, 0, 1080, 2000);
        Canvas canvas = new Canvas(Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888));
        backdrop.draw(canvas);                       // the aurora
        backdrop.setArt(art, "album-1", false);
        backdrop.draw(canvas);                       // blurred art
        backdrop.setArt(null, "album-2", false);
        backdrop.draw(canvas);                       // colours from a cover without art
        backdrop.setArt(null, "", false);
        backdrop.draw(canvas);                       // back to the aurora
        assertEquals(PixelFormat.OPAQUE, backdrop.getOpacity());
    }

    private static float luma(int c) {
        return (0.2126f * Color.red(c) + 0.7152f * Color.green(c) + 0.0722f * Color.blue(c)) / 255f;
    }

    @Test
    public void aBrightCoverIsDarkenedAtNightButKeepsItsHue() {
        int sunny = 0xFFFFC46B;
        int toned = BackdropDrawable.toneColor(sunny, true);
        assertEquals(BackdropDrawable.NIGHT_MAX_LUMA, luma(toned), 0.01f);
        // Same colour, only darker: the channels keep their proportions.
        assertEquals(Color.green(sunny) / (float) Color.red(sunny),
                Color.green(toned) / (float) Color.red(toned), 0.02f);
        assertEquals(Color.blue(sunny) / (float) Color.red(sunny),
                Color.blue(toned) / (float) Color.red(toned), 0.03f);
    }

    @Test
    public void aDarkCoverIsLightenedByDayButStaysTinted() {
        int navy = 0xFF1B1446;
        int toned = BackdropDrawable.toneColor(navy, false);
        assertEquals(BackdropDrawable.DAY_MIN_LUMA, luma(toned), 0.01f);
        assertTrue("still tinted by the cover, not plain white", Color.red(toned) < Color.blue(toned));
    }

    @Test
    public void coversAlreadyInsideTheRangeAreLeftAlone() {
        assertEquals(0xFF281E3C, BackdropDrawable.toneColor(0xFF281E3C, true));
        assertEquals(0xFFF0E6FA, BackdropDrawable.toneColor(0xFFF0E6FA, false));
        assertEquals("black stays black at night", 0xFF000000, BackdropDrawable.toneColor(0xFF000000, true));
    }

    @Test
    public void everyPixelOfABlurredCoverIsToned() {
        Bitmap white = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
        white.eraseColor(0xFFFFFFFF);
        Bitmap night = BackdropDrawable.tone(white, true);
        Bitmap day = BackdropDrawable.tone(white, false);
        assertEquals(BackdropDrawable.NIGHT_MAX_LUMA, luma(night.getPixel(0, 0)), 0.01f);
        assertEquals(BackdropDrawable.NIGHT_MAX_LUMA, luma(night.getPixel(23, 23)), 0.01f);
        assertEquals("white is already light enough by day", 0xFFFFFFFF, day.getPixel(12, 12));
    }

    @Test
    @Config(qualifiers = "night")
    public void theNightBackdropShowsABrightCoverDarkened() {
        Bitmap sunny = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        sunny.eraseColor(0xFFFFD27A);
        BackdropDrawable backdrop = new BackdropDrawable(ctx);
        backdrop.setArt(sunny, "", false);
        assertNotNull(backdrop.shown());
        assertEquals(BackdropDrawable.NIGHT_MAX_LUMA, luma(backdrop.shown().getPixel(12, 12)), 0.02f);
        backdrop.setArt(null, "", false);
        assertNull("no art and no key shows the aurora", backdrop.shown());
    }

    @Test
    public void theDayBackdropShowsADarkCoverLightened() {
        Bitmap navy = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        navy.eraseColor(0xFF1B1446);
        BackdropDrawable backdrop = new BackdropDrawable(ctx);
        backdrop.setArt(navy, "", false);
        assertNotNull(backdrop.shown());
        assertEquals(BackdropDrawable.DAY_MIN_LUMA, luma(backdrop.shown().getPixel(12, 12)), 0.02f);
    }
}
