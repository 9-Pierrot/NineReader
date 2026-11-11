package com.ninereader.ui.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager2.widget.ViewPager2;

/**
 * Wraps a horizontally scrolling view placed in a page of a {@link ViewPager2} (recent files row,
 * category chips...): the child keeps the horizontal gesture as long as it can still scroll in that
 * direction, then the pager takes over and changes page. Java version of the NestedScrollableHost of
 * the ViewPager2 samples.
 */
public class NestedScrollableHost extends FrameLayout {

    private final int touchSlop;
    private float initialX;
    private float initialY;

    public NestedScrollableHost(@NonNull Context context) {
        this(context, null);
    }

    public NestedScrollableHost(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    @Nullable
    private ViewPager2 parentPager() {
        ViewParent v = getParent();
        while (v != null && !(v instanceof ViewPager2)) v = v.getParent();
        return (ViewPager2) v;
    }

    @Nullable
    private View child() {
        return getChildCount() > 0 ? getChildAt(0) : null;
    }

    private boolean canChildScroll(int orientation, float delta) {
        View child = child();
        if (child == null) return false;
        int direction = (int) -Math.signum(delta);
        return orientation == ViewPager2.ORIENTATION_HORIZONTAL
                ? child.canScrollHorizontally(direction) : child.canScrollVertically(direction);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        handleInterceptTouchEvent(e);
        return super.onInterceptTouchEvent(e);
    }

    private void handleInterceptTouchEvent(MotionEvent e) {
        ViewPager2 pager = parentPager();
        if (pager == null) return;
        int orientation = pager.getOrientation();
        // Nothing to arbitrate when the child cannot scroll along the pager.
        if (!canChildScroll(orientation, -1f) && !canChildScroll(orientation, 1f)) return;
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            initialX = e.getX();
            initialY = e.getY();
            getParent().requestDisallowInterceptTouchEvent(true);
        } else if (e.getAction() == MotionEvent.ACTION_MOVE) {
            float dx = e.getX() - initialX;
            float dy = e.getY() - initialY;
            boolean horizontal = orientation == ViewPager2.ORIENTATION_HORIZONTAL;
            // Assume the pager's direction for small movements, like ViewPager2 does.
            float scaledDx = Math.abs(dx) * (horizontal ? 0.5f : 1f);
            float scaledDy = Math.abs(dy) * (horizontal ? 1f : 0.5f);
            if (scaledDx > touchSlop || scaledDy > touchSlop) {
                if (horizontal == (scaledDy > scaledDx)) {
                    // Gesture across the pager: let all parents intercept (vertical scrolling).
                    getParent().requestDisallowInterceptTouchEvent(false);
                } else {
                    // Gesture along the pager: the child scrolls while it can, then the pager.
                    getParent().requestDisallowInterceptTouchEvent(canChildScroll(orientation, horizontal ? dx : dy));
                }
            }
        }
    }
}
