package com.ninereader.edit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Groups the glyphs of a page into lines, then into blocks (paragraphs, titles, list items, columns), the
 * unit of text editing (MOD-02). Titles are told apart by size, but also by colour and weight (many
 * documents use one size everywhere); list items and changes of line pitch start new blocks; deliberate
 * line breaks and the style of each character are kept. Pure logic, unit tested.
 */
public final class TextLayoutAnalyzer {

    private static final String BULLETS = "•●▪■◦‣○□➢►▶✓✔-–—*·";
    private static final Pattern NUMBERED = Pattern.compile("^(\\d{1,3}[.)]|[a-z]\\)|[ivx]{1,4}[.)])\\s.*", Pattern.DOTALL);

    private TextLayoutAnalyzer() {
    }

    /** A horizontal run of glyphs sharing a baseline. */
    static final class Line {
        final List<Glyph> glyphs = new ArrayList<>();
        float baseline;
        float left = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        float size;
        /** The line starts a list item (bullet glyph, vector bullet or number). */
        boolean listItem;

        void add(Glyph g) {
            glyphs.add(g);
            left = Math.min(left, g.x);
            right = Math.max(right, g.right());
            size = Math.max(size, g.size);
        }

        /** Whether a space separates two consecutive glyphs. */
        static boolean spaceBetween(@NonNull Glyph prev, @NonNull Glyph g) {
            float gap = g.x - prev.right();
            boolean hasSpace = prev.text.endsWith(" ") || g.text.startsWith(" ");
            return !hasSpace && gap > Math.max(prev.size, g.size) * 0.18f;
        }

        @NonNull
        String text() {
            StyledText t = new StyledText();
            appendTo(t);
            return t.sb.toString().trim();
        }

        void appendTo(@NonNull StyledText out) {
            Glyph prev = null;
            for (Glyph g : glyphs) {
                if (prev != null && spaceBetween(prev, g)) out.append(' ', separatorStyle(prev, g));
                for (int i = 0; i < g.text.length(); i++) out.append(g.text.charAt(i), g);
                prev = g;
            }
            out.trimEnd();
        }

        /** Width of the first word (up to the first space), to know whether it would have fit on the line above. */
        float firstWordWidth() {
            Glyph prev = null;
            float start = left;
            for (Glyph g : glyphs) {
                if (prev != null && (spaceBetween(prev, g) || g.text.startsWith(" "))) return prev.right() - start;
                int sp = g.text.indexOf(' ');
                if (sp > 0) return g.x + g.width * sp / g.text.length() - start;
                prev = g;
            }
            return right - start;
        }

        @NonNull
        Set<Integer> styles() {
            Set<Integer> s = new HashSet<>();
            for (Glyph g : glyphs) if (!g.text.trim().isEmpty()) s.add(g.styleKey());
            return s;
        }

        @NonNull
        String firstChars() {
            StringBuilder sb = new StringBuilder();
            for (Glyph g : glyphs) {
                sb.append(g.text);
                if (sb.length() > 8) break;
            }
            return sb.toString().trim();
        }
    }

    /** Text being built with the style of each character. */
    static final class StyledText {
        final StringBuilder sb = new StringBuilder();
        final List<StyleRun> runs = new ArrayList<>();

        void append(char c, @NonNull Glyph style) {
            append(c, style.bold, style.italic, style.color, style.highlight);
        }

        void append(char c, boolean bold, boolean italic, int color, int highlight) {
            if (Character.isWhitespace(c) || c == ' ') {
                if (sb.length() == 0) return;
                char last = sb.charAt(sb.length() - 1);
                if (last == ' ' || last == '\n') return;
                if (c != '\n') c = ' ';
            }
            int pos = sb.length();
            sb.append(c);
            StyleRun last = runs.isEmpty() ? null : runs.get(runs.size() - 1);
            StyleRun r = new StyleRun(pos, pos + 1, bold, italic, color, highlight);
            if (last != null && last.end == pos && last.sameStyle(r)) {
                last.end = pos + 1;
            } else {
                runs.add(r);
            }
        }

        void trimEnd() {
            while (sb.length() > 0 && sb.charAt(sb.length() - 1) == ' ') {
                sb.setLength(sb.length() - 1);
                StyleRun last = runs.get(runs.size() - 1);
                last.end--;
                if (last.end <= last.start) runs.remove(runs.size() - 1);
            }
        }

        char lastChar() {
            return sb.length() == 0 ? 0 : sb.charAt(sb.length() - 1);
        }

        void deleteLast() {
            sb.setLength(sb.length() - 1);
            StyleRun last = runs.get(runs.size() - 1);
            last.end--;
            if (last.end <= last.start) runs.remove(runs.size() - 1);
        }
    }

    /**
     * Style of a space inserted between two glyphs: the previous one inside a run, the next one between a
     * bold lead-in and the text; highlighted only inside a highlight.
     */
    @NonNull
    static Glyph separatorStyle(@NonNull Glyph prev, @NonNull Glyph next) {
        Glyph base = prev.styleKey() == next.styleKey() && prev.italic == next.italic ? prev : next;
        return prev.highlight == next.highlight ? base.withHighlight(prev.highlight) : base.withHighlight(0);
    }

    /** Detects the editable blocks of a page. */
    @NonNull
    public static List<TextBlock> analyze(int page, @NonNull List<Glyph> glyphs) {
        return analyze(page, glyphs, Collections.emptyList());
    }

    /**
     * Detects the editable blocks of a page.
     *
     * @param marks small vector shapes drawn on the page {left, bottom, right, top} (list bullets)
     */
    @NonNull
    public static List<TextBlock> analyze(int page, @NonNull List<Glyph> glyphs, @NonNull List<float[]> marks) {
        return analyze(page, glyphs, marks, Collections.emptyList());
    }

    /**
     * Detects the editable blocks of a page.
     *
     * @param marks small vector shapes drawn on the page {left, bottom, right, top} (list bullets)
     * @param rules thin lines {left, bottom, right, top} (table borders...): text is never joined across them
     */
    @NonNull
    public static List<TextBlock> analyze(int page, @NonNull List<Glyph> glyphs, @NonNull List<float[]> marks,
                                          @NonNull List<float[]> rules) {
        List<float[]> horizontal = new ArrayList<>();
        List<float[]> vertical = new ArrayList<>();
        for (float[] r : rules) (r[2] - r[0] >= r[3] - r[1] ? horizontal : vertical).add(r);
        List<Line> lines = lines(glyphs, vertical);
        markListItems(lines, marks);
        List<List<Line>> groups = blocks(lines, horizontal);
        List<TextBlock> out = new ArrayList<>();
        for (List<Line> group : groups) {
            TextBlock b = toBlock(page, group, columnRight(lines, group));
            if (b != null) out.add(b);
        }
        return out;
    }

    /**
     * Right edge of the text column of a block: the furthest right of the page lines starting at the same
     * left edge. A line of the block ending well before it was broken on purpose (address, signature,
     * short lines of a box), even when it is the longest line of the block.
     */
    static float columnRight(@NonNull List<Line> all, @NonNull List<Line> block) {
        float left = blockLeft(block);
        float right = blockRight(block);
        float size = block.get(0).size;
        for (Line l : all) {
            if (Math.abs(l.left - left) < size * 2f && Math.abs(l.size - size) <= size * 0.25f) right = Math.max(right, l.right);
        }
        return right;
    }

    /** Splits glyphs into line segments (same baseline, no wide horizontal gap). */
    @NonNull
    static List<Line> lines(@NonNull List<Glyph> glyphs) {
        return lines(glyphs, Collections.emptyList());
    }

    /** Splits glyphs into line segments; a vertical rule (table cell border) also ends a segment. */
    @NonNull
    static List<Line> lines(@NonNull List<Glyph> glyphs, @NonNull List<float[]> vertical) {
        List<Glyph> sorted = new ArrayList<>();
        for (Glyph g : glyphs) {
            if (g.size > 0 && !g.text.trim().isEmpty()) sorted.add(g);
        }
        // Top to bottom; rows are then sorted left to right. The comparator must be a total order: a
        // "same line within a tolerance" comparison is not transitive and makes TimSort throw on dense
        // pages (superscripts, mixed fonts), which left the whole page without editable text.
        Collections.sort(sorted, (a, b) -> Float.compare(b.baseline, a.baseline));
        List<List<Glyph>> rows = new ArrayList<>();
        List<Glyph> row = null;
        float rowBaseline = 0;
        for (Glyph g : sorted) {
            if (row == null || Math.abs(g.baseline - rowBaseline) > g.size * 0.35f) {
                row = new ArrayList<>();
                rows.add(row);
                rowBaseline = g.baseline;
            }
            row.add(g);
        }
        List<Line> lines = new ArrayList<>();
        for (List<Glyph> r : rows) {
            Collections.sort(r, (a, b) -> Float.compare(a.x, b.x));
            Line line = null;
            for (Glyph g : r) {
                // A wide gap separates columns / table cells.
                if (line != null && (g.x - line.right > Math.max(g.size, line.size) * 1.6f || crosses(vertical, line.right, g))) line = null;
                if (line == null) {
                    line = new Line();
                    line.baseline = g.baseline;
                    lines.add(line);
                }
                line.add(g);
            }
        }
        return lines;
    }

    /** Whether a vertical rule is drawn between x and the glyph, across its baseline. */
    private static boolean crosses(@NonNull List<float[]> vertical, float x, @NonNull Glyph g) {
        for (float[] r : vertical) {
            float cx = (r[0] + r[2]) / 2f;
            if (cx >= x - 0.5f && cx <= g.x + g.width * 0.3f && r[1] <= g.baseline + g.size * 0.3f && r[3] >= g.baseline) return true;
        }
        return false;
    }

    /** Flags the lines that start a list item: bullet character, number, or vector bullet on their left. */
    static void markListItems(@NonNull List<Line> lines, @NonNull List<float[]> marks) {
        List<float[]> all = new ArrayList<>(marks);
        for (Line line : lines) {
            String t = line.text();
            if (t.length() == 1 && BULLETS.indexOf(t.charAt(0)) >= 0) {
                // A bullet separated from its text by a tab: acts as a mark for the text on its right.
                all.add(new float[]{line.left, line.baseline, line.right, line.baseline + line.size * 0.6f});
            }
        }
        for (Line line : lines) {
            String start = line.firstChars();
            if (start.isEmpty()) continue;
            char c = start.charAt(0);
            if (BULLETS.indexOf(c) >= 0 && start.length() > 1 && (start.charAt(1) == ' ' || line.glyphs.size() > 1
                    && Line.spaceBetween(line.glyphs.get(0), line.glyphs.get(1)))) {
                line.listItem = true;
                continue;
            }
            if (NUMBERED.matcher(line.text()).matches() && line.text().length() > 3) {
                line.listItem = true;
                continue;
            }
            for (float[] m : all) {
                float w = m[2] - m[0];
                float h = m[3] - m[1];
                float cy = (m[1] + m[3]) / 2f;
                if (w <= 0 || h <= 0 || w > line.size * 0.9f || h > line.size * 0.9f) continue;
                boolean leftOfLine = m[2] <= line.left + 0.5f && line.left - m[2] < line.size * 3f;
                boolean onLine = cy > line.baseline - line.size * 0.1f && cy < line.baseline + line.size * 0.75f;
                if (leftOfLine && onLine) {
                    line.listItem = true;
                    break;
                }
            }
        }
    }

    /** Groups lines into blocks: similar size and style, regular line pitch, overlapping horizontally. */
    @NonNull
    static List<List<Line>> blocks(@NonNull List<Line> lines) {
        return blocks(lines, Collections.emptyList());
    }

    @NonNull
    static List<List<Line>> blocks(@NonNull List<Line> lines, @NonNull List<float[]> rules) {
        List<Line> sorted = new ArrayList<>(lines);
        Collections.sort(sorted, (a, b) -> a.baseline != b.baseline ? Float.compare(b.baseline, a.baseline) : Float.compare(a.left, b.left));
        List<List<Line>> blocks = new ArrayList<>();
        for (Line line : sorted) {
            List<Line> target = null;
            if (!line.listItem) {
                for (int i = blocks.size() - 1; i >= 0 && target == null; i--) {
                    List<Line> block = blocks.get(i);
                    if (canJoin(block, line) && !ruleBetween(block.get(block.size() - 1), line, rules)) target = block;
                }
            }
            if (target == null) {
                target = new ArrayList<>();
                blocks.add(target);
            }
            target.add(line);
        }
        return blocks;
    }

    /** Whether a horizontal line is drawn between two lines of text (a table border, for instance). */
    private static boolean ruleBetween(@NonNull Line above, @NonNull Line below, @NonNull List<float[]> rules) {
        for (float[] r : rules) {
            float cy = (r[1] + r[3]) / 2f;
            if (cy >= above.baseline || cy <= below.baseline + below.size * 0.5f) continue;
            if (Math.min(r[2], below.right) > Math.max(r[0], below.left)) return true;
        }
        return false;
    }

    private static boolean canJoin(@NonNull List<Line> block, @NonNull Line line) {
        Line last = block.get(block.size() - 1);
        float size = last.size;
        float gap = last.baseline - line.baseline;
        if (gap <= 0) return false;
        if (block.size() == 1) {
            if (gap > size * 1.75f) return false;
        } else {
            // Inside a paragraph the pitch is regular: a wider gap is a paragraph or list item break.
            float pitch = block.get(block.size() - 2).baseline - last.baseline;
            if (gap > pitch + Math.max(1f, size * 0.2f)) return false;
        }
        if (Math.abs(line.size - size) > size * 0.25f) return false;
        // A title differs from the following text by its colour or its weight: no style in common.
        Set<Integer> common = last.styles();
        common.retainAll(line.styles());
        if (common.isEmpty()) return false;
        float overlap = Math.min(line.right, blockRight(block)) - Math.max(line.left, blockLeft(block));
        float minWidth = Math.min(line.right - line.left, blockRight(block) - blockLeft(block));
        boolean alignedLeft = Math.abs(line.left - blockLeft(block)) < size * 2f;
        return overlap > minWidth * 0.3f || alignedLeft && overlap > 0;
    }

    private static float blockLeft(List<Line> block) {
        float l = Float.MAX_VALUE;
        for (Line line : block) l = Math.min(l, line.left);
        return l;
    }

    private static float blockRight(List<Line> block) {
        float r = -Float.MAX_VALUE;
        for (Line line : block) r = Math.max(r, line.right);
        return r;
    }

    @Nullable
    static TextBlock toBlock(int page, @NonNull List<Line> block) {
        return toBlock(page, block, blockRight(block));
    }

    @Nullable
    static TextBlock toBlock(int page, @NonNull List<Line> block, float columnRight) {
        float l = Float.MAX_VALUE;
        float r = -Float.MAX_VALUE;
        float top = -Float.MAX_VALUE;
        float bottom = Float.MAX_VALUE;
        Map<String, Integer> fonts = new HashMap<>();
        Map<Integer, Integer> colors = new HashMap<>();
        int boldChars = 0;
        int italicChars = 0;
        int chars = 0;
        List<Float> sizes = new ArrayList<>();
        StyledText text = new StyledText();
        float right = Math.max(blockRight(block), columnRight);
        float originalWidth = 0;
        for (int i = 0; i < block.size(); i++) {
            Line line = block.get(i);
            l = Math.min(l, line.left);
            r = Math.max(r, line.right);
            top = Math.max(top, line.baseline + line.size * 0.8f);
            bottom = Math.min(bottom, line.baseline - line.size * 0.25f);
            originalWidth += line.right - line.left;
            for (Glyph g : line.glyphs) {
                sizes.add(g.size);
                int n = g.text.trim().length();
                String f = g.font == null ? "" : g.font;
                fonts.put(f, fonts.getOrDefault(f, 0) + n);
                colors.put(g.color, colors.getOrDefault(g.color, 0) + n);
                chars += n;
                if (g.bold) boldChars += n;
                if (g.italic) italicChars += n;
            }
            if (i > 0 && text.sb.length() > 0) {
                Line prevLine = block.get(i - 1);
                Glyph prevGlyph = prevLine.glyphs.get(prevLine.glyphs.size() - 1);
                Glyph nextGlyph = line.glyphs.get(0);
                char lastChar = text.lastChar();
                String t = line.text();
                float space = Math.max(prevLine.size, line.size) * 0.25f;
                // The first word of this line would have fitted on the previous one: deliberate line break.
                boolean deliberate = prevLine.right + space + line.firstWordWidth() < right - Math.max(1f, line.size * 0.3f);
                boolean shortPrevious = prevLine.right - prevLine.left < (right - l) * 0.6f;
                if (lastChar == '-' && !t.isEmpty() && Character.isLowerCase(t.charAt(0))) {
                    text.deleteLast(); // "infor-" + "mation"
                } else if (deliberate || shortPrevious && ".:!?".indexOf(lastChar) >= 0) {
                    text.append('\n', prevGlyph.withHighlight(0));
                } else {
                    text.append(' ', separatorStyle(prevGlyph, nextGlyph));
                }
            }
            line.appendTo(text);
        }
        text.trimEnd();
        String s = text.sb.toString();
        if (s.trim().isEmpty()) return null;
        Collections.sort(sizes);
        float size = sizes.get(sizes.size() / 2);
        List<Float> gaps = new ArrayList<>();
        for (int i = 1; i < block.size(); i++) gaps.add(block.get(i - 1).baseline - block.get(i).baseline);
        float lineHeight = 0;
        if (!gaps.isEmpty()) {
            Collections.sort(gaps);
            lineHeight = Math.max(size * 0.9f, Math.min(size * 3f, gaps.get(gaps.size() / 2)));
        }
        String font = Collections.max(fonts.entrySet(), Map.Entry.comparingByValue()).getKey();
        int color = Collections.max(colors.entrySet(), Map.Entry.comparingByValue()).getKey();
        boolean bold = boldChars * 2 > chars;
        boolean italic = italicChars * 2 > chars;
        TextBlock b = new TextBlock(page, new float[]{l, bottom, r, top}, s, size, lineHeight, EditFonts.guessFamily(font),
                bold, italic, color, false, text.runs);
        b.originalWidth = originalWidth;
        b.lineCount = block.size();
        return b;
    }
}
