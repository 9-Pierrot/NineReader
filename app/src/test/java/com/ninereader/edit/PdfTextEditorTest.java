package com.ninereader.edit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.ninereader.annotations.Annotation;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/** Real text editing with PdfBox (MOD-02): original glyphs removed, new text embedded, layout preserved. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = android.app.Application.class)
public class PdfTextEditorTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        PDFBoxResourceLoader.init(context);
    }

    /** Page with a title, a 2-line paragraph and a footer line made of two Tj operators. */
    private PDDocument sample() throws IOException {
        PDDocument doc = new PDDocument();
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA_BOLD, 20);
            cs.newLineAtOffset(72, 760);
            cs.showText("Contrat de location");
            cs.endText();
            cs.beginText();
            cs.setFont(PDType1Font.TIMES_ROMAN, 12);
            cs.setLeading(14);
            cs.newLineAtOffset(72, 700);
            cs.showText("Le loyer mensuel est fixe a 800 euros");
            cs.newLine();
            cs.showText("payable le premier jour du mois.");
            cs.endText();
            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA, 10);
            cs.newLineAtOffset(72, 100);
            cs.showText("Paraphe: ");
            cs.showText("FIN");
            cs.endText();
        }
        return doc;
    }

    private static String text(PDDocument doc) throws IOException {
        return new PDFTextStripper().getText(doc);
    }

    private static PDDocument roundTrip(PDDocument doc) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.save(out);
        doc.close();
        return PDDocument.load(out.toByteArray());
    }

    @Test
    public void detectsBlocksWithFonts() throws IOException {
        try (PDDocument doc = sample()) {
            List<TextBlock> blocks = TextBlockExtractor.fromPdf(doc, 0);
            assertEquals(3, blocks.size());
            assertEquals("Contrat de location", blocks.get(0).text);
            assertTrue(blocks.get(0).bold);
            assertEquals(20f, blocks.get(0).fontSize, 0.5f);
            assertEquals("Le loyer mensuel est fixe a 800 euros payable le premier jour du mois.", blocks.get(1).text);
            assertEquals(EditFonts.Family.SERIF, blocks.get(1).family);
            assertEquals(12f, blocks.get(1).fontSize, 0.5f);
        }
    }

    @Test
    public void replacesTheOriginalTextForReal() throws IOException {
        PDDocument doc = sample();
        TextBlock para = TextBlockExtractor.fromPdf(doc, 0).get(1);
        Annotation edit = new Annotation();
        edit.type = Annotation.Type.TEXT_EDIT;
        edit.page = 0;
        edit.originalBounds = para.bounds.clone();
        edit.bounds = para.bounds.clone();
        edit.originalText = para.text;
        edit.text = "Le loyer mensuel est fixé à 950 €, payable le 5 du mois.";
        edit.fontSize = para.fontSize;
        edit.lineHeight = para.lineHeight;
        edit.fontFamily = para.family.name();
        PdfTextEditor.Result r = PdfTextEditor.apply(context, doc, Collections.singletonList(edit), Collections.emptySet());
        assertEquals(1, r.edits);
        assertEquals(2, r.removedOperators);
        PDDocument saved = roundTrip(doc);
        try {
            String t = text(saved);
            assertFalse("old text must be gone from the file: " + t, t.contains("800 euros"));
            assertTrue(t, t.contains("fixé à 950 €"));
            assertTrue("other blocks untouched", t.contains("Contrat de location"));
            assertTrue(t.contains("Paraphe:"));
            // The new paragraph is detected again as a block at the same place.
            TextBlock again = null;
            for (TextBlock b : TextBlockExtractor.fromPdf(saved, 0)) if (b.text.contains("950")) again = b;
            assertNotNull(again);
            assertEquals(para.bounds[0], again.bounds[0], 2f);
            assertEquals(para.bounds[3], again.bounds[3], 0.5f);
            // Same first baseline and same line pitch as the original paragraph.
            assertEquals(14f, again.lineHeight, 0.01f);
            assertEquals(700f, glyphY(saved, "9"), 0.01f);
        } finally {
            saved.close();
        }
    }

    @Test
    public void removingPartOfALineKeepsTheRestInPlace() throws IOException {
        PDDocument doc = sample();
        // Position of "FIN" before editing.
        float before = glyphX(doc, "F");
        // Remove only "Paraphe: " (first Tj of the footer line).
        int removed = PdfTextEditor.removeText(doc, doc.getPage(0), Collections.singletonList(new float[]{70, 95, 112, 110}));
        assertEquals(1, removed);
        PDDocument saved = roundTrip(doc);
        try {
            String t = text(saved);
            assertFalse(t.contains("Paraphe"));
            assertTrue(t.contains("FIN"));
            assertEquals("FIN must not move", before, glyphX(saved, "F"), 0.01f);
        } finally {
            saved.close();
        }
    }

    @Test
    public void deletingABlockAndAddingNewText() throws IOException {
        PDDocument doc = sample();
        TextBlock title = TextBlockExtractor.fromPdf(doc, 0).get(0);
        Annotation delete = new Annotation();
        delete.type = Annotation.Type.TEXT_EDIT;
        delete.page = 0;
        delete.originalBounds = title.bounds.clone();
        delete.bounds = title.bounds.clone();
        delete.text = "";
        Annotation added = new Annotation();
        added.type = Annotation.Type.TEXT_EDIT;
        added.page = 0;
        added.bounds = new float[]{72, 300, 300, 320};
        added.text = "Nouvelle clause ajoutée";
        added.fontSize = 14;
        added.fontFamily = "SERIF";
        added.bold = true;
        PdfTextEditor.apply(context, doc, java.util.Arrays.asList(delete, added), Collections.emptySet());
        PDDocument saved = roundTrip(doc);
        try {
            String t = text(saved);
            assertFalse(t.contains("Contrat de location"));
            assertTrue(t.contains("Nouvelle clause ajoutée"));
        } finally {
            saved.close();
        }
    }

    @Test
    public void textInsideFormXObjectIsCoveredWhenItCannotBeRemoved() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject form =
                    new com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject(doc);
            form.setBBox(new PDRectangle(0, 0, 595, 842));
            form.setResources(new com.tom_roush.pdfbox.pdmodel.PDResources());
            try (PDPageContentStream fs = new PDPageContentStream(doc, form, form.getContentStream().createOutputStream())) {
                fs.beginText();
                fs.setFont(PDType1Font.HELVETICA, 12);
                fs.newLineAtOffset(72, 400);
                fs.showText("Texte dans un formulaire");
                fs.endText();
            }
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawForm(form);
            }
            TextBlock b = TextBlockExtractor.fromPdf(doc, 0).get(0);
            assertEquals("Texte dans un formulaire", b.text);
            List<float[]> regions = Collections.singletonList(b.bounds);
            PdfTextEditor.removeText(doc, page, regions);
            assertEquals(1, PdfTextEditor.regionsStillWithText(doc, 0, regions).size());
        }
    }

    @Test
    public void removedTextLeavesNoRegionToCover() throws IOException {
        try (PDDocument doc = sample()) {
            TextBlock para = TextBlockExtractor.fromPdf(doc, 0).get(1);
            List<float[]> regions = Collections.singletonList(para.bounds);
            PdfTextEditor.removeText(doc, doc.getPage(0), regions);
            assertTrue(PdfTextEditor.regionsStillWithText(doc, 0, regions).isEmpty());
        }
    }

    @Test
    public void wrapsLongText() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            com.tom_roush.pdfbox.pdmodel.font.PDFont font = PDType1Font.HELVETICA;
            List<String> lines = PdfTextEditor.wrap("un deux trois quatre cinq six sept\nhuit", font, 12, 60);
            assertTrue(lines.size() >= 4);
            assertEquals("huit", lines.get(lines.size() - 1));
        }
    }

    /**
     * Page built like a Qt / Kanwrite export: every text has the same size, the title is told apart by
     * weight and colour, a paragraph starts with a bold lead-in, part of it is highlighted by a yellow
     * rectangle drawn under the text, and list items have vector bullets.
     */
    private PDDocument styledSample() throws IOException {
        PDDocument doc = new PDDocument();
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            // Highlight under "pour une duree limitee" (second part of the first item line).
            float start = 100 + PDType1Font.HELVETICA_BOLD.getStringWidth("A la rentree") / 1000f * 11
                    + PDType1Font.HELVETICA.getStringWidth(" : commencer au CFA ") / 1000f * 11;
            float width = PDType1Font.HELVETICA.getStringWidth("pour une duree limitee") / 1000f * 11;
            cs.setNonStrokingColor(1f, 1f, 0f);
            cs.addRect(start - 0.5f, 697, width + 1, 13.7f);
            cs.fill();
            // Vector bullets.
            cs.setNonStrokingColor(0f, 0f, 0f);
            cs.addRect(93.6f, 702f, 4.3f, 4.3f);
            cs.fill();
            cs.addRect(93.6f, 670f, 4.3f, 4.3f);
            cs.fill();
            cs.beginText();
            cs.setNonStrokingColor(0x2F / 255f, 0x54 / 255f, 0x96 / 255f);
            cs.setFont(PDType1Font.HELVETICA_BOLD, 11);
            cs.newLineAtOffset(70, 730);
            cs.showText("Le calendrier");
            cs.endText();
            cs.beginText();
            cs.setNonStrokingColor(0f, 0f, 0f);
            cs.setFont(PDType1Font.HELVETICA_BOLD, 11);
            cs.newLineAtOffset(100, 700);
            cs.showText("A la rentree");
            cs.setFont(PDType1Font.HELVETICA, 11);
            cs.showText(" : commencer au CFA ");
            cs.showText("pour une duree limitee");
            cs.endText();
            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA, 11);
            cs.newLineAtOffset(100, 668);
            cs.showText("Second point de la liste.");
            cs.endText();
        }
        return doc;
    }

    private static int countFills(PDDocument doc, int rgb) throws IOException {
        int n = 0;
        for (TextBlockExtractor.Fill f : TextBlockExtractor.fills(doc, 0).fills) if ((f.color & 0xFFFFFF) == rgb) n++;
        return n;
    }

    @Test
    public void detectsTitlesStylesHighlightsAndListItems() throws IOException {
        try (PDDocument doc = styledSample()) {
            List<TextBlock> blocks = TextBlockExtractor.fromPdf(doc, 0);
            assertEquals(3, blocks.size());
            TextBlock title = blocks.get(0);
            assertEquals("Le calendrier", title.text);
            assertTrue(title.bold);
            assertEquals(0xFF2F5496, title.color);
            TextBlock item = blocks.get(1);
            assertEquals("A la rentree : commencer au CFA pour une duree limitee", item.text);
            StyleRun lead = item.runs.get(0);
            assertTrue(lead.bold);
            assertEquals("A la rentree", item.text.substring(lead.start, lead.end));
            StyleRun hl = null;
            for (StyleRun r : item.runs) if (r.highlight != 0) hl = r;
            assertNotNull("highlight detected", hl);
            assertEquals(0xFFFFFF00, hl.highlight);
            assertEquals("pour une duree limitee", item.text.substring(hl.start, hl.end).trim());
            assertEquals(0xFFFFFFFF, item.background);
            assertEquals("Second point de la liste.", blocks.get(2).text);
        }
    }

    @Test
    public void editKeepsStylesAndMovesTheHighlightWithTheText() throws IOException {
        PDDocument doc = styledSample();
        TextBlock item = TextBlockExtractor.fromPdf(doc, 0).get(1);
        Annotation edit = new Annotation();
        edit.type = Annotation.Type.TEXT_EDIT;
        edit.page = 0;
        edit.originalBounds = item.bounds.clone();
        edit.bounds = item.bounds.clone();
        edit.bounds[2] = 520;
        edit.originalText = item.text;
        // Words inserted before the highlight: it must follow its words, not stay at the old place.
        String inserted = " sans employeur";
        int at = item.text.indexOf(" pour");
        edit.text = item.text.substring(0, at) + inserted + item.text.substring(at);
        edit.runs = new java.util.ArrayList<>();
        for (StyleRun r : item.runs) {
            StyleRun c = r.copy();
            if (c.start >= at) c.start += inserted.length();
            if (c.end > at) c.end += inserted.length();
            edit.runs.add(c);
        }
        edit.fontSize = item.fontSize;
        edit.lineHeight = item.lineHeight;
        edit.fontFamily = item.family.name();
        edit.bold = item.bold;
        edit.color = item.color;
        assertEquals(1, countFills(doc, 0xFFFF00));
        PdfTextEditor.apply(context, doc, Collections.singletonList(edit), Collections.emptySet());
        PDDocument saved = roundTrip(doc);
        try {
            // The original rectangle is gone, a new one is drawn under the moved words.
            List<TextBlockExtractor.Fill> yellow = new java.util.ArrayList<>();
            for (TextBlockExtractor.Fill f : TextBlockExtractor.fills(saved, 0).fills) if ((f.color & 0xFFFFFF) == 0xFFFF00) yellow.add(f);
            assertEquals(1, yellow.size());
            assertTrue("highlight moved right with its words: " + yellow.get(0).box[0], yellow.get(0).box[0] > 260);
            // Bullets kept.
            assertEquals(2, countFills(saved, 0x000000));
            TextBlock again = null;
            for (TextBlock b : TextBlockExtractor.fromPdf(saved, 0)) if (b.text.contains("employeur")) again = b;
            assertNotNull(again);
            assertEquals(edit.text, again.text);
            assertTrue("bold lead-in kept", again.runs.get(0).bold);
            assertEquals("A la rentree", again.text.substring(again.runs.get(0).start, again.runs.get(0).end));
            StyleRun hl = null;
            for (StyleRun r : again.runs) if (r.highlight != 0) hl = r;
            assertNotNull(hl);
            assertEquals("pour une duree limitee", again.text.substring(hl.start, hl.end).trim());
            // Title untouched, still blue and bold.
            TextBlock title = TextBlockExtractor.fromPdf(saved, 0).get(0);
            assertEquals(0xFF2F5496, title.color);
            assertTrue(title.bold);
        } finally {
            saved.close();
        }
    }

    @Test
    public void horizontalScaleKeepsTheOriginalWidth() throws IOException {
        PDDocument doc = sample();
        PDDocument copy = sample();
        TextBlock title = TextBlockExtractor.fromPdf(doc, 0).get(0);
        Annotation edit = new Annotation();
        edit.type = Annotation.Type.TEXT_EDIT;
        edit.page = 0;
        edit.originalBounds = title.bounds.clone();
        edit.bounds = title.bounds.clone();
        edit.text = title.text;
        edit.fontSize = title.fontSize;
        edit.fontFamily = title.family.name();
        edit.bold = true;
        edit.textScaleX = 1f;
        PdfTextEditor.apply(context, doc, Collections.singletonList(edit), Collections.emptySet());
        Annotation narrow = edit.copy();
        narrow.textScaleX = 0.8f;
        PdfTextEditor.apply(context, copy, Collections.singletonList(narrow), Collections.emptySet());
        PDDocument a = roundTrip(doc);
        PDDocument b = roundTrip(copy);
        try {
            float wa = TextBlockExtractor.fromPdf(a, 0).get(0).originalWidth;
            float wb = TextBlockExtractor.fromPdf(b, 0).get(0).originalWidth;
            assertEquals(0.8f, wb / wa, 0.03f);
        } finally {
            a.close();
            b.close();
        }
    }

    @Test
    public void recognisesLigaturesWithoutUnicodeFromTheirWidth() {
        // Widths (em) of Calibri regular, and of its "ti" / "tt" ligatures exported without ToUnicode.
        java.util.Map<Character, Float> w = new java.util.HashMap<>();
        w.put('t', 0.335f);
        w.put('i', 0.229f);
        w.put('f', 0.305f);
        w.put('j', 0.239f);
        assertEquals("ti", TextBlockExtractor.GlyphCollector.recognise(w, 0.557f));
        assertEquals("tt", TextBlockExtractor.GlyphCollector.recognise(w, 0.634f));
        assertEquals(null, TextBlockExtractor.GlyphCollector.recognise(w, 0.9f));
        assertEquals("ffi", TextBlockExtractor.decomposeLigatures("ﬃ"));
        assertEquals("chiffres", TextBlockExtractor.decomposeLigatures("chiﬀres"));
    }

    /** Baseline of the first glyph matching a string. */
    private static float glyphY(PDDocument doc, String unicode) throws IOException {
        for (Glyph g : TextBlockExtractor.glyphs(doc, 0)) {
            if (g.text.equals(unicode)) return g.baseline;
        }
        throw new AssertionError(unicode + " not found");
    }

    /** x of the first glyph matching a string. */
    private static float glyphX(PDDocument doc, String unicode) throws IOException {
        for (Glyph g : TextBlockExtractor.glyphs(doc, 0)) {
            if (g.text.equals(unicode)) return g.x;
        }
        throw new AssertionError(unicode + " not found");
    }
}
