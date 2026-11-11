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
    public final boolean bold;
    public final boolean italic;
    /** Colour of a highlight drawn behind the glyph (ARGB), 0 = none. */
    public final int highlight;

    public Glyph(@NonNull String text, float x, float baseline, float width, float size, @Nullable String font, int color) {
        this(text, x, baseline, width, size, font, color, EditFonts.guessBold(font), EditFonts.guessItalic(font), 0);
    }

    public Glyph(@NonNull String text, float x, float baseline, float width, float size, @Nullable String font, int color,
                 boolean bold, boolean italic, int highlight) {
        this.text = text;
        this.x = x;
        this.baseline = baseline;
        this.width = width;
        this.size = size;
        this.font = font;
        this.color = color;
        this.bold = bold;
        this.italic = italic;
        this.highlight = highlight;
    }

    public float right() {
        return x + width;
    }

    @NonNull
    Glyph withHighlight(int highlight) {
        return new Glyph(text, x, baseline, width, size, font, color, bold, italic, highlight);
    }

    /** Style key used to tell titles and subtitles from body text: colour, weight and slant. */
    int styleKey() {
        return (color & 0xFFFFFF) * 4 + (bold ? 2 : 0) + (italic ? 1 : 0);
    }
}
