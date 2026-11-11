package com.musicplayer.lite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/** The update card on the Settings screen, for every state the updater can be in. */
@RunWith(RobolectricTestRunner.class)
public class UpdateCardTest {
    private Context ctx;
    private View root;

    @Before
    public void inflate() {
        ctx = new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.AppTheme);
        root = LayoutInflater.from(ctx).inflate(R.layout.activity_settings, null);
    }

    private static Release release(String notes, long size) throws Exception {
        return Release.parse("{\"tag_name\":\"v1.2.0\",\"body\":" + JSONObject.quote(notes)
                + ",\"html_url\":\"https://github.com/o/r/releases/tag/v1.2.0\",\"assets\":["
                + "{\"name\":\"a.apk\",\"browser_download_url\":\"https://example.org/a.apk\",\"size\":" + size + "}]}");
    }

    private void show(Updater.State state, Release release, int percent, int errorRes, String detail, long lastCheck) {
        SettingsActivity.renderUpdate(ctx, root, new Updater.Status(state, release, "1.1.0", percent, errorRes, detail, lastCheck));
    }

    private void show(Updater.State state) {
        show(state, null, 0, 0, "", 0);
    }

    private TextView status() {
        return root.findViewById(R.id.row_version).findViewById(R.id.set_sub);
    }

    private TextView action() {
        return root.findViewById(R.id.upd_action);
    }

    private boolean visible(int id) {
        return root.findViewById(id).getVisibility() == View.VISIBLE;
    }

    private String text(TextView v) {
        return v.getText().toString();
    }

    @Test
    public void neverCheckedOffersToCheck() {
        show(Updater.State.IDLE);
        assertEquals(ctx.getString(R.string.upd_never), text(status()));
        assertEquals(ctx.getString(R.string.upd_check), text(action()));
        assertTrue(action().isEnabled());
        assertEquals("Version 1.1.0", text(root.findViewById(R.id.row_version).findViewById(R.id.set_title)));
        assertFalse(visible(R.id.upd_progress));
        assertFalse(visible(R.id.upd_hint));
        assertFalse(visible(R.id.upd_notes));
        assertFalse(visible(R.id.upd_secondary));
    }

    @Test
    public void checkingDisablesTheButton() {
        show(Updater.State.CHECKING);
        assertEquals(ctx.getString(R.string.upd_checking), text(status()));
        assertFalse(action().isEnabled());
        assertEquals(0.5f, action().getAlpha(), 0f);
    }

    @Test
    public void upToDateSaysWhenItWasChecked() {
        show(Updater.State.UP_TO_DATE, null, 0, 0, "", System.currentTimeMillis() - 5 * 60_000L);
        String s = text(status());
        assertTrue(s, s.startsWith(ctx.getString(R.string.upd_up_to_date)));
        assertTrue(s, s.contains("Last checked") && s.contains("5 minutes ago"));
        assertEquals(ctx.getString(R.string.upd_check), text(action()));
        assertTrue(action().isEnabled());
        assertEquals(1f, action().getAlpha(), 0f);
    }

    @Test
    public void nothingPublishedYet() {
        show(Updater.State.NONE_PUBLISHED);
        assertEquals(ctx.getString(R.string.upd_none), text(status()));
    }

    @Test
    public void anAvailableUpdateShowsItsNotesAsPlainText() throws Exception {
        show(Updater.State.AVAILABLE, release("## What's new\n- **Bold** and `code`\n", 1_300_000), 0, 0, "", 0);
        assertEquals("Version 1.2.0 is available (1.2 MB)", text(status()));
        assertEquals(ctx.getString(R.string.upd_download), text(action()));
        assertTrue(visible(R.id.upd_notes_title));
        assertTrue(visible(R.id.upd_notes));
        assertEquals("What's new\n- Bold and code", text(root.findViewById(R.id.upd_notes)));
        assertTrue(visible(R.id.upd_secondary));
        assertEquals(ctx.getString(R.string.upd_view), text(root.findViewById(R.id.upd_secondary)));
    }

    @Test
    public void noNotesMeansNoNotesBlock() throws Exception {
        show(Updater.State.AVAILABLE, release("   ", 0), 0, 0, "", 0);
        assertEquals("Version 1.2.0 is available", text(status()));
        assertFalse(visible(R.id.upd_notes_title));
        assertFalse(visible(R.id.upd_notes));
        assertTrue("the GitHub link is still offered", visible(R.id.upd_secondary));
    }

    @Test
    public void downloadingShowsProgressAndCancel() throws Exception {
        show(Updater.State.DOWNLOADING, release("n", 10), 62, 0, "", 0);
        assertEquals("Downloading… 62%", text(status()));
        ProgressBar bar = root.findViewById(R.id.upd_progress);
        assertEquals(View.VISIBLE, bar.getVisibility());
        assertFalse(bar.isIndeterminate());
        assertEquals(62, bar.getProgress());
        assertEquals(ctx.getString(R.string.upd_cancel), text(action()));
        assertTrue(action().isEnabled());
    }

    @Test
    public void verifyingShowsAnIndeterminateBarAndIgnoresTaps() throws Exception {
        show(Updater.State.VERIFYING, release("n", 10), 100, 0, "", 0);
        ProgressBar bar = root.findViewById(R.id.upd_progress);
        assertEquals(View.VISIBLE, bar.getVisibility());
        assertTrue(bar.isIndeterminate());
        assertFalse(action().isEnabled());
    }

    @Test
    public void readyOffersInstall() throws Exception {
        show(Updater.State.READY, release("n", 10), 100, 0, "", 0);
        assertEquals(ctx.getString(R.string.upd_ready), text(status()));
        assertEquals(ctx.getString(R.string.upd_install), text(action()));
        assertTrue(action().isEnabled());
        assertFalse(visible(R.id.upd_progress));
    }

    @Test
    public void missingPermissionExplainsWhatToDo() throws Exception {
        show(Updater.State.NEEDS_PERMISSION, release("n", 10), 100, 0, "", 0);
        assertEquals(ctx.getString(R.string.upd_allow_title), text(status()));
        assertTrue(visible(R.id.upd_hint));
        assertEquals(ctx.getString(R.string.upd_allow_body), text(root.findViewById(R.id.upd_hint)));
        assertEquals(ctx.getString(R.string.upd_allow_action), text(action()));
    }

    @Test
    public void installingWaitsForAndroid() throws Exception {
        show(Updater.State.INSTALLING, release("n", 10), 100, 0, "", 0);
        assertEquals(ctx.getString(R.string.upd_installing), text(status()));
        assertFalse(action().isEnabled());
    }

    @Test
    public void errorsExplainThemselvesAndOfferRetry() throws Exception {
        show(Updater.State.ERROR, null, 0, R.string.upd_err_network, "", 0);
        assertEquals(ctx.getString(R.string.upd_err_network), text(status()));
        assertEquals(ctx.getString(R.string.upd_retry), text(action()));
        assertFalse("no release is known, so no GitHub link", visible(R.id.upd_secondary));

        show(Updater.State.ERROR, release("n", 10), 0, R.string.upd_err_install, "STATUS_FAILURE_CONFLICT", 0);
        assertEquals("Android could not install the update (STATUS_FAILURE_CONFLICT).", text(status()));
        assertTrue("a release is known, so its page can be opened", visible(R.id.upd_secondary));
    }

    @Test
    public void leavingAStateLeavesNothingBehind() throws Exception {
        // Everything the busiest states switch on must switch off again in a calm state.
        show(Updater.State.AVAILABLE, release("## Notes\n- one", 10), 0, 0, "", 0);
        show(Updater.State.DOWNLOADING, release("## Notes\n- one", 10), 40, 0, "", 0);
        show(Updater.State.NEEDS_PERMISSION, release("## Notes\n- one", 10), 100, 0, "", 0);
        show(Updater.State.UP_TO_DATE, null, 0, 0, "", System.currentTimeMillis());
        assertFalse(visible(R.id.upd_notes_title));
        assertFalse(visible(R.id.upd_notes));
        assertFalse(visible(R.id.upd_progress));
        assertFalse(visible(R.id.upd_hint));
        assertFalse(visible(R.id.upd_secondary));
        assertTrue(action().isEnabled());
        assertEquals(1f, action().getAlpha(), 0f);
    }
}
