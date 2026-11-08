package com.ninereader.reader;

import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * Column zoom (RAP-05): from the text line rectangles of a page, finds the horizontal extent of the
 * text column under a point. Coordinates are view pixels (y down).
 */
public final class ColumnDetector {

    private ColumnDetector() {
    }

    /**
     * @param lines text rectangles of the page (view coordinates)
     * @param x     tap position
     * @param pageWidth width of the page in the same unit
     * @return {left, right} of the column, or null when no clear column was found
     */
    @Nullable
    public static float[] columnAt(@NonNull List<RectF> lines, float x, float pageWidth) {
        float left = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        int hits = 0;
        for (RectF r : lines) {
            // Ignore lines spanning (almost) the full page: titles, headers, single-column text.
            if (r.width() > pageWidth * 0.9f) continue;
            if (r.left <= x && r.right >= x) {
                left = Math.min(left, r.left);
                right = Math.max(right, r.right);
                hits++;
            }
        }
        if (hits < 2) return null;
        // Extend with lines that are mostly inside the found column (short last lines of paragraphs).
        for (RectF r : lines) {
            if (r.left >= left - pageWidth * 0.02f && r.right <= right + pageWidth * 0.05f) {
                left = Math.min(left, r.left);
                right = Math.max(right, r.right);
            }
        }
        float width = right - left;
        if (width <= 0 || width > pageWidth * 0.8f) return null;
        return new float[]{left, right};
    }
}
