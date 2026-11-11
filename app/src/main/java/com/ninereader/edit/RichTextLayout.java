package com.ninereader.edit;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays out styled text in a box (MOD-02): greedy word wrap honouring explicit line breaks, then pieces of
 * uniform style with their x position. The same layout is used for the on-screen preview and for the text
 * written into the PDF, so both break lines at the same places. Pure logic, unit tested.
 */
public final class RichTextLayout {

    /** Width of a string drawn with a style, in points (already including the horizontal scale). */
    public interface Measurer {
        float width(@NonNull String text, @NonNull StyleRun style);
    }

    /** A run of characters of one style on a line. */
    public static final class Piece {
        @NonNull
        public final String text;
        @NonNull
        public final StyleRun style;
        public final float x;
        public final float width;

        Piece(@NonNull String text, @NonNull StyleRun style, float x, float width) {
            this.text = text;
            this.style = style;
            this.x = x;
            this.width = width;
        }
    }

    /** A laid-out line. */
    public static final class Line {
        public final List<Piece> pieces = new ArrayList<>();
        public float width;
        /** Character range of the line in the text. */
        public int start;
        public int end;
    }

    private RichTextLayout() {
    }

    @NonNull
    public static List<Line> layout(@NonNull String text, @NonNull List<StyleRun> runs, float maxWidth, @NonNull Measurer m) {
        List<Line> out = new ArrayList<>();
        int pos = 0;
        while (pos <= text.length()) {
            int nl = text.indexOf('\n', pos);
            int end = nl < 0 ? text.length() : nl;
            wrapParagraph(text, runs, pos, end, maxWidth, m, out);
            if (nl < 0) break;
            pos = nl + 1;
        }
        return out;
    }

    private static void wrapParagraph(String text, List<StyleRun> runs, int from, int to, float maxWidth, Measurer m, List<Line> out) {
        int lineStart = from; // leading spaces of a paragraph are kept (indent)
        int lineEnd = -1; // end of the last word placed on the current line
        int i = from;
        while (i < to) {
            int wordStart = i;
            while (wordStart < to && text.charAt(wordStart) == ' ') wordStart++;
            int wordEnd = wordStart;
            while (wordEnd < to && text.charAt(wordEnd) != ' ') wordEnd++;
            if (wordEnd == wordStart) break; // trailing spaces
            if (lineEnd >= 0 && width(text, runs, lineStart, wordEnd, m) > maxWidth) {
                out.add(line(text, runs, lineStart, lineEnd, m));
                lineStart = wordStart; // the spaces at the break are dropped
            }
            lineEnd = wordEnd; // a word wider than the box stays alone on its line
            i = wordEnd;
        }
        out.add(line(text, runs, lineStart, lineEnd < 0 ? lineStart : lineEnd, m));
    }

    /** Width of [start, end), measured piece by piece. */
    public static float width(@NonNull String text, @NonNull List<StyleRun> runs, int start, int end, @NonNull Measurer m) {
        float w = 0;
        for (StyleRun r : runs) {
            int s = Math.max(start, r.start);
            int e = Math.min(end, r.end);
            if (e > s) w += m.width(text.substring(s, e), r);
        }
        return w;
    }

    private static Line line(String text, List<StyleRun> runs, int start, int end, Measurer m) {
        Line line = new Line();
        line.start = start;
        line.end = end;
        float x = 0;
        for (StyleRun r : runs) {
            int s = Math.max(start, r.start);
            int e = Math.min(end, r.end);
            if (e <= s) continue;
            String piece = text.substring(s, e);
            float w = m.width(piece, r);
            line.pieces.add(new Piece(piece, r, x, w));
            x += w;
        }
        line.width = x;
        return line;
    }

    /** Vertical extent of a highlight on a line: {bottom, top} relative to the baseline (y up). */
    @NonNull
    public static float[] highlightSpan(float fontSize, float lineHeight) {
        float h = Math.max(lineHeight, fontSize * 1.15f);
        float bottom = -h * 0.22f;
        return new float[]{bottom, bottom + h};
    }
}
