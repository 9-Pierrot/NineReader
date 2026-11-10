package com.ninereader.pdfops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.ninereader.TestPdfs;
import com.ninereader.reader.PdfEngine;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** PDF operations that need the native renderer or Android APIs (ORG-04, CNV-02, MOD-03, CRE-01..03, CMP-01). */
@RunWith(AndroidJUnit4.class)
public class PdfOpsDeviceTest {

    private final Context context = ApplicationProvider.getApplicationContext();

    @Before
    public void setUp() {
        PDFBoxResourceLoader.init(context);
    }

    @Test
    public void autoCropRemovesWhiteMargins() throws IOException {
        Uri src = TestPdfs.text(context, 2, "crop.pdf");
        File out = PdfOps.crop(context, src, null, null, Arrays.asList(0, 1), PdfOps.Progress.NONE);
        try (PDDocument doc = PDDocument.load(out)) {
            PDRectangle crop = doc.getPage(0).getCropBox();
            Log.i("NineReaderTest", "auto crop box: " + crop);
            assertTrue(crop.getWidth() < 595 && crop.getHeight() < 842);
            assertTrue(crop.getLowerLeftX() > 30); // left margin of the text is 72 pt
        }
    }

    @Test
    public void manualCropUsesMargins() throws IOException {
        Uri src = TestPdfs.text(context, 1, "crop_manual.pdf");
        File out = PdfOps.crop(context, src, null, new float[]{0.1f, 0.1f, 0.1f, 0.1f},
                Collections.singletonList(0), PdfOps.Progress.NONE);
        try (PDDocument doc = PDDocument.load(out)) {
            PDRectangle crop = doc.getPage(0).getCropBox();
            assertEquals(595 * 0.8f, crop.getWidth(), 2f);
            assertEquals(842 * 0.8f, crop.getHeight(), 2f);
        }
    }

    @Test
    public void exportsText() throws IOException {
        Uri src = TestPdfs.text(context, 3, "export.pdf");
        File txt = ExportOps.exportText(context, src, null, Arrays.asList(0, 2), "Page", PdfOps.Progress.NONE);
        String s = new String(Files.readAllBytes(txt.toPath()), StandardCharsets.UTF_8);
        assertTrue(s.contains("Chapitre 1"));
        assertTrue(s.contains("Chapitre 3"));
        assertFalse(s.contains("Chapitre 2"));
        assertTrue(s.contains("----- Page 3 -----"));
    }

    @Test
    public void redactionReallyRemovesContent() throws IOException {
        Uri src = TestPdfs.text(context, 2, "redact.pdf");
        File out;
        try (PdfBoxHelper.Loaded loaded = PdfBoxHelper.load(context, src, null)) {
            Map<Integer, List<float[]>> boxes = new HashMap<>();
            boxes.put(0, Collections.singletonList(new float[]{60, 500, 550, 800}));
            Redaction.apply(context, loaded.doc, src, null, boxes);
            out = PdfBoxHelper.saveToTemp(context, loaded.doc);
        }
        try (PdfEngine e = PdfEngine.open(context, Uri.fromFile(out), null)) {
            assertEquals(2, e.pageCount());
            assertFalse("redacted page has no text layer any more", e.pageText(0).contains("Chapitre 1"));
            assertTrue("other pages are untouched", e.pageText(1).contains("Chapitre 2"));
        }
    }

    @Test
    public void ocrMakesScannedPageSearchable() throws IOException {
        Uri src = TestPdfs.scanned(context);
        try (PdfEngine e = PdfEngine.open(context, src, null)) {
            assertFalse(e.pageText(0).toUpperCase().contains(TestPdfs.OCR_WORD));
        }
        long start = System.currentTimeMillis();
        OcrOp.Result r = OcrOp.run(context, src, null, Collections.singletonList(0), false, PdfOps.Progress.NONE);
        Log.i("NineReaderPerf", "OCR 1 page: " + (System.currentTimeMillis() - start) + " ms");
        assertEquals(1, r.pagesRecognized);
        try (PdfEngine e = PdfEngine.open(context, Uri.fromFile(r.file), null)) {
            String text = e.pageText(0).toUpperCase();
            Log.i("NineReaderTest", "OCR text: " + text.trim());
            assertTrue(text.contains(TestPdfs.OCR_WORD));
        }
        // A page that already has text is skipped.
        OcrOp.Result again = OcrOp.run(context, Uri.fromFile(r.file), null, Collections.singletonList(0), false, PdfOps.Progress.NONE);
        assertEquals(0, again.pagesRecognized);
    }

    @Test
    public void compressionShrinksImages() throws IOException {
        Uri src = TestPdfs.heavyImage(context);
        long before = new File(src.getPath()).length();
        PdfOps.CompressionInfo info = PdfOps.analyze(context, src, null, CompressionLevel.MEDIUM);
        assertEquals(1, info.images);
        File out = PdfOps.compress(context, src, null, CompressionLevel.MEDIUM, PdfOps.Progress.NONE);
        long after = out.length();
        Log.i("NineReaderTest", "compression " + before + " -> " + after + " (estimate " + info.estimatedSize() + ")");
        assertTrue(after < before / 2);
        // The estimate shown to the user is in the right order of magnitude.
        assertTrue(info.estimatedSize() < before && info.estimatedSize() > after / 10);
        try (PdfEngine e = PdfEngine.open(context, Uri.fromFile(out), null)) {
            assertEquals(1, e.pageCount());
        }
    }

    @Test
    public void createsPdfFromImagesAndBlankPdf() throws IOException {
        Uri a = TestPdfs.png(context, 800, 1200);
        Uri b = TestPdfs.png(context, 1600, 900);
        File fromImages = CreateOps.imagesToPdf(context, Arrays.asList(a, b), CreateOps.PageFormat.A4, 24, PdfOps.Progress.NONE);
        try (PdfEngine e = PdfEngine.open(context, Uri.fromFile(fromImages), null)) {
            assertEquals(2, e.pageCount());
            assertTrue(e.pageWidth(0) < e.pageHeight(0));  // portrait image
            assertTrue(e.pageWidth(1) > e.pageHeight(1));  // landscape image
        }
        File blank = CreateOps.blank(context, CreateOps.PageFormat.A5, false, CreateOps.Pattern.GRID, 3);
        try (PdfEngine e = PdfEngine.open(context, Uri.fromFile(blank), null)) {
            assertEquals(3, e.pageCount());
            assertEquals(420f, e.pageWidth(0), 1f);
        }
        File merged = PdfOps.merge(context, Arrays.asList(Uri.fromFile(blank), a), PdfOps.Progress.NONE);
        try (PdfEngine e = PdfEngine.open(context, Uri.fromFile(merged), null)) {
            assertEquals(4, e.pageCount());
        }
    }
}
