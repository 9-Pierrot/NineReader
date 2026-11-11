package com.ninereader.edit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Style of a range of characters [start, end) of an edited text (MOD-02): a paragraph can start with a
 * bold lead-in, contain an italic word, a coloured or highlighted passage. Plain fields: stored as JSON
 * with the annotation.
 */
public final class StyleRun {

    public int start;
    public int end;
    public boolean bold;
    public boolean italic;
    /** Text colour (ARGB). */
    public int color = 0xFF000000;
    /** Highlight colour behind the text (ARGB), 0 = none. */
    public int highlight;

    public StyleRun() {
    }

    public StyleRun(int start, int end, boolean bold, boolean italic, int color, int highlight) {
        this.start = start;
        this.end = end;
        this.bold = bold;
        this.italic = italic;
        this.color = color;
        this.highlight = highlight;
    }

    @NonNull
    public StyleRun copy() {
        return new StyleRun(start, end, bold, italic, color, highlight);
    }

    public boolean sameStyle(@NonNull StyleRun o) {
        return bold == o.bold && italic == o.italic && color == o.color && highlight == o.highlight;
    }

    @NonNull
    public static List<StyleRun> copyOf(@Nullable List<StyleRun> runs) {
        List<StyleRun> out = new ArrayList<>();
        if (runs != null) for (StyleRun r : runs) out.add(r.copy());
        return out;
    }

    /**
     * Normalises runs for a text of the given length: sorted, clipped, gaps filled with the default style,
     * adjacent runs of the same style merged. Never empty for a non-empty text.
     */
    @NonNull
    public static List<StyleRun> normalize(@Nullable List<StyleRun> runs, int length, boolean bold, boolean italic, int color) {
        List<StyleRun> sorted = copyOf(runs);
        sorted.sort((a, b) -> Integer.compare(a.start, b.start));
        List<StyleRun> out = new ArrayList<>();
        int pos = 0;
        for (StyleRun r : sorted) {
            int s = Math.max(pos, Math.max(0, r.start));
            int e = Math.min(length, r.end);
            if (e <= s) continue;
            if (s > pos) add(out, new StyleRun(pos, s, bold, italic, color, 0));
            add(out, new StyleRun(s, e, r.bold, r.italic, r.color, r.highlight));
            pos = e;
        }
        if (pos < length) add(out, new StyleRun(pos, length, bold, italic, color, 0));
        return out;
    }

    private static void add(List<StyleRun> out, StyleRun r) {
        if (!out.isEmpty()) {
            StyleRun last = out.get(out.size() - 1);
            if (last.end == r.start && last.sameStyle(r)) {
                last.end = r.end;
                return;
            }
        }
        out.add(r);
    }

    /** A change of style applied to a range. */
    public interface Change {
        void apply(@NonNull StyleRun run);
    }

    /** Applies a change to the characters [start, end), splitting the runs at the range boundaries. */
    @NonNull
    public static List<StyleRun> restyle(@NonNull List<StyleRun> runs, int start, int end, @NonNull Change change) {
        List<StyleRun> out = new ArrayList<>();
        for (StyleRun r : runs) {
            int s = Math.max(r.start, start);
            int e = Math.min(r.end, end);
            if (e <= s) {
                out.add(r.copy());
                continue;
            }
            if (r.start < s) out.add(new StyleRun(r.start, s, r.bold, r.italic, r.color, r.highlight));
            StyleRun mid = new StyleRun(s, e, r.bold, r.italic, r.color, r.highlight);
            change.apply(mid);
            out.add(mid);
            if (e < r.end) out.add(new StyleRun(e, r.end, r.bold, r.italic, r.color, r.highlight));
        }
        int length = out.isEmpty() ? 0 : out.get(out.size() - 1).end;
        StyleRun first = out.isEmpty() ? new StyleRun() : out.get(0);
        return normalize(out, length, first.bold, first.italic, first.color);
    }

    /** Whether every character of [start, end) satisfies a condition. */
    public static boolean all(@NonNull List<StyleRun> runs, int start, int end, @NonNull java.util.function.Predicate<StyleRun> test) {
        boolean any = false;
        for (StyleRun r : runs) {
            if (Math.min(r.end, end) <= Math.max(r.start, start)) continue;
            any = true;
            if (!test.test(r)) return false;
        }
        return any;
    }

    /** Whether all runs share one style (the text can be handled with the block-level style). */
    public static boolean uniform(@Nullable List<StyleRun> runs) {
        if (runs == null || runs.size() < 2) return true;
        for (StyleRun r : runs) if (!r.sameStyle(runs.get(0))) return false;
        return true;
    }

    public static boolean sameRuns(@Nullable List<StyleRun> a, @Nullable List<StyleRun> b) {
        List<StyleRun> x = a == null ? new ArrayList<>() : a;
        List<StyleRun> y = b == null ? new ArrayList<>() : b;
        if (x.size() != y.size()) return false;
        for (int i = 0; i < x.size(); i++) {
            StyleRun p = x.get(i);
            StyleRun q = y.get(i);
            if (p.start != q.start || p.end != q.end || !p.sameStyle(q)) return false;
        }
        return true;
    }
}
