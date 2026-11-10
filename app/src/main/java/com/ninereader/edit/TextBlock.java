package com.ninereader.edit;

import androidx.annotation.NonNull;

/**
 * A block of text detected on a page (a paragraph, a title, a table cell...), editable as a whole.
 * Bounds are {left, bottom, right, top} in PDF user space.
 */
public final class TextBlock {

    /** Line pitch used when it cannot be measured (single line, new text), relative to the font size. */
    public static final float DEFAULT_LEADING = 1.2f;

    public final int page;
    @NonNull
    public final float[] bounds;
    @NonNull
    public final String text;
    public final float fontSize;
    /** Distance between two baselines (the original line pitch), in points. */
    public final float lineHeight;
    @NonNull
    public final EditFonts.Family family;
    public final boolean bold;
    public final boolean italic;
    public final int color;
    /** True when the text comes from OCR (scanned page): there is no glyph to remove. */
    public final boolean fromOcr;

    public TextBlock(int page, @NonNull float[] bounds, @NonNull String text, float fontSize, float lineHeight,
                     @NonNull EditFonts.Family family, boolean bold, boolean italic, int color, boolean fromOcr) {
        this.page = page;
        this.bounds = bounds;
        this.text = text;
        this.fontSize = fontSize;
        this.lineHeight = lineHeight > 0 ? lineHeight : fontSize * DEFAULT_LEADING;
        this.family = family;
        this.bold = bold;
        this.italic = italic;
        this.color = color;
        this.fromOcr = fromOcr;
    }

    public boolean contains(float x, float y, float tolerance) {
        return x >= bounds[0] - tolerance && x <= bounds[2] + tolerance && y >= bounds[1] - tolerance && y <= bounds[3] + tolerance;
    }

    /** Key identifying the block of a page (used to know whether it was already edited). */
    @NonNull
    public String key() {
        return page + ":" + Math.round(bounds[0]) + ":" + Math.round(bounds[1]) + ":" + Math.round(bounds[2]) + ":" + Math.round(bounds[3]);
    }
}
