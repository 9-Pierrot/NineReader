package com.ninereader.pdfops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.ninereader.annotations.Annotation;
import com.ninereader.annotations.AnnotationExporter;
import com.ninereader.annotations.FormField;
import com.ninereader.annotations.PdfAnnotationWriter;
import com.ninereader.annotations.PdfFormReader;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm;
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox;
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/** PdfBox based operations (pdfops), run on the JVM with Robolectric. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = android.app.Application.class)
public class PdfOpsTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        PDFBoxResourceLoader.init(context);
    }

    /** Creates a PDF with pages of distinct widths (100, 200, 300...) and returns its file URI. */
    private Uri samplePdf(int pages) throws IOException {
        File f = File.createTempFile("sample", ".pdf", context.getCacheDir());
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pages; i++) doc.addPage(new PDPage(new PDRectangle(100 * (i + 1), 500)));
            doc.save(f);
        }
        return Uri.fromFile(f);
    }

    private static float width(PDDocument doc, int i) {
        return doc.getPage(i).getMediaBox().getWidth();
    }

    @Test
    public void organizeReordersRotatesDuplicatesAndInserts() throws IOException {
        Uri src = samplePdf(3);
        PagePlan plan = PagePlan.decode("2:90,0:0,0:0,-1:0");
        File out = PdfOps.organize(context, src, null, plan);
        try (PDDocument doc = PDDocument.load(out)) {
            assertEquals(4, doc.getNumberOfPages());
            assertEquals(300f, width(doc, 0), 0.1f);
            assertEquals(90, doc.getPage(0).getRotation());
            assertEquals(100f, width(doc, 1), 0.1f);
            assertEquals(100f, width(doc, 2), 0.1f);
            // Blank page takes the size of the previous page.
            assertEquals(100f, width(doc, 3), 0.1f);
            assertNull(doc.getDocumentCatalog().getDocumentOutline());
        }
    }

    @Test
    public void extractRange() throws IOException {
        Uri src = samplePdf(5);
        File out = PdfOps.organize(context, src, null, PagePlan.of(com.ninereader.util.PageRangeParser.parse("2-3", 5)));
        try (PDDocument doc = PDDocument.load(out)) {
            assertEquals(2, doc.getNumberOfPages());
            assertEquals(200f, width(doc, 0), 0.1f);
            assertEquals(300f, width(doc, 1), 0.1f);
        }
    }

    @Test
    public void mergeConcatenatesDocuments() throws IOException {
        Uri a = samplePdf(2);
        Uri b = samplePdf(3);
        File out = PdfOps.merge(context, Arrays.asList(a, b), PdfOps.Progress.NONE);
        try (PDDocument doc = PDDocument.load(out)) {
            assertEquals(5, doc.getNumberOfPages());
            assertEquals(200f, width(doc, 1), 0.1f);
            assertEquals(100f, width(doc, 2), 0.1f);
        }
    }

    @Test
    public void protectAndUnprotect() throws IOException {
        Uri src = samplePdf(1);
        File protectedFile = PdfOps.protect(context, src, null, "secret", null, false, false);
        try {
            PDDocument.load(protectedFile).close();
            throw new AssertionError("A password should be required");
        } catch (InvalidPasswordException expected) {
            // ok
        }
        try (PDDocument doc = PDDocument.load(protectedFile, "secret")) {
            assertTrue(doc.isEncrypted());
            assertEquals(256, doc.getEncryption().getLength());
            assertTrue(!doc.getCurrentAccessPermission().canPrint() || doc.getCurrentAccessPermission().isOwnerPermission());
        }
        File clear = PdfOps.unprotect(context, Uri.fromFile(protectedFile), "secret");
        try (PDDocument doc = PDDocument.load(clear)) {
            assertTrue(!doc.isEncrypted());
        }
    }

    @Test(expected = InvalidPasswordException.class)
    public void unprotectWithWrongPasswordFails() throws IOException {
        Uri src = samplePdf(1);
        File protectedFile = PdfOps.protect(context, src, null, "secret", "owner", true, true);
        PdfOps.unprotect(context, Uri.fromFile(protectedFile), "wrong");
    }

    @Test
    public void compressionAnalysisOfDocumentWithoutImages() throws IOException {
        Uri src = samplePdf(2);
        PdfOps.CompressionInfo info = PdfOps.analyze(context, src, null, CompressionLevel.MEDIUM);
        assertEquals(0, info.images);
        assertTrue(info.estimatedSize() > 0);
        File out = PdfOps.compress(context, src, null, CompressionLevel.HIGH, PdfOps.Progress.NONE);
        try (PDDocument doc = PDDocument.load(out)) {
            assertEquals(2, doc.getNumberOfPages());
        }
    }

    @Test(expected = PdfOps.CancelledException.class)
    public void cancellation() throws IOException {
        Uri a = samplePdf(1);
        PdfOps.merge(context, Arrays.asList(a, a), new PdfOps.Progress() {
            @Override
            public void report(int done, int total) {
                // no-op
            }

            @Override
            public boolean isCancelled() {
                return true;
            }
        });
    }

    @Test
    public void writesStandardAnnotations() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(PDRectangle.A4));
            List<Annotation> list = new ArrayList<>();
            Annotation h = new Annotation();
            h.type = Annotation.Type.HIGHLIGHT;
            h.rects.add(new float[]{50, 700, 200, 715});
            h.text = "important";
            list.add(h);
            Annotation note = new Annotation();
            note.type = Annotation.Type.NOTE;
            note.bounds = new float[]{100, 600, 120, 620};
            note.text = "commentaire";
            list.add(note);
            Annotation ink = new Annotation();
            ink.type = Annotation.Type.INK;
            ink.strokes.add(new float[]{10, 10, 20, 30, 40, 20});
            list.add(ink);
            Annotation rect = new Annotation();
            rect.type = Annotation.Type.RECT;
            rect.bounds = new float[]{300, 300, 400, 350};
            list.add(rect);
            Annotation arrow = new Annotation();
            arrow.type = Annotation.Type.ARROW;
            arrow.bounds = new float[]{10, 400, 200, 450};
            list.add(arrow);
            Annotation whiteout = new Annotation();
            whiteout.type = Annotation.Type.WHITEOUT;
            whiteout.bounds = new float[]{0, 0, 50, 50};
            list.add(whiteout);
            Annotation outOfRange = new Annotation();
            outOfRange.page = 7;
            list.add(outOfRange);

            PdfAnnotationWriter.Result r = PdfAnnotationWriter.apply(doc, list, Collections.emptyMap());
            assertEquals(6, r.written);
            assertEquals(1, r.failures.size());
            List<String> subtypes = new ArrayList<>();
            for (PDAnnotation a : doc.getPage(0).getAnnotations()) subtypes.add(a.getSubtype());
            assertEquals(Arrays.asList("Highlight", "Text", "Ink", "Square", "Line"), subtypes);
            for (PDAnnotation a : doc.getPage(0).getAnnotations()) {
                assertTrue("appearance for " + a.getSubtype(), a.getAppearance() != null);
            }
            List<AnnotationExporter.Entry> existing = PdfAnnotationWriter.existing(doc);
            assertEquals(5, existing.size());
            assertEquals("important", existing.get(0).text);
        }
    }

    @Test
    public void readsAndFillsForms() throws IOException {
        File f = File.createTempFile("form", ".pdf", context.getCacheDir());
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDAcroForm form = new PDAcroForm(doc);
            doc.getDocumentCatalog().setAcroForm(form);
            form.setDefaultAppearance("/Helv 0 Tf 0 g");
            com.tom_roush.pdfbox.pdmodel.PDResources dr = new com.tom_roush.pdfbox.pdmodel.PDResources();
            dr.put(com.tom_roush.pdfbox.cos.COSName.getPDFName("Helv"), com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA);
            form.setDefaultResources(dr);
            PDTextField name = new PDTextField(form);
            name.setPartialName("name");
            name.setDefaultAppearance("/Helv 12 Tf 0 g");
            PDAnnotationWidget w = name.getWidgets().get(0);
            w.setRectangle(new PDRectangle(50, 700, 200, 20));
            w.setPage(page);
            page.getAnnotations().add(w);
            form.getFields().add(name);
            PDCheckBox agree = new PDCheckBox(form);
            agree.setPartialName("agree");
            PDAnnotationWidget cw = agree.getWidgets().get(0);
            cw.setRectangle(new PDRectangle(50, 650, 15, 15));
            page.getAnnotations().add(cw);
            form.getFields().add(agree);
            doc.save(f);
        }
        try (PDDocument doc = PDDocument.load(f)) {
            List<FormField> fields = PdfFormReader.read(doc);
            assertEquals(2, fields.size());
            assertEquals(FormField.Kind.TEXT, fields.get(0).kind);
            assertEquals(0, fields.get(0).widgets.get(0).page);
            assertEquals(FormField.Kind.CHECKBOX, fields.get(1).kind);
            Map<String, String> values = new HashMap<>();
            values.put("name", "Alice Martin");
            values.put("missing", "x");
            PdfAnnotationWriter.Result r = PdfAnnotationWriter.apply(doc, Collections.emptyList(), values);
            assertEquals(1, r.written);
            assertEquals(1, r.failures.size());
            assertEquals("Alice Martin", doc.getDocumentCatalog().getAcroForm().getField("name").getValueAsString());
        }
    }
}
