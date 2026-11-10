package com.ninereader.pdfops;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.ninereader.annotations.PdfAnnotationWriter;
import com.ninereader.reader.PageTransform;
import com.ninereader.reader.PdfEngine;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.font.PDFont;
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode;
import com.tom_roush.pdfbox.util.Matrix;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * OCR (CRE-03): makes a scanned PDF searchable by adding an invisible text layer, fully offline with
 * the bundled ML Kit Latin recogniser. Pages that already contain text are skipped.
 */
public final class OcrOp {

    /** Rendering resolution used for recognition. */
    static final int OCR_DPI = 300;
    static final int MAX_DIMENSION = 3000;

    private OcrOp() {
    }

    /** A recognised word: text and its box in display-normalised coordinates [0..1]. */
    static final class Word {
        final String text;
        final float left;
        final float top;
        final float right;
        final float bottom;

        Word(String text, float left, float top, float right, float bottom) {
            this.text = text;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }

    /** Result of the OCR: output file and number of processed pages. */
    public static final class Result {
        @NonNull
        public final File file;
        public final int pagesRecognized;

        Result(@NonNull File file, int pagesRecognized) {
            this.file = file;
            this.pagesRecognized = pagesRecognized;
        }
    }

    @WorkerThread
    @NonNull
    public static Result run(@NonNull Context context, @NonNull Uri src, @Nullable String password,
                             @NonNull List<Integer> pages, boolean force, @NonNull PdfOps.Progress progress) throws IOException {
        List<List<Word>> words = new ArrayList<>();
        List<PageTransform> transforms = new ArrayList<>();
        List<Integer> recognized = new ArrayList<>();
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        try (PdfEngine engine = PdfEngine.open(context, src, password)) {
            int i = 0;
            for (int p : pages) {
                PdfOps.check(progress);
                progress.report(i++, pages.size() + 1);
                if (!force && hasTextLayer(engine.pageText(p))) continue;
                float w = engine.pageWidth(p);
                float h = engine.pageHeight(p);
                float scale = OCR_DPI / 72f;
                int bw = Math.round(w * scale);
                int bh = Math.round(h * scale);
                float limit = MAX_DIMENSION / (float) Math.max(bw, bh);
                if (limit < 1f) {
                    bw = Math.round(bw * limit);
                    bh = Math.round(bh * limit);
                }
                Bitmap bmp = Bitmap.createBitmap(Math.max(1, bw), Math.max(1, bh), Bitmap.Config.ARGB_8888);
                engine.render(p, bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), false);
                List<Word> pageWords = new ArrayList<>();
                try {
                    Text text = Tasks.await(recognizer.process(InputImage.fromBitmap(bmp, 0)));
                    for (Text.TextBlock block : text.getTextBlocks()) {
                        for (Text.Line line : block.getLines()) {
                            for (Text.Element el : line.getElements()) {
                                Rect r = el.getBoundingBox();
                                if (r == null || el.getText().trim().isEmpty()) continue;
                                pageWords.add(new Word(el.getText(), r.left / (float) bmp.getWidth(), r.top / (float) bmp.getHeight(),
                                        r.right / (float) bmp.getWidth(), r.bottom / (float) bmp.getHeight()));
                            }
                        }
                    }
                } catch (ExecutionException e) {
                    throw new IOException("OCR failed", e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new PdfOps.CancelledException();
                } finally {
                    bmp.recycle();
                }
                if (!pageWords.isEmpty()) {
                    words.add(pageWords);
                    transforms.add(engine.transform(p));
                    recognized.add(p);
                }
            }
        } finally {
            recognizer.close();
        }
        try (PdfBoxHelper.Loaded loaded = PdfBoxHelper.load(context, src, password)) {
            PDDocument doc = loaded.doc;
            PDFont font = PdfBoxHelper.unicodeFont(doc);
            for (int k = 0; k < recognized.size(); k++) {
                addTextLayer(doc, doc.getPage(recognized.get(k)), font, words.get(k), transforms.get(k));
            }
            progress.report(pages.size() + 1, pages.size() + 1);
            return new Result(PdfBoxHelper.saveKeepingProtection(context, doc, password), recognized.size());
        }
    }

    /** Whether a page already has a text layer (a previous OCR, or a born-digital page). */
    static boolean hasTextLayer(@NonNull String pageText) {
        int letters = 0;
        for (int i = 0; i < pageText.length() && letters < 20; i++) {
            if (Character.isLetterOrDigit(pageText.charAt(i))) letters++;
        }
        return letters >= 20;
    }

    /** Writes invisible text (rendering mode 3) aligned on the recognised word boxes. */
    static void addTextLayer(@NonNull PDDocument doc, @NonNull PDPage page, @NonNull PDFont font,
                             @NonNull List<Word> words, @NonNull PageTransform t) throws IOException {
        float[] p0 = new float[2];
        float[] p1 = new float[2];
        float[] p2 = new float[2];
        try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
            cs.beginText();
            cs.setRenderingMode(RenderingMode.NEITHER);
            for (Word w : words) {
                String s = PdfAnnotationWriter.encodable(font, w.text);
                t.toPage(w.left, w.bottom, p0);
                t.toPage(w.right, w.bottom, p1);
                t.toPage(w.left, w.top, p2);
                float ux = p1[0] - p0[0];
                float uy = p1[1] - p0[1];
                float vx = p2[0] - p0[0];
                float vy = p2[1] - p0[1];
                float width = (float) Math.hypot(ux, uy);
                float height = (float) Math.hypot(vx, vy);
                if (width < 0.5f || height < 0.5f) continue;
                float size = height * 0.85f;
                float textWidth;
                try {
                    textWidth = font.getStringWidth(s) / 1000f * size;
                } catch (IOException | IllegalArgumentException e) {
                    continue;
                }
                if (textWidth <= 0) continue;
                cs.setFont(font, size);
                cs.setHorizontalScaling(Math.max(10f, Math.min(1000f, width / textWidth * 100f)));
                cs.setTextMatrix(new Matrix(ux / width, uy / width, vx / height, vy / height, p0[0], p0[1]));
                try {
                    cs.showText(s);
                } catch (IllegalArgumentException ignored) {
                    // Glyph missing in the font: skip the word.
                }
            }
            cs.endText();
        }
    }
}
