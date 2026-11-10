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
