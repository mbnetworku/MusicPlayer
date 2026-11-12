package com.musicplayer.lite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RadioGroup;
import android.widget.RemoteViews;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowAppWidgetManager;

/** The home-screen widget and the screen where its style is chosen. */
@RunWith(RobolectricTestRunner.class)
public class WidgetTest {
    private Application app;
    private Prefs prefs;

    @Before
    public void setUp() {
        TestMedia.resetSingletons();
        app = RuntimeEnvironment.getApplication();
        prefs = Prefs.get(app);
    }

    private View inflate(RemoteViews views) {
        return views.apply(app, new FrameLayout(app));
    }

    @Test
    public void eachStyleHasItsOwnLayout() {
        assertEquals(R.layout.widget_player, PlayerWidget.layoutFor(Appearance.FOLLOW_DEVICE));
        assertEquals(R.layout.widget_player_light, PlayerWidget.layoutFor(Appearance.LIGHT));
        assertEquals(R.layout.widget_player_dark, PlayerWidget.layoutFor(Appearance.DARK));
    }

    @Test
    public void aWidgetShowsTheSongAndFollowsPlayPause() {
        Track t = new Track(1, "Midnight Drive", "Neon Harbor", "Afterglow", 10, 1000, 1, "/m", "a.mp3");
        PlayerWidget.publish(app, t, true, Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888));
        View v = inflate(PlayerWidget.build(app, 77));
        assertEquals("Midnight Drive", ((TextView) v.findViewById(R.id.w_title)).getText().toString());
        assertEquals("Neon Harbor", ((TextView) v.findViewById(R.id.w_artist)).getText().toString());
        assertEquals("while playing the button offers to pause", app.getString(R.string.cd_pause),
                v.findViewById(R.id.w_play).getContentDescription().toString());

        PlayerWidget.publish(app, t, false, null);
        View paused = inflate(PlayerWidget.build(app, 77));
        assertEquals(app.getString(R.string.cd_play), paused.findViewById(R.id.w_play).getContentDescription().toString());
    }

    @Test
    public void withoutAnySongItInvitesYouToOpenTheApp() {
        prefs.setNowPlayingText("", "");
        View v = inflate(PlayerWidget.build(app, 78));
        assertEquals(app.getString(R.string.nothing_playing), ((TextView) v.findViewById(R.id.w_title)).getText().toString());
        assertEquals(app.getString(R.string.widget_tap_to_open), ((TextView) v.findViewById(R.id.w_artist)).getText().toString());
    }

    @Test
    public void everyButtonHasSomethingToDoWhenTapped() {
        RemoteViews views = PlayerWidget.build(app, 79);
        View v = inflate(views);
        for (int id : new int[]{R.id.w_play, R.id.w_next, R.id.w_prev, R.id.w_root}) {
            assertTrue("button " + id + " is wired", v.findViewById(id).hasOnClickListeners());
        }
    }

    @Test
    public void everyWidgetOnTheHomeScreenIsRefreshedWhenPlaybackChanges() {
        ShadowAppWidgetManager manager = shadowOf(AppWidgetManager.getInstance(app));
        int id = manager.createWidget(PlayerWidget.class, R.layout.widget_player);
        Track t = new Track(2, "Glass Cities", "Neon Harbor", "Afterglow", 10, 1000, 2, "/m", "b.mp3");
        PlayerWidget.publish(app, t, true, null);
        View view = manager.getViewFor(id);
        assertNotNull(view);
        assertEquals("Glass Cities", ((TextView) view.findViewById(R.id.w_title)).getText().toString());
    }

    @Test
    public void removingAWidgetForgetsItsStyle() {
        prefs.setWidgetStyle(5, Appearance.DARK);
        assertEquals(Appearance.DARK, prefs.widgetStyle(5));
        new PlayerWidget().onDeleted(app, new int[]{5});
        assertEquals("back to the default", Appearance.FOLLOW_DEVICE, prefs.widgetStyle(5));
    }

    @Test
    public void thePlaybackServiceKeepsTheWidgetCurrent() throws Exception {
        TestMedia.install(app);
        FakePlayback.register();
        Library library = Library.get(app);
        library.refresh();
        TestMedia.awaitLibrary(library);
        PlaybackService service = Robolectric.buildService(PlaybackService.class).create().get();
        TestMedia.exposeService(app, service);
        service.playQueue(new java.util.ArrayList<>(library.tracks()), 0, false);
        TestMedia.idle();
        assertEquals("Ember", prefs.nowPlayingTitle());
        assertEquals("Okoro", prefs.nowPlayingArtist());
    }

    @Test
    public void choosingAStyleSavesItAndAnswersOk() {
        final int widget = shadowOf(AppWidgetManager.getInstance(app)).createWidget(PlayerWidget.class, R.layout.widget_player);
        Intent intent = new Intent(app, WidgetConfigActivity.class)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widget);
        ActivityController<WidgetConfigActivity> c = Robolectric.buildActivity(WidgetConfigActivity.class, intent)
                .create().start().resume().visible();
        RadioGroup group = c.get().findViewById(R.id.widget_style);
        assertEquals("follows the device by default", R.id.widget_follow, group.getCheckedRadioButtonId());

        group.check(R.id.widget_dark);
        c.get().findViewById(R.id.widget_save).performClick();
        assertEquals(Appearance.DARK, prefs.widgetStyle(widget));
        assertEquals(android.app.Activity.RESULT_OK, shadowOf(c.get()).getResultCode());
        assertEquals(widget, shadowOf(c.get()).getResultIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1));
        assertTrue(c.get().isFinishing());
    }

    @Test
    public void leavingWithoutSavingCancelsAddingTheWidget() {
        Intent intent = new Intent(app, WidgetConfigActivity.class)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 43);
        ActivityController<WidgetConfigActivity> c = Robolectric.buildActivity(WidgetConfigActivity.class, intent)
                .create().start().resume().visible();
        assertEquals(android.app.Activity.RESULT_CANCELED, shadowOf(c.get()).getResultCode());
        c.get().findViewById(R.id.btn_back).performClick();
        assertTrue(c.get().isFinishing());
        assertEquals(Appearance.FOLLOW_DEVICE, prefs.widgetStyle(43));
    }

    @Test
    public void aLaunchWithoutAWidgetIdJustCloses() {
        ActivityController<WidgetConfigActivity> c = Robolectric.buildActivity(WidgetConfigActivity.class)
                .create().start().resume();
        assertTrue(c.get().isFinishing());
    }
}
