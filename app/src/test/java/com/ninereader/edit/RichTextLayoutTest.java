package com.ninereader.edit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RichTextLayoutTest {

    /** 5 pt per character, 6 pt in bold. */
    private static final RichTextLayout.Measurer M = (s, st) -> s.length() * (st.bold ? 6f : 5f);

    private static List<StyleRun> plain(String text) {
        return StyleRun.normalize(null, text.length(), false, false, 0xFF000000);
    }

    private static List<String> lines(String text, List<StyleRun> runs, float width) {
        List<String> out = new ArrayList<>();
        for (RichTextLayout.Line l : RichTextLayout.layout(text, runs, width, M)) out.add(text.substring(l.start, l.end));
        return out;
    }

    @Test
    public void wrapsAtWordsAndHonoursLineBreaks() {
        String t = "un deux trois quatre\ncinq";
        assertEquals(Arrays.asList("un deux", "trois", "quatre", "cinq"), lines(t, plain(t), 40));
        assertEquals(Arrays.asList("un deux trois quatre", "cinq"), lines(t, plain(t), 1000));
    }

    @Test
    public void longWordStaysAloneAndEmptyParagraphsAreKept() {
        String t = "a anticonstitutionnellement b\n\nc";
        assertEquals(Arrays.asList("a", "anticonstitutionnellement", "b", "", "c"), lines(t, plain(t), 30));
    }

    @Test
    public void piecesFollowTheRuns() {
        String t = "Gras puis normal";
        List<StyleRun> runs = Arrays.asList(new StyleRun(0, 4, true, false, 0xFF000000, 0),
                new StyleRun(4, t.length(), false, false, 0xFF000000, 0xFFFFFF00));
        List<RichTextLayout.Line> l = RichTextLayout.layout(t, runs, 1000, M);
        assertEquals(1, l.size());
        List<RichTextLayout.Piece> p = l.get(0).pieces;
        assertEquals(2, p.size());
        assertEquals("Gras", p.get(0).text);
        assertEquals(24f, p.get(0).width, 0.001f);
        assertEquals(24f, p.get(1).x, 0.001f);
        assertEquals(0xFFFFFF00, p.get(1).style.highlight);
        // Bold is wider: wrapping measures each style with its own font.
        assertEquals(2, RichTextLayout.layout(t, runs, 70, M).size());
    }

    @Test
    public void restyleSplitsRunsAndMerges() {
        String t = "abcdefghij";
        List<StyleRun> runs = StyleRun.restyle(plain(t), 2, 5, r -> r.bold = true);
        assertEquals(3, runs.size());
        assertTrue(runs.get(1).bold);
        assertEquals(2, runs.get(1).start);
        assertEquals(5, runs.get(1).end);
        assertTrue(StyleRun.all(runs, 2, 5, r -> r.bold));
        assertFalse(StyleRun.all(runs, 1, 5, r -> r.bold));
        runs = StyleRun.restyle(runs, 0, t.length(), r -> r.bold = false);
        assertEquals(1, runs.size());
        assertTrue(StyleRun.uniform(runs));
    }

    @Test
    public void normalizeFillsGapsWithTheDefaultStyle() {
        List<StyleRun> runs = StyleRun.normalize(Arrays.asList(new StyleRun(3, 5, false, true, 0xFF0000FF, 0)), 8, true, false, 0xFF000000);
        assertEquals(3, runs.size());
        assertTrue(runs.get(0).bold);
        assertTrue(runs.get(1).italic);
        assertEquals(8, runs.get(2).end);
    }
}
