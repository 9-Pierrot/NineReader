package com.ninereader.edit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** A glyph drawn on a page, in PDF user space (points, y up): origin on the baseline, advance width. */
public final class Glyph {

    @NonNull
    public final String text;
    public final float x;
    public final float baseline;
    public final float width;
    public final float size;
    @Nullable
    public final String font;
    public final int color;

    public Glyph(@NonNull String text, float x, float baseline, float width, float size, @Nullable String font, int color) {
        this.text = text;
        this.x = x;
        this.baseline = baseline;
        this.width = width;
        this.size = size;
        this.font = font;
        this.color = color;
    }

    public float right() {
        return x + width;
    }
}
