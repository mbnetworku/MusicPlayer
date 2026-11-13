package com.musicplayer.lite;

import android.app.Activity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.SeekBar;

/**
 * Drag the whole screen down to close it, like a sheet on an iPhone. The screen follows the
 * finger and slides away. Sliders keep their own drag, so a touch that starts on one is ignored.
 */
final class SwipeDownToClose {
    private final Activity activity;
    private final int slop;
    private float downX;
    private float downY;
    private boolean tracking;
    private boolean dragging;
    private VelocityTracker velocity;
    private boolean injecting;

    SwipeDownToClose(Activity activity) {
        this.activity = activity;
        this.slop = ViewConfiguration.get(activity).getScaledTouchSlop();
    }

    /** Call from dispatchTouchEvent. True when this gesture has taken the touch. */
    boolean onTouch(MotionEvent e) {
        if (injecting) return false;   // the cancel that this class sends to the children itself
        final View content = activity.findViewById(android.R.id.content);
        if (content == null) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getRawX();
                downY = e.getRawY();
                dragging = false;
                tracking = !startsOnSlider(content, e.getRawX(), e.getRawY());
                if (velocity != null) velocity.recycle();
                velocity = VelocityTracker.obtain();
                velocity.addMovement(e);
                return false;
            case MotionEvent.ACTION_MOVE: {
                if (!tracking) return false;
                velocity.addMovement(e);
                final float dx = e.getRawX() - downX;
                final float dy = e.getRawY() - downY;
                if (!dragging && dy > slop * 2 && dy > Math.abs(dx) * 1.5f) {
                    dragging = true;
                    final MotionEvent cancel = MotionEvent.obtain(e);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    injecting = true;
                    try {
                        activity.getWindow().getDecorView().dispatchTouchEvent(cancel);   // children stop their own touch
                    } finally {
                        injecting = false;
                    }
                    cancel.recycle();
                }
                if (dragging) {
                    content.setTranslationY(Math.max(0f, dy));
                    return true;
                }
                return false;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                float vy = 0f;
                if (velocity != null) {
                    velocity.addMovement(e);
                    velocity.computeCurrentVelocity(1000);
                    vy = velocity.getYVelocity();
                    velocity.recycle();
                    velocity = null;
                }
                if (!dragging) return false;
                dragging = false;
                tracking = false;
                final float dy = e.getRawY() - downY;
                final boolean cancelled = e.getActionMasked() == MotionEvent.ACTION_CANCEL;
                if (!cancelled && (dy > content.getHeight() * 0.18f || vy > 1200f)) {
                    activity.finish();
                    activity.overridePendingTransition(0, R.anim.slide_down);
                } else {
                    content.animate().translationY(0f).setDuration(200).start();
                }
                return true;
            }
            default:
                return dragging;
        }
    }

    private static boolean startsOnSlider(View root, float rawX, float rawY) {
        return sliderAt(root, rawX, rawY);
    }

    private static boolean sliderAt(View v, float rawX, float rawY) {
        final int[] at = new int[2];
        v.getLocationOnScreen(at);
        if (rawX < at[0] || rawX > at[0] + v.getWidth() || rawY < at[1] || rawY > at[1] + v.getHeight()) return false;
        if (v instanceof SeekBar) return true;
        if (v instanceof ViewGroup) {
            final ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                if (g.getChildAt(i).getVisibility() == View.VISIBLE && sliderAt(g.getChildAt(i), rawX, rawY)) return true;
            }
        }
        return false;
    }
}
