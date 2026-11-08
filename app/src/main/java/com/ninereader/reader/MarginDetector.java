package com.ninereader.reader;

import androidx.annotation.NonNull;

/**
 * Smart margin cropping (RAP-01): finds the bounding box of non-white content in a low resolution
 * render of a page, as fractions of the page size.
 */
public final class MarginDetector {

    /** Luminance under which a pixel counts as content. */
    private static final int THRESHOLD = 235;
    /** Margin kept around the detected content (fraction of the page). */
    private static final float PADDING = 0.02f;
    /** Ignore crops removing less than this fraction (keeps pages stable). */
    private static final float MIN_GAIN = 0.03f;

    private MarginDetector() {
    }

    /**
     * @param pixels ARGB pixels (row major)
     * @return {left, top, right, bottom} in [0..1], or the full page when nothing worth cropping was found
     */
    @NonNull
    public static float[] detect(@NonNull int[] pixels, int width, int height) {
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        // Noise tolerance: a row/column needs at least this many dark pixels.
        int rowNeed = Math.max(1, width / 200);
        int colNeed = Math.max(1, height / 200);
        int[] colCount = new int[width];
        for (int y = 0; y < height; y++) {
            int rowCount = 0;
            int base = y * width;
            for (int x = 0; x < width; x++) {
                int c = pixels[base + x];
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;
                int lum = (r * 299 + g * 587 + b * 114) / 1000;
                if (lum < THRESHOLD) {
                    rowCount++;
                    colCount[x]++;
                }
            }
            if (rowCount >= rowNeed) {
                if (y < minY) minY = y;
                maxY = y;
            }
        }
        for (int x = 0; x < width; x++) {
            if (colCount[x] >= colNeed) {
                if (x < minX) minX = x;
                maxX = x;
            }
        }
        if (maxX < 0 || maxY < 0) return new float[]{0f, 0f, 1f, 1f};
        float l = Math.max(0f, minX / (float) width - PADDING);
        float t = Math.max(0f, minY / (float) height - PADDING);
        float r = Math.min(1f, (maxX + 1) / (float) width + PADDING);
        float b = Math.min(1f, (maxY + 1) / (float) height + PADDING);
        if ((r - l) > 1f - MIN_GAIN && (b - t) > 1f - MIN_GAIN) return new float[]{0f, 0f, 1f, 1f};
        if (r - l < 0.2f || b - t < 0.2f) return new float[]{0f, 0f, 1f, 1f};
        return new float[]{l, t, r, b};
    }

    /** Union of several crops (keeps a stable crop for the whole document). */
    @NonNull
    public static float[] union(@NonNull float[] a, @NonNull float[] b) {
        return new float[]{Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[2], b[2]), Math.max(a[3], b[3])};
    }
}
