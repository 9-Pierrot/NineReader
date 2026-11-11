package com.ninereader.edit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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

    /**
     * Dense page whose glyphs sit on slightly jittered baselines (mixed fonts, superscripts, glyphs
     * drawn out of order): the line grouping must not depend on a non-transitive comparator, which
     * makes TimSort throw "Comparison method violates its general contract" and the edit mode report
     * "no text" on the whole page.
     */
    @Test
    public void densePageWithJitteredBaselines() {
        for (int seed = 0; seed < 40; seed++) {
            java.util.Random rnd = new java.util.Random(seed);
            List<Glyph> g = new ArrayList<>();
            int lines = 20 + rnd.nextInt(50);
            float jitter = 1 + rnd.nextFloat() * 8; // superscripts, subscripts, mixed fonts
            float base = 800;
            for (int l = 0; l < lines; l++) {
                base -= 8 + rnd.nextFloat() * 6;
                int n = 20 + rnd.nextInt(100);
                for (int i = 0; i < n; i++) {
                    float size = rnd.nextInt(4) == 0 ? 6 + rnd.nextInt(4) : 9 + rnd.nextInt(4);
                    g.add(new Glyph("a", 30 + rnd.nextFloat() * 500, base + (rnd.nextFloat() - 0.5f) * jitter, 4f, size, "Arial", 0xFF000000));
                }
            }
            if (rnd.nextBoolean()) java.util.Collections.shuffle(g, rnd);
            List<TextBlock> blocks = TextLayoutAnalyzer.analyze(0, g);
            int chars = 0;
            for (TextBlock b : blocks) chars += b.text.replace(" ", "").replace("\n", "").length();
            assertEquals("seed " + seed, g.size(), chars);
        }
    }

    /** Words of a styled line (Calibri-like: same size everywhere, style by weight and colour). */
    private static float styled(List<Glyph> out, String text, float x, float baseline, boolean bold, boolean italic, int color, int highlight) {
        float size = 11f;
        float adv = size * 0.5f;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != ' ') out.add(new Glyph(String.valueOf(c), x, baseline, adv, size, "Calibri", color, bold, italic, highlight));
            x += c == ' ' ? size * 0.3f : adv;
        }
        return x;
    }

    /** Document whose titles have the size of the text, told apart only by weight and colour (Qt / Kanwrite export). */
    @Test
    public void titlesOfTheSameSizeAreSeparateBlocks() {
        List<Glyph> g = new ArrayList<>();
        int blue = 0xFF2F5496;
        styled(g, "Un CV qui prouve", 70, 700, true, false, blue, 0);
        float x = styled(g, "Illustrer chaque competence", 100, 682, true, false, 0xFF000000, 0);
        styled(g, " : un projet, un resultat.", x, 682, false, false, 0xFF000000, 0);
        styled(g, "depuis une seule interface dit tout.", 100, 668.3f, false, false, 0xFF000000, 0);
        styled(g, "Mon astuce : un tableau", 77, 650, false, true, 0xFF555555, 0);
        List<TextBlock> blocks = TextLayoutAnalyzer.analyze(0, g);
        assertEquals(3, blocks.size());
        TextBlock title = blocks.get(0);
        assertEquals("Un CV qui prouve", title.text);
        assertTrue(title.bold);
        assertEquals(blue, title.color);
        TextBlock para = blocks.get(1);
        assertEquals("Illustrer chaque competence : un projet, un resultat. depuis une seule interface dit tout.", para.text);
        assertFalse("mostly regular text", para.bold);
        // The bold lead-in is kept as a run.
        StyleRun lead = para.runs.get(0);
        assertTrue(lead.bold);
        assertEquals("Illustrer chaque competence", para.text.substring(lead.start, lead.end));
        assertFalse(para.runs.get(1).bold);
        assertTrue(blocks.get(2).italic);
        assertEquals(0xFF555555, blocks.get(2).color);
    }

    @Test
    public void listItemsAreSeparateBlocks() {
        List<Glyph> g = new ArrayList<>();
        // Vector bullets (small filled disks) on the left of each item, regular pitch inside an item.
        List<float[]> bullets = new ArrayList<>();
        styled(g, "Premier point qui continue", 100, 700, false, false, 0xFF000000, 0);
        styled(g, "sur une seconde ligne.", 100, 686.3f, false, false, 0xFF000000, 0);
        bullets.add(new float[]{93.6f, 702f, 97.9f, 706.3f});
        styled(g, "Second point.", 100, 668.1f, false, false, 0xFF000000, 0);
        bullets.add(new float[]{93.6f, 670f, 97.9f, 674.3f});
        styled(g, "1. Etape une", 90, 640, false, false, 0xFF000000, 0);
        styled(g, "2. Etape deux", 90, 626, false, false, 0xFF000000, 0);
        List<TextBlock> blocks = TextLayoutAnalyzer.analyze(0, g, bullets);
        assertEquals(4, blocks.size());
        assertEquals("Premier point qui continue sur une seconde ligne.", blocks.get(0).text);
        assertEquals("Second point.", blocks.get(1).text);
        assertEquals("1. Etape une", blocks.get(2).text);
        assertEquals("2. Etape deux", blocks.get(3).text);
    }

    @Test
    public void largerGapEndsTheParagraph() {
        List<Glyph> g = new ArrayList<>();
        styled(g, "Ligne un du paragraphe", 70, 700, false, false, 0xFF000000, 0);
        styled(g, "ligne deux du paragraphe", 70, 686.3f, false, false, 0xFF000000, 0);
        styled(g, "Autre paragraphe juste dessous", 70, 668.1f, false, false, 0xFF000000, 0);
        assertEquals(2, TextLayoutAnalyzer.analyze(0, g).size());
    }

    @Test
    public void keepsHighlightsAndDeliberateLineBreaks() {
        List<Glyph> g = new ArrayList<>();
        int yellow = 0xFFFFFF00;
        float x = styled(g, "Commencer au CFA sans employeur,", 100, 700, false, false, 0xFF000000, 0);
        styled(g, " pour une duree limitee", x, 700, false, false, 0xFF000000, yellow);
        styled(g, "par le Code du travail.", 100, 686.3f, false, false, 0xFF000000, yellow);
        styled(g, "Court.", 100, 672.6f, false, false, 0xFF000000, 0);
        styled(g, "Suite du bloc sur toute la largeur de la ligne.", 100, 658.9f, false, false, 0xFF000000, 0);
        List<TextBlock> blocks = TextLayoutAnalyzer.analyze(0, g);
        assertEquals(1, blocks.size());
        TextBlock b = blocks.get(0);
        assertTrue(b.text, b.text.contains("travail.\nCourt.\nSuite"));
        StyleRun hl = null;
        for (StyleRun r : b.runs) if (r.highlight == yellow) hl = r;
        assertNotNull(hl);
        // One highlight across the line break, as in the original.
        assertEquals("pour une duree limitee par le Code du travail.", b.text.substring(hl.start, hl.end));
        assertEquals(4, b.lineCount);
        assertTrue(b.originalWidth > 0);
    }

    @Test
    public void qtFontNamesGiveWeightAndStyle() {
        assertTrue(EditFonts.guessBold("Calibri-0-700"));
        assertFalse(EditFonts.guessBold("Calibri-1-400"));
        assertTrue(EditFonts.guessItalic("Calibri-1-400"));
        assertFalse(EditFonts.guessItalic("Calibri-0-700"));
        assertTrue(new Glyph("a", 0, 0, 1, 11, "Calibri-1-700", 0).bold);
    }

    @Test
    public void tableBordersSeparateCellsAndRows() {
        List<Glyph> g = new ArrayList<>();
        // Two cells close to each other on each row, separated by a vertical border at x = 180.
        styled(g, "Calendrier", 75, 700, true, false, 0xFF000000, 0);
        styled(g, "Commencer tot", 186, 700, false, false, 0xFF000000, 0);
        styled(g, "Lettre", 75, 686.3f, true, false, 0xFF000000, 0);
        styled(g, "Un fait precis", 186, 686.3f, false, false, 0xFF000000, 0);
        List<float[]> rules = new ArrayList<>();
        rules.add(new float[]{180f, 680f, 180.7f, 715f});
        // Horizontal border between the rows, under the left cells.
        rules.add(new float[]{71f, 696f, 300f, 696.7f});
        List<TextBlock> blocks = TextLayoutAnalyzer.analyze(0, g, new ArrayList<>(), rules);
        assertEquals(4, blocks.size());
        for (TextBlock b : blocks) assertFalse(b.text, b.text.contains(" ") && b.text.startsWith("Calendrier"));
        // Without the borders, the gap alone (< 1.6 x size) would join the cells and the rows.
        assertTrue(TextLayoutAnalyzer.analyze(0, g).size() < 4);
    }

    @Test
    public void shortLinesOfABoxKeepTheirBreaks() {
        List<Glyph> g = new ArrayList<>();
        styled(g, "Un paragraphe large qui definit la colonne de texte de la page entiere.", 77, 760, false, false, 0xFF000000, 0);
        styled(g, "Objet : Candidature au poste", 77, 700, false, false, 0xFF000000, 0);
        styled(g, "Bonjour Madame,", 77, 686.3f, false, false, 0xFF000000, 0);
        List<TextBlock> blocks = TextLayoutAnalyzer.analyze(0, g);
        assertEquals("Objet : Candidature au poste\nBonjour Madame,", blocks.get(1).text);
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
