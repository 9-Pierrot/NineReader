package com.ninereader.ui.components;

import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.RecyclerView;

import com.ninereader.R;

/**
 * Lets the scrolling content of a screen pass under the floating bottom bar (so that it shows blurred
 * through it) while its last item stays reachable: the outermost vertical scrolling views get a bottom
 * padding equal to the bar height and stop clipping to their padding.
 */
public final class BottomInsets {

    private BottomInsets() {
    }

    /** Applies (or, with 0, removes) the bottom inset to the vertical scrolling views of a screen. */
    public static void apply(@NonNull View root, int inset) {
        if (isVerticalScroller(root)) {
            pad(root, inset);
            return;
        }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) apply(g.getChildAt(i), inset);
        }
    }

    static boolean isVerticalScroller(@NonNull View v) {
        if (v instanceof RecyclerView) {
            RecyclerView.LayoutManager lm = ((RecyclerView) v).getLayoutManager();
            return lm == null || lm.canScrollVertically();
        }
        return v instanceof NestedScrollView || v instanceof ScrollView || v instanceof AbsListView;
    }

    private static void pad(@NonNull View v, int inset) {
        Object base = v.getTag(R.id.tag_bottom_inset_base);
        if (!(base instanceof int[])) {
            // Original bottom padding and clipToPadding, restored when the bar is hidden.
            base = new int[]{v.getPaddingBottom(), v instanceof ViewGroup && ((ViewGroup) v).getClipToPadding() ? 1 : 0};
            v.setTag(R.id.tag_bottom_inset_base, base);
        }
        int[] original = (int[]) base;
        int bottom = original[0] + inset;
        if (v.getPaddingBottom() != bottom) v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bottom);
        if (v instanceof ViewGroup) ((ViewGroup) v).setClipToPadding(inset == 0 && original[1] == 1);
    }
}
