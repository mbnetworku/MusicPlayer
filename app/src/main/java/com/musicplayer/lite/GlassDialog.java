package com.musicplayer.lite;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Liquid-glass dialogs: a centred alert (used for permission questions and confirmations) and
 * a bottom action sheet (the "more" menus). They are always glass, whatever the look setting,
 * and on Android 12 and newer the screen behind them is blurred.
 */
final class GlassDialog {
    private static final class Item {
        final int icon;
        final CharSequence label;
        final boolean destructive;
        final Runnable action;

        Item(int icon, CharSequence label, boolean destructive, Runnable action) {
            this.icon = icon;
            this.label = label;
            this.destructive = destructive;
            this.action = action;
        }
    }

    private final Context context;
    private final boolean sheet;
    private Dialog dialog;
    private final List<Item> items = new ArrayList<>();
    private int icon;
    private CharSequence title;
    private CharSequence message;
    private CharSequence positiveLabel;
    private CharSequence negativeLabel;
    private Runnable positive;
    private Runnable negative;
    private View root;

    private GlassDialog(Context context, boolean sheet) {
        this.context = context;
        this.sheet = sheet;
    }

    /** A centred alert: icon, title, message and up to two buttons. */
    static GlassDialog alert(Context c) {
        return new GlassDialog(c, false);
    }

    /** A list of actions at the bottom of the screen, with a Cancel button. */
    static GlassDialog sheet(Context c) {
        return new GlassDialog(c, true);
    }

    GlassDialog icon(int drawable) {
        icon = drawable;
        return this;
    }

    GlassDialog title(int res) {
        return title(context.getString(res));
    }

    GlassDialog title(CharSequence text) {
        title = text;
        return this;
    }

    GlassDialog message(int res) {
        return message(context.getString(res));
    }

    GlassDialog message(CharSequence text) {
        message = text;
        return this;
    }

    /** The main button. Runs {@code action} after the dialog closes. */
    GlassDialog positive(int res, Runnable action) {
        positiveLabel = context.getString(res);
        positive = action;
        return this;
    }

    /** The other button (an alert) or Cancel (a sheet). {@code action} may be null. */
    GlassDialog negative(int res, Runnable action) {
        negativeLabel = context.getString(res);
        negative = action;
        return this;
    }

    /** One row of a sheet. */
    GlassDialog item(int iconRes, int labelRes, Runnable action) {
        items.add(new Item(iconRes, context.getString(labelRes), false, action));
        return this;
    }

    /** A row of a sheet for something that cannot be undone: shown in red. */
    GlassDialog destructiveItem(int iconRes, int labelRes, Runnable action) {
        items.add(new Item(iconRes, context.getString(labelRes), true, action));
        return this;
    }

    /** Builds the content without showing it, so it can also be drawn on its own. */
    View build() {
        root = LayoutInflater.from(context).inflate(sheet ? R.layout.dialog_sheet : R.layout.dialog_glass, null);
        View card = root.findViewById(R.id.gd_card);
        card.setBackground(GlassDrawable.dialog(context));
        card.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        card.setClipToOutline(true);

        TextView titleView = root.findViewById(R.id.gd_title);
        setText(titleView, title);
        TextView negativeView = root.findViewById(R.id.gd_negative);
        if (sheet) {
            fillSheet();
            negativeView.setBackground(GlassDrawable.dialog(context));
            negativeView.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            negativeView.setClipToOutline(true);
            negativeView.setText(negativeLabel != null ? negativeLabel : context.getString(R.string.dialog_cancel));
            negativeView.setOnClickListener(v -> finish(negative));
        } else {
            ImageView iconView = root.findViewById(R.id.gd_icon);
            if (icon != 0) iconView.setImageResource(icon);
            else iconView.setVisibility(View.GONE);
            setText(root.findViewById(R.id.gd_message), message);
            TextView positiveView = root.findViewById(R.id.gd_positive);
            if (positiveLabel != null) {
                positiveView.setBackground(Glass.pill(context, true));
                positiveView.setText(positiveLabel);
                positiveView.setOnClickListener(v -> finish(positive));
            } else {
                positiveView.setVisibility(View.GONE);
            }
            if (negativeLabel != null) {
                negativeView.setText(negativeLabel);
                negativeView.setOnClickListener(v -> finish(negative));
            } else {
                negativeView.setVisibility(View.GONE);
            }
        }

        return root;
    }

    GlassDialog show() {
        View content = build();
        dialog = new Dialog(context, R.style.GlassDialogTheme);
        dialog.setContentView(content);
        style(dialog.getWindow());
        dialog.show();
        return this;
    }

    void dismiss() {
        if (dialog != null) dialog.dismiss();
    }

    boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }

    /** The decor view of the open dialog, so tests can look at it. */
    View contentView() {
        return root;
    }

    private void fillSheet() {
        LinearLayout list = root.findViewById(R.id.gd_items);
        LayoutInflater inflater = LayoutInflater.from(context);
        for (Item item : items) {
            View row = inflater.inflate(R.layout.row_sheet_item, list, false);
            ImageView icon = row.findViewById(R.id.gd_item_icon);
            TextView label = row.findViewById(R.id.gd_item_label);
            label.setText(item.label);
            final int color = context.getColor(item.destructive ? R.color.accent_text : R.color.text_primary);
            label.setTextColor(color);
            if (item.icon != 0) {
                icon.setImageResource(item.icon);
                icon.setColorFilter(color);
            }
            final Runnable action = item.action;
            row.setOnClickListener(v -> finish(action));
            list.addView(row);
        }
    }

    private void finish(Runnable action) {
        dismiss();
        if (action != null) action.run();
    }

    private static void setText(TextView view, CharSequence text) {
        boolean has = text != null && text.length() > 0;
        view.setText(has ? text : "");
        view.setVisibility(has ? View.VISIBLE : View.GONE);
    }

    private void style(Window window) {
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        final int maxWidth = Math.round(context.getResources().getDisplayMetrics().density * (sheet ? 520 : 440));
        final int available = context.getResources().getDisplayMetrics().widthPixels;
        window.setLayout(Math.min(available, maxWidth), ViewGroup.LayoutParams.WRAP_CONTENT);
        window.setGravity(sheet ? Gravity.BOTTOM : Gravity.CENTER);
        if (Build.VERSION.SDK_INT >= 31) {
            // Blur what is behind the dialog, where the device allows it.
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.setBlurBehindRadius(Math.round(context.getResources().getDisplayMetrics().density * 16));
            window.setAttributes(params);
        }
    }
}
