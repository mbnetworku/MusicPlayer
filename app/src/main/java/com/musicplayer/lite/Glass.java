package com.musicplayer.lite;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;

/**
 * Turns the plain layouts into the liquid-glass look. Views opt in with a tag such as
 * {@code android:tag="glass:card,margin"}; when glass is off the tags do nothing.
 *
 * <ul>
 *   <li>{@code card}: a rounded glass panel (children are clipped to it)</li>
 *   <li>{@code pill}: a fully rounded glass capsule</li>
 *   <li>{@code accent-pill}: an amber-tinted glass capsule</li>
 *   <li>{@code tabs}: a glass capsule whose children become glass segments</li>
 *   <li>{@code margin}: float the view away from the screen edges</li>
 *   <li>{@code inset}: horizontal breathing room only</li>
 *   <li>{@code lift}: a little space below, so a thin bar clears the rounded corners</li>
 *   <li>{@code padded}: padding inside a panel</li>
 *   <li>{@code clear}: remove the flat background</li>
 *   <li>{@code hide}: not needed in the glass look</li>
 * </ul>
 */
final class Glass {
    private static final String PREFIX = "glass:";

    private Glass() {}

    static void decorate(View root) {
        Object tag = root.getTag();
        if (tag instanceof String && ((String) tag).startsWith(PREFIX)) {
            apply(root, ((String) tag).substring(PREFIX.length()).split(","));
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) decorate(group.getChildAt(i));
        }
    }

    private static void apply(View v, String[] tokens) {
        final Context c = v.getContext();
        final float d = c.getResources().getDisplayMetrics().density;
        for (String raw : tokens) {
            switch (raw.trim()) {
                case "hide":
                    v.setVisibility(View.GONE);
                    break;
                case "clear":
                    v.setBackground(null);
                    break;
                case "card":
                    surface(v, GlassDrawable.CARD, false);
                    break;
                case "pill":
                    surface(v, GlassDrawable.PILL, false);
                    break;
                case "accent-pill":
                    surface(v, GlassDrawable.PILL, true);
                    break;
                case "tabs":
                    surface(v, GlassDrawable.PILL, false);
                    int pad = Math.round(4 * d);
                    v.setPadding(pad, pad, pad, pad);
                    if (v instanceof ViewGroup) {
                        ViewGroup group = (ViewGroup) v;
                        for (int i = 0; i < group.getChildCount(); i++) group.getChildAt(i).setBackground(segment(c));
                    }
                    break;
                case "margin":
                    addMargins(v, Math.round(12 * d), Math.round(6 * d));
                    break;
                case "inset":
                    addMargins(v, Math.round(16 * d), 0);
                    break;
                case "lift":
                    addMargins(v, 0, 0);
                    ((ViewGroup.MarginLayoutParams) v.getLayoutParams()).bottomMargin += Math.round(8 * d);
                    break;
                case "padded":
                    int p = Math.round(12 * d);
                    v.setPadding(v.getPaddingLeft() + p, v.getPaddingTop() + p,
                            v.getPaddingRight() + p, v.getPaddingBottom() + p);
                    break;
                default:
                    break;
            }
        }
    }

    private static void surface(View v, int shape, boolean accent) {
        v.setBackground(GlassDrawable.create(v.getContext(), shape, accent, false));
        v.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        v.setClipToOutline(true);
    }

    private static void addMargins(View v, int horizontal, int vertical) {
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (!(lp instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) lp;
        m.leftMargin += horizontal;
        m.rightMargin += horizontal;
        m.topMargin += vertical;
        m.bottomMargin += vertical;
        v.setLayoutParams(m);
    }

    // ---- backgrounds for views that are created in code --------------------------------------

    /** A glass capsule for buttons and the search field. */
    static Drawable pill(Context c, boolean accent) {
        return GlassDrawable.create(c, GlassDrawable.PILL, accent, false);
    }

    /** A chip: clear glass, and amber glass while selected. */
    static Drawable chip(Context c) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_selected}, GlassDrawable.create(c, GlassDrawable.PILL, true, false));
        s.addState(new int[]{}, GlassDrawable.create(c, GlassDrawable.PILL, false, false));
        return s;
    }

    /** One segment of the tab bar: transparent, brighter glass when selected. */
    static Drawable segment(Context c) {
        StateListDrawable s = new StateListDrawable();
        if (Design.isNew(c)) {
            // New design: the chosen tab sits in a rounded pill, darker than the bar on black, softer on white.
            GradientDrawable chosen = new GradientDrawable();
            chosen.setColor(Appearance.isNight(c) ? 0x66000000 : 0x14000000);
            chosen.setCornerRadius(200f * c.getResources().getDisplayMetrics().density);
            s.addState(new int[]{android.R.attr.state_selected}, chosen);
            s.addState(new int[]{android.R.attr.state_pressed}, GlassDrawable.create(c, GlassDrawable.PILL, false, false));
            s.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
            return s;
        }
        s.addState(new int[]{android.R.attr.state_selected}, GlassDrawable.create(c, GlassDrawable.PILL, false, true));
        s.addState(new int[]{android.R.attr.state_pressed}, GlassDrawable.create(c, GlassDrawable.PILL, false, false));
        s.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        return s;
    }
}
