package com.musicplayer.lite;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/** Lays children out left to right and wraps to the next line when a row is full. */
public class FlowLayout extends ViewGroup {

    public FlowLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        final int maxWidth = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
        int x = 0;
        int rowHeight = 0;
        int height = 0;
        int widest = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            measureChildWithMargins(child, widthSpec, 0, heightSpec, 0);
            MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
            int w = child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin;
            int h = child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;
            if (x > 0 && x + w > maxWidth) {
                height += rowHeight;
                x = 0;
                rowHeight = 0;
            }
            x += w;
            widest = Math.max(widest, x);
            rowHeight = Math.max(rowHeight, h);
        }
        height += rowHeight;
        setMeasuredDimension(
                resolveSize(widest + getPaddingLeft() + getPaddingRight(), widthSpec),
                resolveSize(height + getPaddingTop() + getPaddingBottom(), heightSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int width = r - l;
        final int maxWidth = width - getPaddingLeft() - getPaddingRight();
        final boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        int x = 0;
        int y = 0;
        int rowHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
            int w = child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin;
            int h = child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;
            if (x > 0 && x + w > maxWidth) {
                y += rowHeight;
                x = 0;
                rowHeight = 0;
            }
            int outerLeft = rtl ? width - getPaddingRight() - x - w : getPaddingLeft() + x;
            int left = outerLeft + lp.leftMargin;
            int top = getPaddingTop() + y + lp.topMargin;
            child.layout(left, top, left + child.getMeasuredWidth(), top + child.getMeasuredHeight());
            x += w;
            rowHeight = Math.max(rowHeight, h);
        }
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new MarginLayoutParams(getContext(), attrs);
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected LayoutParams generateLayoutParams(LayoutParams p) {
        return new MarginLayoutParams(p);
    }

    @Override
    protected boolean checkLayoutParams(LayoutParams p) {
        return p instanceof MarginLayoutParams;
    }
}
