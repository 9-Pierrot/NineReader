package com.ninereader.reader;

import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Reading themes (LEC-09), applied as a colour filter on rendered pages. */
public enum ReaderTheme {
    LIGHT("light"),
    DARK("dark"),
    SEPIA("sepia"),
    INVERTED("inverted");

    @NonNull
    public final String key;

    ReaderTheme(@NonNull String key) {
        this.key = key;
    }

    @NonNull
    public static ReaderTheme fromKey(@Nullable String key, boolean systemDark) {
        if (key == null || "auto".equals(key)) return systemDark ? DARK : LIGHT;
        for (ReaderTheme t : values()) {
            if (t.key.equals(key)) return t;
        }
        return LIGHT;
    }

    /** Matrix applied to page pixels, or null for the original colours. */
    @Nullable
    public float[] matrix() {
        switch (this) {
            case DARK:
                // Invert luminance while keeping hues roughly readable, then soften the white text.
                return new float[]{
                        -0.85f, 0, 0, 0, 235,
                        0, -0.85f, 0, 0, 235,
                        0, 0, -0.85f, 0, 235,
                        0, 0, 0, 1, 0};
            case SEPIA:
                return new float[]{
                        0.393f * 0.95f, 0.769f * 0.95f, 0.189f * 0.95f, 0, 12,
                        0.349f * 0.92f, 0.686f * 0.92f, 0.168f * 0.92f, 0, 6,
                        0.272f * 0.82f, 0.534f * 0.82f, 0.131f * 0.82f, 0, 0,
                        0, 0, 0, 1, 0};
            case INVERTED:
                return new float[]{
                        -1, 0, 0, 0, 255,
                        0, -1, 0, 0, 255,
                        0, 0, -1, 0, 255,
                        0, 0, 0, 1, 0};
            case LIGHT:
            default:
                return null;
        }
    }

    @Nullable
    public ColorMatrixColorFilter colorFilter() {
        float[] m = matrix();
        return m == null ? null : new ColorMatrixColorFilter(new ColorMatrix(m));
    }

    /** Applies the theme to a single ARGB colour (used for page backgrounds and tests). */
    public int apply(int argb) {
        float[] m = matrix();
        if (m == null) return argb;
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        int nr = clamp(m[0] * r + m[1] * g + m[2] * b + m[3] * a + m[4]);
        int ng = clamp(m[5] * r + m[6] * g + m[7] * b + m[8] * a + m[9]);
        int nb = clamp(m[10] * r + m[11] * g + m[12] * b + m[13] * a + m[14]);
        return (a << 24) | (nr << 16) | (ng << 8) | nb;
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, Math.round(v)));
    }

    public boolean isDark() {
        return this == DARK || this == INVERTED;
    }
}
