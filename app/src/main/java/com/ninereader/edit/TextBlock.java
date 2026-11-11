package com.ninereader.edit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * A block of text detected on a page (a paragraph, a title, a list item, a table cell...), editable as a
 * whole. Bounds are {left, bottom, right, top} in PDF user space.
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
    /** Dominant style of the block (used for new text and the format bar). */
    public final boolean bold;
    public final boolean italic;
    public final int color;
    /** True when the text comes from OCR (scanned page): there is no glyph to remove. */
    public final boolean fromOcr;
    /** Styles of the characters of {@link #text} (bold lead-in, italic word, colour, highlight). */
    @NonNull
    public final List<StyleRun> runs;
    /** Sum of the widths of the original lines, in points (0 = unknown): the substitute font is fitted to it. */
    public float originalWidth;
    /** Number of original lines. */
    public int lineCount = 1;
    /** Colour of the page under the block (ARGB): white, or the fill of a coloured panel. */
    public int background = 0xFFFFFFFF;

    public TextBlock(int page, @NonNull float[] bounds, @NonNull String text, float fontSize, float lineHeight,
                     @NonNull EditFonts.Family family, boolean bold, boolean italic, int color, boolean fromOcr) {
        this(page, bounds, text, fontSize, lineHeight, family, bold, italic, color, fromOcr, null);
    }

    public TextBlock(int page, @NonNull float[] bounds, @NonNull String text, float fontSize, float lineHeight,
                     @NonNull EditFonts.Family family, boolean bold, boolean italic, int color, boolean fromOcr,
                     @Nullable List<StyleRun> runs) {
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
        this.runs = Collections.unmodifiableList(StyleRun.normalize(runs, text.length(), bold, italic, color));
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
