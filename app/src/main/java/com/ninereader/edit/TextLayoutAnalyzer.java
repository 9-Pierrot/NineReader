package com.ninereader.edit;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups the glyphs of a page into lines, then into blocks (paragraphs, titles, columns), the unit of
 * text editing (MOD-02). Pure logic, unit tested.
 */
public final class TextLayoutAnalyzer {

    private TextLayoutAnalyzer() {
    }

    /** A horizontal run of glyphs sharing a baseline. */
    static final class Line {
        final List<Glyph> glyphs = new ArrayList<>();
        float baseline;
        float left = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        float size;

        void add(Glyph g) {
            glyphs.add(g);
            left = Math.min(left, g.x);
            right = Math.max(right, g.right());
            size = Math.max(size, g.size);
        }

        @NonNull
        String text() {
            StringBuilder sb = new StringBuilder();
            Glyph prev = null;
            for (Glyph g : glyphs) {
                if (prev != null) {
                    float gap = g.x - prev.right();
                    boolean hasSpace = prev.text.endsWith(" ") || g.text.startsWith(" ");
                    if (!hasSpace && gap > Math.max(prev.size, g.size) * 0.18f) sb.append(' ');
                }
                sb.append(g.text);
                prev = g;
            }
            return sb.toString().replaceAll("\\s+", " ").trim();
        }
    }

    /** Detects the editable blocks of a page. */
    @NonNull
    public static List<TextBlock> analyze(int page, @NonNull List<Glyph> glyphs) {
        List<Line> lines = lines(glyphs);
        List<List<Line>> groups = blocks(lines);
        List<TextBlock> out = new ArrayList<>();
        for (List<Line> group : groups) {
            TextBlock b = toBlock(page, group);
            if (b != null) out.add(b);
        }
        return out;
    }

    /** Splits glyphs into line segments (same baseline, no wide horizontal gap). */
    @NonNull
    static List<Line> lines(@NonNull List<Glyph> glyphs) {
        List<Glyph> sorted = new ArrayList<>();
        for (Glyph g : glyphs) {
            if (g.size > 0 && !g.text.trim().isEmpty()) sorted.add(g);
        }
        // Top to bottom, then left to right.
        Collections.sort(sorted, (a, b) -> {
            float tol = Math.max(a.size, b.size) * 0.35f;
            if (Math.abs(a.baseline - b.baseline) > tol) return Float.compare(b.baseline, a.baseline);
            return Float.compare(a.x, b.x);
        });
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
                if (line != null && g.x - line.right > Math.max(g.size, line.size) * 1.6f) line = null;
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

    /** Groups lines into blocks: similar size, close baselines, overlapping horizontally. */
    @NonNull
    static List<List<Line>> blocks(@NonNull List<Line> lines) {
        List<Line> sorted = new ArrayList<>(lines);
        Collections.sort(sorted, (a, b) -> Float.compare(b.baseline, a.baseline));
        List<List<Line>> blocks = new ArrayList<>();
        for (Line line : sorted) {
            List<Line> target = null;
            for (int i = blocks.size() - 1; i >= 0 && target == null; i--) {
                List<Line> block = blocks.get(i);
                Line last = block.get(block.size() - 1);
                float size = last.size;
                float gap = last.baseline - line.baseline;
                if (gap <= 0 || gap > size * 1.75f) continue;
                if (Math.abs(line.size - size) > size * 0.25f) continue;
                float overlap = Math.min(line.right, blockRight(block)) - Math.max(line.left, blockLeft(block));
                float minWidth = Math.min(line.right - line.left, blockRight(block) - blockLeft(block));
                boolean alignedLeft = Math.abs(line.left - blockLeft(block)) < size * 2f;
                if (overlap > minWidth * 0.3f || alignedLeft && overlap > 0) target = block;
            }
            if (target == null) {
                target = new ArrayList<>();
                blocks.add(target);
            }
            target.add(line);
        }
        return blocks;
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

    static TextBlock toBlock(int page, @NonNull List<Line> block) {
        float l = Float.MAX_VALUE;
        float r = -Float.MAX_VALUE;
        float top = -Float.MAX_VALUE;
        float bottom = Float.MAX_VALUE;
        Map<String, Integer> fonts = new HashMap<>();
        Map<Integer, Integer> colors = new HashMap<>();
        List<Float> sizes = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        float width = 0;
        for (Line line : block) width = Math.max(width, line.right - line.left);
        for (int i = 0; i < block.size(); i++) {
            Line line = block.get(i);
            l = Math.min(l, line.left);
            r = Math.max(r, line.right);
            top = Math.max(top, line.baseline + line.size * 0.8f);
            bottom = Math.min(bottom, line.baseline - line.size * 0.25f);
            for (Glyph g : line.glyphs) {
                sizes.add(g.size);
                String f = g.font == null ? "" : g.font;
                fonts.put(f, fonts.getOrDefault(f, 0) + g.text.length());
                colors.put(g.color, colors.getOrDefault(g.color, 0) + 1);
            }
            String t = line.text();
            if (text.length() > 0) {
                char lastChar = text.charAt(text.length() - 1);
                boolean shortPrevious = block.get(i - 1).right - block.get(i - 1).left < width * 0.6f;
                if (lastChar == '-' && !t.isEmpty() && Character.isLowerCase(t.charAt(0))) {
                    text.setLength(text.length() - 1); // "infor-" + "mation"
                } else if (shortPrevious && ".:!?".indexOf(lastChar) >= 0) {
                    text.append('\n');
                } else {
                    text.append(' ');
                }
            }
            text.append(t);
        }
        String s = text.toString().trim();
        if (s.isEmpty()) return null;
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
        return new TextBlock(page, new float[]{l, bottom, r, top}, s, size, lineHeight, EditFonts.guessFamily(font),
                EditFonts.guessBold(font), EditFonts.guessItalic(font), color, false);
    }
}
