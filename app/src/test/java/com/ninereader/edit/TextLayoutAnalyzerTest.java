package com.ninereader.edit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class TextLayoutAnalyzerTest {

    /** Adds the glyphs of a word (6 pt advance per char at size 12) starting at x. */
    private static float word(List<Glyph> out, String w, float x, float baseline, float size, String font) {
        float adv = size * 0.5f;
        for (int i = 0; i < w.length(); i++) {
            out.add(new Glyph(String.valueOf(w.charAt(i)), x, baseline, adv, size, font, 0xFF000000));
            x += adv;
        }
        return x;
    }

    private static void line(List<Glyph> out, String text, float x, float baseline, float size, String font) {
        for (String w : text.split(" ")) {
            x = word(out, w, x, baseline, size, font) + size * 0.3f;
        }
    }

    @Test
    public void groupsLinesIntoParagraphsAndTitles() {
        List<Glyph> g = new ArrayList<>();
        line(g, "Titre du document", 72, 760, 20, "Helvetica-Bold");
        line(g, "Premiere ligne du paragraphe qui continue", 72, 700, 12, "TimesNewRomanPSMT");
        line(g, "sur une seconde ligne.", 72, 686, 12, "TimesNewRomanPSMT");
        line(g, "Autre paragraphe", 72, 600, 12, "TimesNewRomanPSMT");
        List<TextBlock> blocks = TextLayoutAnalyzer.analyze(3, g);
        assertEquals(3, blocks.size());
        TextBlock title = blocks.get(0);
        assertEquals("Titre du document", title.text);
        assertTrue(title.bold);
        assertEquals(EditFonts.Family.SANS, title.family);
        assertEquals(20f, title.fontSize, 0.01f);
        TextBlock para = blocks.get(1);
        assertEquals("Premiere ligne du paragraphe qui continue sur une seconde ligne.", para.text);
        assertEquals(EditFonts.Family.SERIF, para.family);
        assertFalse(para.bold);
        assertEquals(3, para.page);
        assertEquals(14f, para.lineHeight, 0.01f);
        assertEquals(20f * TextBlock.DEFAULT_LEADING, title.lineHeight, 0.01f);
        // Bounds cover both lines.
        assertTrue(para.bounds[1] < 686 && para.bounds[3] > 700);
        assertTrue(para.contains(80, 690, 0));
    }

    @Test
    public void separatesColumns() {
        List<Glyph> g = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            line(g, "Colonne gauche texte", 50, 700 - i * 14, 10, "Arial");
            line(g, "Colonne droite texte", 330, 700 - i * 14, 10, "Arial");
        }
        List<TextBlock> blocks = TextLayoutAnalyzer.analyze(0, g);
        assertEquals(2, blocks.size());
        assertTrue(blocks.get(0).bounds[2] < 330);
        assertTrue(blocks.get(1).bounds[0] >= 330);
    }

    @Test
    public void joinsHyphenatedWordsAndKeepsSentenceBreaks() {
        List<Glyph> g = new ArrayList<>();
        line(g, "Une longue ligne avec une infor-", 72, 700, 12, "Arial");
        line(g, "mation coupee en fin de ligne ici.", 72, 686, 12, "Arial");
        line(g, "Court.", 72, 672, 12, "Arial");
        line(g, "Suite du meme bloc de texte ici present.", 72, 658, 12, "Arial");
        List<TextBlock> blocks = TextLayoutAnalyzer.analyze(0, g);
        assertEquals(1, blocks.size());
        String t = blocks.get(0).text;
        assertTrue(t, t.contains("information coupee"));
        assertTrue(t, t.contains("Court.\nSuite"));
    }

    @Test
    public void insertsSpacesFromGaps() {
        List<Glyph> g = new ArrayList<>();
        word(g, "Bonjour", 72, 700, 12, null);
        word(g, "monde", 72 + 7 * 6 + 4, 700, 12, null);
        assertEquals("Bonjour monde", TextLayoutAnalyzer.analyze(0, g).get(0).text);
    }

    @Test
    public void emptyPage() {
        assertTrue(TextLayoutAnalyzer.analyze(0, new ArrayList<>()).isEmpty());
    }

    @Test
    public void fontGuesses() {
        assertEquals(EditFonts.Family.SERIF, EditFonts.guessFamily("ABCDEF+TimesNewRomanPS-BoldMT"));
        assertEquals(EditFonts.Family.MONO, EditFonts.guessFamily("CourierNewPSMT"));
        assertEquals(EditFonts.Family.SANS, EditFonts.guessFamily("Helvetica"));
        assertEquals(EditFonts.Family.SANS, EditFonts.guessFamily("NotoSans-Regular"));
        assertEquals(EditFonts.Family.SANS, EditFonts.guessFamily(null));
        assertTrue(EditFonts.guessBold("Arial-BoldMT"));
        assertTrue(EditFonts.guessItalic("Times-Italic"));
        assertFalse(EditFonts.guessItalic("Arial"));
        assertEquals("fonts/LiberationSerif-BoldItalic.ttf", EditFonts.assetPath(EditFonts.Family.SERIF, true, true));
        assertEquals("fonts/LiberationMono-Regular.ttf", EditFonts.assetPath(EditFonts.Family.MONO, false, false));
    }
}
