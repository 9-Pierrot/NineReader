package com.ninereader.annotations;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An annotation being edited (not yet written into the PDF). All coordinates are PDF page
 * coordinates (points, origin bottom-left, y up) so they map directly to PDF annotations.
 */
public class Annotation {

    /** Annotation kinds (ANN-01..04, SIG-02..04, MOD-01/02). */
    public enum Type {
        HIGHLIGHT, UNDERLINE, STRIKEOUT, NOTE, INK, RECT, CIRCLE, LINE, ARROW, FREETEXT, IMAGE, WHITEOUT, REDACT,
        /** Edited text of the PDF (MOD-02): original glyphs removed, new text written. */
        TEXT_EDIT;

        public boolean isTextMarkup() {
            return this == HIGHLIGHT || this == UNDERLINE || this == STRIKEOUT;
        }

        /** Whether the annotation is defined by a box that can be moved and resized. */
        public boolean isBoxed() {
            return this == NOTE || this == RECT || this == CIRCLE || this == FREETEXT || this == IMAGE || this == WHITEOUT
                    || this == REDACT || this == TEXT_EDIT;
        }

        public boolean isLine() {
            return this == LINE || this == ARROW;
        }
    }

    @NonNull
    public String id = UUID.randomUUID().toString();
    @NonNull
    public Type type = Type.HIGHLIGHT;
    public int page;
    public int color = 0xFFFFEB3B;
    /** Stroke width in points. */
    public float width = 2f;
    public float opacity = 1f;
    /** Text markup: quads as {left, bottom, right, top}. */
    @NonNull
    public List<float[]> rects = new ArrayList<>();
    /** Ink: polylines as [x0, y0, x1, y1, ...]. */
    @NonNull
    public List<float[]> strokes = new ArrayList<>();
    /** Boxed types: {left, bottom, right, top}. Lines/arrows: {x1, y1, x2, y2}. */
    @Nullable
    public float[] bounds;
    /** Note content, free text, or excerpt of the marked-up text. */
    @Nullable
    public String text;
    public float fontSize = 12f;
    /** IMAGE: absolute path of a PNG/JPEG file (signature, stamp or picture). */
    @Nullable
    public String imagePath;
    public long createdAt = System.currentTimeMillis();
    /** TEXT_EDIT: region of the original text to remove {left, bottom, right, top}; null for a new text box. */
    @Nullable
    public float[] originalBounds;
    /** TEXT_EDIT: text before editing (to detect changes and for the annotation list). */
    @Nullable
    public String originalText;
    /** TEXT_EDIT: font family name of {@code EditFonts.Family}. */
    @Nullable
    public String fontFamily;
    public boolean bold;
    public boolean italic;
    /** TEXT_EDIT: distance between baselines in points (0 = 1.2 x font size). */
    public float lineHeight;
    /** TEXT_EDIT: the original text comes from OCR (scanned page). */
    public boolean fromOcr;
    /** TEXT_EDIT: styles of character ranges of {@link #text} (bold, italic, colour, highlight); empty = uniform. */
    @Nullable
    public List<com.ninereader.edit.StyleRun> runs;
    /**
     * TEXT_EDIT: horizontal scale of the substitute font so that the text keeps the width of the original
     * font, hence the same line breaks (0 = 1).
     */
    public float textScaleX;
    /** TEXT_EDIT: colour of the page under the original text (ARGB, 0 = white), used to hide it on screen. */
    public int background;

    @NonNull
    public Annotation copy() {
        Annotation a = new Annotation();
        a.id = id;
        a.type = type;
        a.page = page;
        a.color = color;
        a.width = width;
        a.opacity = opacity;
        for (float[] r : rects) a.rects.add(r.clone());
        for (float[] s : strokes) a.strokes.add(s.clone());
        a.bounds = bounds == null ? null : bounds.clone();
        a.text = text;
        a.fontSize = fontSize;
        a.imagePath = imagePath;
        a.createdAt = createdAt;
        a.originalBounds = originalBounds == null ? null : originalBounds.clone();
        a.originalText = originalText;
        a.fontFamily = fontFamily;
        a.bold = bold;
        a.italic = italic;
        a.lineHeight = lineHeight;
        a.fromOcr = fromOcr;
        a.runs = runs == null ? null : com.ninereader.edit.StyleRun.copyOf(runs);
        a.textScaleX = textScaleX;
        a.background = background;
        return a;
    }

    /** Bounding box {left, bottom, right, top} of the whole annotation in page coordinates. */
    @NonNull
    public float[] boundingBox() {
        float l = Float.MAX_VALUE;
        float b = Float.MAX_VALUE;
        float r = -Float.MAX_VALUE;
        float t = -Float.MAX_VALUE;
        for (float[] q : rects) {
            l = Math.min(l, Math.min(q[0], q[2]));
            r = Math.max(r, Math.max(q[0], q[2]));
            b = Math.min(b, Math.min(q[1], q[3]));
            t = Math.max(t, Math.max(q[1], q[3]));
        }
        for (float[] s : strokes) {
            for (int i = 0; i + 1 < s.length; i += 2) {
                l = Math.min(l, s[i]);
                r = Math.max(r, s[i]);
                b = Math.min(b, s[i + 1]);
                t = Math.max(t, s[i + 1]);
            }
        }
        if (bounds != null) {
            l = Math.min(l, Math.min(bounds[0], bounds[2]));
            r = Math.max(r, Math.max(bounds[0], bounds[2]));
            b = Math.min(b, Math.min(bounds[1], bounds[3]));
            t = Math.max(t, Math.max(bounds[1], bounds[3]));
        }
        if (l > r) return new float[]{0, 0, 0, 0};
        float pad = type == Type.INK || type.isLine() ? width : 0;
        return new float[]{l - pad, b - pad, r + pad, t + pad};
    }

    /** Whether a page point hits the annotation (with a tolerance in points). */
    public boolean hitTest(float x, float y, float tolerance) {
        if (type == Type.INK) {
            for (float[] s : strokes) {
                for (int i = 0; i + 1 < s.length; i += 2) {
                    if (Math.hypot(s[i] - x, s[i + 1] - y) <= tolerance + width) return true;
                }
            }
            return false;
        }
        if (type.isLine() && bounds != null) {
            return distanceToSegment(x, y, bounds[0], bounds[1], bounds[2], bounds[3]) <= tolerance + width;
        }
        float[] bb = boundingBox();
        return x >= bb[0] - tolerance && x <= bb[2] + tolerance && y >= bb[1] - tolerance && y <= bb[3] + tolerance;
    }

    static double distanceToSegment(float px, float py, float x1, float y1, float x2, float y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len2 = dx * dx + dy * dy;
        if (len2 == 0) return Math.hypot(px - x1, py - y1);
        double t = Math.max(0, Math.min(1, ((px - x1) * dx + (py - y1) * dy) / len2));
        return Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
    }

    /** Moves the annotation by a page-space offset. */
    public void translate(float dx, float dy) {
        for (float[] q : rects) {
            q[0] += dx;
            q[2] += dx;
            q[1] += dy;
            q[3] += dy;
        }
        for (float[] s : strokes) {
            for (int i = 0; i + 1 < s.length; i += 2) {
                s[i] += dx;
                s[i + 1] += dy;
            }
        }
        if (bounds != null) {
            bounds[0] += dx;
            bounds[2] += dx;
            bounds[1] += dy;
            bounds[3] += dy;
        }
    }
}
