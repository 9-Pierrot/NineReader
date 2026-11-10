package com.ninereader.edit;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.ninereader.reader.PageTransform;
import com.ninereader.reader.PdfEngine;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import com.tom_roush.pdfbox.text.TextPosition;
import com.tom_roush.pdfbox.util.Matrix;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Finds the editable text blocks of a page: from the PDF glyphs (font, size, colour), or by OCR when
 * the page is a scan without text layer ("recognise the text", MOD-02).
 */
public final class TextBlockExtractor {

    private TextBlockExtractor() {
    }

    /** Collects the glyphs drawn by a page, in user space, with their font and fill colour. */
    static final class GlyphCollector extends PDFTextStripper {
        final List<Glyph> glyphs = new ArrayList<>();

        GlyphCollector() throws IOException {
            super();
        }

        @Override
        protected void processTextPosition(TextPosition t) {
            Matrix m = t.getTextMatrix();
            // Only horizontal text can be edited as blocks.
            if (Math.abs(m.getShearY()) > Math.abs(m.getScaleX()) * 0.2f) return;
            String unicode = t.getUnicode();
            if (unicode == null || unicode.isEmpty()) return;
            int color = 0xFF000000;
            try {
                PDColor c = getGraphicsState().getNonStrokingColor();
                color = 0xFF000000 | (c.toRGB() & 0xFFFFFF);
            } catch (IOException | RuntimeException ignored) {
                // Unknown colour space: keep black.
            }
            String font = t.getFont() == null ? null : t.getFont().getName();
            glyphs.add(new Glyph(unicode, m.getTranslateX(), m.getTranslateY(), t.getWidthDirAdj(), t.getFontSizeInPt(), font, color));
        }
    }

    /** Glyphs of a page (0-based). */
    @WorkerThread
    @NonNull
    public static List<Glyph> glyphs(@NonNull PDDocument doc, int page) throws IOException {
        GlyphCollector collector = new GlyphCollector();
        collector.setStartPage(page + 1);
        collector.setEndPage(page + 1);
        collector.setSortByPosition(false);
        collector.writeText(doc, new StringWriter());
        return collector.glyphs;
    }

    /** Text blocks of a page from its glyphs. */
    @WorkerThread
    @NonNull
    public static List<TextBlock> fromPdf(@NonNull PDDocument doc, int page) throws IOException {
        return TextLayoutAnalyzer.analyze(page, glyphs(doc, page));
    }

    /** Text blocks of a scanned page, recognised offline with ML Kit. */
    @WorkerThread
    @NonNull
    public static List<TextBlock> fromOcr(@NonNull Context context, @NonNull PdfEngine engine, int page) throws IOException {
        float w = engine.pageWidth(page);
        float h = engine.pageHeight(page);
        float scale = Math.min(2500f / Math.max(w, h), 300f / 72f);
        int bw = Math.max(1, Math.round(w * scale));
        int bh = Math.max(1, Math.round(h * scale));
        Bitmap bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        List<TextBlock> out = new ArrayList<>();
        try {
            engine.render(page, bmp, 0, 0, bw, bh, false);
            Text text = Tasks.await(recognizer.process(InputImage.fromBitmap(bmp, 0)));
            PageTransform t = engine.transform(page);
            float[] a = new float[2];
            float[] b = new float[2];
            for (Text.TextBlock block : text.getTextBlocks()) {
                Rect r = block.getBoundingBox();
                if (r == null || block.getText().trim().isEmpty()) continue;
                t.toPage(r.left / (float) bw, r.bottom / (float) bh, a);
                t.toPage(r.right / (float) bw, r.top / (float) bh, b);
                List<Float> heights = new ArrayList<>();
                List<Float> pitches = new ArrayList<>();
                int previousBottom = Integer.MIN_VALUE;
                StringBuilder sb = new StringBuilder();
                for (Text.Line line : block.getLines()) {
                    Rect lr = line.getBoundingBox();
                    if (lr != null) {
                        heights.add(lr.height() / scale);
                        if (previousBottom != Integer.MIN_VALUE) pitches.add((lr.bottom - previousBottom) / scale);
                        previousBottom = lr.bottom;
                    }
                    if (sb.length() > 0) sb.append(' ');
                    sb.append(line.getText());
                }
                Collections.sort(heights);
                float lineHeight = heights.isEmpty() ? 12f : heights.get(heights.size() / 2);
                Collections.sort(pitches);
                float pitch = pitches.isEmpty() ? 0 : pitches.get(pitches.size() / 2);
                float fontSize = Math.max(4f, lineHeight * 0.78f);
                out.add(new TextBlock(page,
                        new float[]{Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[0], b[0]), Math.max(a[1], b[1])},
                        sb.toString(), fontSize, pitch > fontSize ? pitch : 0, EditFonts.Family.SANS, false, false, 0xFF000000, true));
            }
        } catch (ExecutionException e) {
            throw new IOException("OCR failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        } finally {
            recognizer.close();
            bmp.recycle();
        }
        return out;
    }
}
