package com.musicplayer.lite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowDialog;

import java.util.concurrent.atomic.AtomicInteger;

/** The liquid-glass pop-ups: permission questions, confirmations and action sheets. */
@RunWith(RobolectricTestRunner.class)
public class GlassDialogTest {
    private Activity activity;

    @Before
    public void setUp() {
        TestMedia.resetSingletons();
        activity = Robolectric.buildActivity(SettingsActivity.class).create().start().resume().visible().get();
    }

    @Test
    public void anAlertShowsItsTextAndRunsTheChosenButton() {
        final AtomicInteger allowed = new AtomicInteger();
        final AtomicInteger denied = new AtomicInteger();
        GlassDialog d = GlassDialog.alert(activity).icon(R.drawable.ic_music_note)
                .title(R.string.perm_music_title).message(R.string.perm_music_body)
                .positive(R.string.perm_allow, allowed::incrementAndGet)
                .negative(R.string.perm_deny, denied::incrementAndGet).show();
        assertTrue(d.isShowing());
        assertTrue(ShadowDialog.getLatestDialog().isShowing());

        View root = d.contentView();
        assertEquals("Allow access to your music?", ((TextView) root.findViewById(R.id.gd_title)).getText().toString());
        assertEquals("Allow", ((TextView) root.findViewById(R.id.gd_positive)).getText().toString());
        assertEquals("Not allowed", ((TextView) root.findViewById(R.id.gd_negative)).getText().toString());
        assertTrue("the card is glass", root.findViewById(R.id.gd_card).getBackground() instanceof GlassDrawable);

        root.findViewById(R.id.gd_negative).performClick();
        assertEquals(1, denied.get());
        assertEquals(0, allowed.get());
        assertFalse("the dialog closes", d.isShowing());
    }

    @Test
    public void allowRunsTheAllowAction() {
        final AtomicInteger allowed = new AtomicInteger();
        GlassDialog d = GlassDialog.alert(activity).title(R.string.perm_music_title)
                .positive(R.string.perm_allow, allowed::incrementAndGet)
                .negative(R.string.perm_deny, null).show();
        d.contentView().findViewById(R.id.gd_positive).performClick();
        assertEquals(1, allowed.get());
        assertFalse(d.isShowing());
    }

    @Test
    public void anAlertWithoutAMessageOrIconHidesThem() {
        GlassDialog d = GlassDialog.alert(activity).title("Only a title").positive(R.string.perm_allow, null).show();
        assertEquals(View.GONE, d.contentView().findViewById(R.id.gd_message).getVisibility());
        assertEquals(View.GONE, d.contentView().findViewById(R.id.gd_icon).getVisibility());
        assertEquals("one button only", View.GONE, d.contentView().findViewById(R.id.gd_negative).getVisibility());
    }

    @Test
    public void aSheetListsItsActionsAndRedMarksTheDestructiveOne() {
        final AtomicInteger deleted = new AtomicInteger();
        final AtomicInteger queued = new AtomicInteger();
        GlassDialog d = GlassDialog.sheet(activity).title("Glass Cities")
                .item(R.drawable.ic_playlist_add, R.string.action_add_queue, queued::incrementAndGet)
                .destructiveItem(R.drawable.ic_delete, R.string.action_delete, deleted::incrementAndGet).show();
        LinearLayout items = d.contentView().findViewById(R.id.gd_items);
        assertEquals(2, items.getChildCount());
        TextView normal = items.getChildAt(0).findViewById(R.id.gd_item_label);
        TextView danger = items.getChildAt(1).findViewById(R.id.gd_item_label);
        assertEquals(activity.getColor(R.color.text_primary), normal.getCurrentTextColor());
        assertEquals("destructive rows are red", activity.getColor(R.color.accent_text), danger.getCurrentTextColor());

        items.getChildAt(1).performClick();
        assertEquals(1, deleted.get());
        assertEquals(0, queued.get());
        assertFalse(d.isShowing());
    }

    @Test
    public void aSheetHasACancelButtonThatJustCloses() {
        GlassDialog d = GlassDialog.sheet(activity).item(R.drawable.ic_info, R.string.action_info, null).show();
        TextView cancel = d.contentView().findViewById(R.id.gd_negative);
        assertEquals("Cancel", cancel.getText().toString());
        cancel.performClick();
        assertFalse(d.isShowing());
    }

    @Test
    public void theViewCanBeBuiltWithoutShowingIt() {
        GlassDialog d = GlassDialog.alert(activity).title("t").positive(R.string.perm_allow, null);
        ViewGroup content = (ViewGroup) d.build();
        assertFalse(d.isShowing());
        assertEquals("t", ((TextView) content.findViewById(R.id.gd_title)).getText().toString());
    }
}
