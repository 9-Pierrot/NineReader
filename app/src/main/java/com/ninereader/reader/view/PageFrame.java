package com.ninereader.reader.view;

import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ninereader.reader.PageTransform;

/**
 * Where a page is drawn inside a {@link SpreadView}: the slot rectangle (view pixels), the size of
 * the full page image and the crop offset. Converts between page and view coordinates.
 */
public final class PageFrame {

    public int page = -1;
    /** Visible part of the page, in spread-view coordinates. */
    public final RectF slot = new RectF();
    /** Size of the whole (uncropped) page at the current layout scale. */
    public float fullW;
    public float fullH;
    /** Offset of the visible part inside the full page. */
    public float offX;
    public float offY;
    @Nullable
    public PageTransform transform;

    private final float[] tmp = new float[2];

    /** Page rectangle → spread-view rectangle. Returns false when the page is not measured yet. */
    public boolean pageToView(@NonNull RectF pageRect, @NonNull RectF out) {
        if (transform == null) return false;
        float[] n = transform.rectToNormalized(pageRect.left, pageRect.top, pageRect.right, pageRect.bottom);
        out.set(normToViewX(n[0]), normToViewY(n[1]), normToViewX(n[2]), normToViewY(n[3]));
        return true;
    }

    /** Page point → spread-view point. */
    public boolean pagePointToView(float px, float py, @NonNull float[] out) {
        if (transform == null) return false;
        transform.toNormalized(px, py, tmp);
        out[0] = normToViewX(tmp[0]);
        out[1] = normToViewY(tmp[1]);
        return true;
    }

    /** Spread-view point → page point. */
    public boolean viewToPage(float x, float y, @NonNull float[] out) {
        if (transform == null) return false;
        transform.toPage((x - slot.left + offX) / fullW, (y - slot.top + offY) / fullH, out);
        return true;
    }

    /** Normalised (0..1 of the full page) → spread-view coordinates. */
    public float normToViewX(float nx) {
        return slot.left - offX + nx * fullW;
    }

    public float normToViewY(float ny) {
        return slot.top - offY + ny * fullH;
    }

    public float viewToNormX(float x) {
        return (x - slot.left + offX) / fullW;
    }

    public float viewToNormY(float y) {
        return (y - slot.top + offY) / fullH;
    }

    /** Page units → view pixels (for stroke widths). */
    public float pageUnitToPx() {
        return transform == null ? 1f : (float) (transform.unitScaleX() * fullW);
    }

    public boolean contains(float x, float y) {
        return slot.contains(x, y);
    }
}
