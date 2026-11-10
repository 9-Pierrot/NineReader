package com.ninereader.reader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.ninereader.TestPdfs;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.List;

import io.legere.pdfiumandroid.PdfDocument;
import io.legere.pdfiumandroid.PdfPasswordException;

/** Pdfium engine on a real device (LEC-01, LEC-06, LEC-07, LEC-11, LEC-15, LEC-16, §5.1). */
@RunWith(AndroidJUnit4.class)
public class PdfEngineTest {

    private final Context context = ApplicationProvider.getApplicationContext();

    @Test
    public void opensMeasuresAndRendersPages() throws IOException {
        Uri uri = TestPdfs.text(context, 3, "engine.pdf");
        try (PdfEngine e = PdfEngine.open(context, uri, null)) {
            assertEquals(3, e.pageCount());
            assertEquals(595f, e.pageWidth(0), 1f);
            assertEquals(842f, e.pageHeight(0), 1f);
            assertTrue(e.isMeasured(0));
            Bitmap bmp = Bitmap.createBitmap(300, 424, Bitmap.Config.ARGB_8888);
            e.render(0, bmp, 0, 0, 300, 424, true);
            assertTrue("rendered page must contain dark pixels", darkPixels(bmp) > 200);
            // Offset rendering (tile): the bottom-right quarter is mostly blank.
            Bitmap tile = Bitmap.createBitmap(150, 212, Bitmap.Config.ARGB_8888);
            e.render(0, tile, 150, 212, 300, 424, true);
            assertTrue(darkPixels(tile) < darkPixels(bmp));
        }
    }

    @Test
    public void extractsTextAndLocatesWords() throws IOException {
        Uri uri = TestPdfs.text(context, 2, "text.pdf");
        try (PdfEngine e = PdfEngine.open(context, uri, null)) {
            String text = e.pageText(1);
            assertTrue(text.contains("Chapitre 2"));
            assertTrue(e.hasCachedText(1));
            List<TextSearch.Match> matches = TextSearch.findInPage(1, text, TextSearch.normalizeQuery(TestPdfs.WORD), 10);
            assertEquals(1, matches.size());
            TextSearch.Match m = matches.get(0);
            List<RectF> rects = e.textRects(1, m.start, m.length);
            assertFalse(rects.isEmpty());
            RectF r = rects.get(0);
            int idx = e.charIndexAt(1, r.centerX(), r.centerY(), 2);
            assertTrue("char index " + idx + " within match", idx >= m.start && idx < m.start + m.length);
            // Page ↔ view mapping consistency.
            PageTransform t = e.transform(1);
            float[] n = t.rectToNormalized(r.left, r.top, r.right, r.bottom);
            assertTrue(n[0] > 0 && n[2] < 1 && n[1] > 0 && n[3] < 1);
        }
    }

    @Test
    public void outlineAndLinks() throws IOException {
        Uri uri = TestPdfs.text(context, 4, "outline.pdf");
        try (PdfEngine e = PdfEngine.open(context, uri, null)) {
            List<OutlineItem> outline = e.outline();
            assertEquals(4, outline.size());
            assertEquals("Chapitre 3", outline.get(2).title);
            assertEquals(2, outline.get(2).page);
            List<PdfDocument.Link> links = e.links(0);
            assertEquals(1, links.size());
            assertEquals(Integer.valueOf(3), links.get(0).getDestPageIdx());
        }
    }

    @Test
    public void passwordProtectedDocuments() throws IOException {
        Uri uri = TestPdfs.encrypted(context, "secret");
        try (PdfEngine ignored = PdfEngine.open(context, uri, null)) {
            fail("A password must be required");
        } catch (PdfPasswordException expected) {
            // ok
        }
        try (PdfEngine ignored = PdfEngine.open(context, uri, "wrong")) {
            fail("Wrong password must be rejected");
        } catch (PdfPasswordException expected) {
            // ok
        }
        try (PdfEngine e = PdfEngine.open(context, uri, "secret")) {
            assertEquals(2, e.pageCount());
            assertTrue(e.pageText(0).contains("Chapitre 1"));
        }
    }

    @Test(expected = IOException.class)
    public void corruptedDocumentFailsCleanly() throws IOException {
        PdfEngine.open(context, TestPdfs.corrupted(context), null).close();
    }

    /** §5.1: first page of a 100-page document displayed in under 1 s. */
    @Test
    public void openAndFirstRenderOf100PagesUnderOneSecond() throws IOException {
        Uri uri = TestPdfs.text(context, 100, "hundred.pdf");
        PdfEngine.warmUp(context);
        long start = SystemClock.elapsedRealtime();
        try (PdfEngine e = PdfEngine.open(context, uri, null)) {
            for (int i = 0; i < e.pageCount(); i++) e.ensureSize(i);
            Bitmap bmp = Bitmap.createBitmap(1080, 1527, Bitmap.Config.ARGB_8888);
            e.render(0, bmp, 0, 0, 1080, 1527, true);
            long elapsed = SystemClock.elapsedRealtime() - start;
            Log.i("NineReaderPerf", "open + measure 100 pages + first render: " + elapsed + " ms");
            assertTrue("took " + elapsed + " ms", elapsed < 1000);
        }
    }

    @Test
    public void searchesAThousandPagesQuickly() throws IOException {
        Uri uri = TestPdfs.text(context, 300, "large.pdf");
        try (PdfEngine e = PdfEngine.open(context, uri, null)) {
            long start = SystemClock.elapsedRealtime();
            int found = 0;
            String q = TextSearch.normalizeQuery(TestPdfs.WORD);
            for (int i = 0; i < 100; i++) found += TextSearch.findInPage(i, e.pageText(i), q, 100).size();
            long elapsed = SystemClock.elapsedRealtime() - start;
            Log.i("NineReaderPerf", "search 100 pages: " + elapsed + " ms");
            assertEquals(100, found);
            assertTrue("first results must come in < 1 s, took " + elapsed, elapsed < 1000);
        }
    }

    static int darkPixels(Bitmap bmp) {
        int[] px = new int[bmp.getWidth() * bmp.getHeight()];
        bmp.getPixels(px, 0, bmp.getWidth(), 0, 0, bmp.getWidth(), bmp.getHeight());
        int n = 0;
        for (int c : px) if (((c >> 16) & 0xFF) < 128) n++;
        return n;
    }
}
