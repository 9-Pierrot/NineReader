package com.ninereader.edit;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.ninereader.annotations.Annotation;
import com.ninereader.annotations.PdfAnnotationWriter;
import com.tom_roush.pdfbox.contentstream.operator.Operator;
import com.tom_roush.pdfbox.cos.COSArray;
import com.tom_roush.pdfbox.cos.COSBase;
import com.tom_roush.pdfbox.cos.COSFloat;
import com.tom_roush.pdfbox.cos.COSName;
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser;
import com.tom_roush.pdfbox.pdfwriter.ContentStreamWriter;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.common.PDStream;
import com.tom_roush.pdfbox.pdmodel.font.PDFont;
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import com.tom_roush.pdfbox.text.TextPosition;
import com.tom_roush.pdfbox.util.Matrix;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Applies text edits to a PDF (MOD-02): the original glyphs of each edited block are really removed from
 * the page content (each text-showing operator is replaced by an invisible move of the same width, so
 * the rest of the line does not shift), then the new text is written with an embedded font.
 * On scanned pages (OCR blocks) the original pixels are covered instead.
 */
public final class PdfTextEditor {

    private PdfTextEditor() {
    }

    /** Result of an edit pass. */
    public static final class Result {
        public int edits;
        public int removedOperators;
    }

    @WorkerThread
    @NonNull
    public static Result apply(@NonNull Context context, @NonNull PDDocument doc, @NonNull List<Annotation> annotations,
                               @NonNull Set<Integer> flattenedPages) throws IOException {
        Result result = new Result();
        Map<Integer, List<Annotation>> byPage = new TreeMap<>();
        for (Annotation a : annotations) {
            if (a.type != Annotation.Type.TEXT_EDIT || a.page < 0 || a.page >= doc.getNumberOfPages()) continue;
            List<Annotation> list = byPage.get(a.page);
            if (list == null) {
                list = new ArrayList<>();
                byPage.put(a.page, list);
            }
            list.add(a);
        }
        Map<String, PDFont> fonts = new HashMap<>();
        for (Map.Entry<Integer, List<Annotation>> e : byPage.entrySet()) {
            PDPage page = doc.getPage(e.getKey());
            List<float[]> remove = new ArrayList<>();
            List<float[]> cover = new ArrayList<>();
            for (Annotation a : e.getValue()) {
                if (a.originalBounds == null) continue;
                if (a.fromOcr || flattenedPages.contains(e.getKey())) {
                    cover.add(a.originalBounds);
                } else {
                    remove.add(a.originalBounds);
                }
            }
            if (!remove.isEmpty()) {
                result.removedOperators += removeText(doc, page, remove);
                // Glyphs that could not be removed (text inside form XObjects, Type 3 fonts...) are covered instead.
                cover.addAll(regionsStillWithText(doc, e.getKey(), remove));
            }
            try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                for (float[] r : cover) {
                    cs.setNonStrokingColor(1f, 1f, 1f);
                    cs.addRect(r[0] - 1, r[1] - 1, r[2] - r[0] + 2, r[3] - r[1] + 2);
                    cs.fill();
                }
                for (Annotation a : e.getValue()) {
                    if (a.text == null || a.text.trim().isEmpty() || a.bounds == null) continue;
                    PDFont font = EditFonts.pdFont(context, doc, fonts, familyOf(a), a.bold, a.italic);
                    drawText(cs, font, a);
                    result.edits++;
                }
            }
        }
        return result;
    }

    @NonNull
    public static EditFonts.Family familyOf(@NonNull Annotation a) {
        try {
            return a.fontFamily == null ? EditFonts.Family.SANS : EditFonts.Family.valueOf(a.fontFamily);
        } catch (IllegalArgumentException e) {
            return EditFonts.Family.SANS;
        }
    }

    /** Regions in which the page still draws glyphs (centre inside the region). */
    @NonNull
    static List<float[]> regionsStillWithText(@NonNull PDDocument doc, int page, @NonNull List<float[]> regions) throws IOException {
        List<Glyph> glyphs = TextBlockExtractor.glyphs(doc, page);
        List<float[]> left = new ArrayList<>();
        for (float[] r : regions) {
            for (Glyph g : glyphs) {
                if (g.text.trim().isEmpty()) continue;
                float cx = g.x + g.width / 2f;
                float cy = g.baseline + g.size * 0.3f;
                if (cx >= r[0] && cx <= r[2] && cy >= r[1] && cy <= r[3]) {
                    left.add(r);
                    break;
                }
            }
        }
        return left;
    }

    /** Distance between baselines of an edited text, in points. */
    public static float lineHeightOf(@NonNull Annotation a) {
        return a.lineHeight > 0 ? a.lineHeight : Math.max(2f, a.fontSize) * TextBlock.DEFAULT_LEADING;
    }

    /** Height between the top of a text box and its first baseline (same convention as the detected blocks). */
    public static float firstBaselineOffset(float fontSize) {
        return fontSize * 0.8f;
    }

    private static void drawText(PDPageContentStream cs, PDFont font, Annotation a) throws IOException {
        float size = Math.max(2f, a.fontSize);
        float left = Math.min(a.bounds[0], a.bounds[2]);
        float top = Math.max(a.bounds[1], a.bounds[3]);
        float width = Math.max(size * 2, Math.abs(a.bounds[2] - a.bounds[0]));
        float ascent = firstBaselineOffset(size);
        List<String> lines = wrap(a.text, font, size, width);
        cs.setNonStrokingColor(((a.color >> 16) & 0xFF) / 255f, ((a.color >> 8) & 0xFF) / 255f, (a.color & 0xFF) / 255f);
        cs.beginText();
        cs.setFont(font, size);
        cs.setLeading(lineHeightOf(a));
        cs.newLineAtOffset(left, top - ascent);
        boolean first = true;
        for (String line : lines) {
            if (!first) cs.newLine();
            first = false;
            cs.showText(PdfAnnotationWriter.encodable(font, line));
        }
        cs.endText();
    }

    /** Wraps text to a width, honouring explicit line breaks. */
    @NonNull
    static List<String> wrap(@NonNull String text, @NonNull PDFont font, float size, float width) {
        List<String> out = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = line.length() == 0 ? word : line + " " + word;
                if (line.length() > 0 && width(font, candidate, size) > width) {
                    out.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(candidate);
                }
            }
            out.add(line.toString());
        }
        return out;
    }

    private static float width(PDFont font, String s, float size) {
        try {
            return font.getStringWidth(PdfAnnotationWriter.encodable(font, s)) / 1000f * size;
        } catch (IOException | RuntimeException e) {
            return s.length() * size * 0.5f;
        }
    }

    // ------------------------------------------------------------------ glyph removal

    /** Records, for each top-level text-showing operator, how many glyphs fall in the regions and its advance. */
    static final class OperatorTracker extends PDFTextStripper {
        private final List<float[]> regions;
        private int depth;
        private int index = -1;
        private int current = -1;
        final Map<Integer, int[]> counts = new HashMap<>();
        final Map<Integer, Float> advance = new HashMap<>();

        OperatorTracker(List<float[]> regions) throws IOException {
            this.regions = regions;
        }

        static boolean isShow(String name) {
            return "Tj".equals(name) || "TJ".equals(name) || "'".equals(name) || "\"".equals(name);
        }

        @Override
        protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
            String name = operator.getName();
            boolean show = depth == 0 && isShow(name);
            Matrix before = null;
            if (show) {
                index++;
                current = index;
                before = getTextMatrix() == null ? null : getTextMatrix().clone();
            }
            try {
                super.processOperator(operator, operands);
            } finally {
                if (show) {
                    Matrix after = getTextMatrix();
                    Matrix start = "Tj".equals(name) || "TJ".equals(name) ? before : getTextLineMatrix();
                    if (after != null && start != null) {
                        float tx;
                        if (Math.abs(start.getScaleX()) > 1e-4f) {
                            tx = (after.getTranslateX() - start.getTranslateX()) / start.getScaleX();
                        } else if (Math.abs(start.getShearY()) > 1e-4f) {
                            tx = (after.getTranslateY() - start.getTranslateY()) / start.getShearY();
                        } else {
                            tx = 0;
                        }
                        float fs = getGraphicsState().getTextState().getFontSize();
                        float th = getGraphicsState().getTextState().getHorizontalScaling() / 100f;
                        advance.put(current, fs * th == 0 ? 0f : -tx * 1000f / (fs * th));
                    }
                    current = -1;
                }
            }
        }

        @Override
        public void showForm(PDFormXObject form) throws IOException {
            depth++;
            try {
                super.showForm(form);
            } finally {
                depth--;
            }
        }

        @Override
        protected void processTextPosition(TextPosition t) {
            if (current < 0 || depth > 0) return;
            Matrix m = t.getTextMatrix();
            float cx = m.getTranslateX() + t.getWidthDirAdj() / 2f;
            float cy = m.getTranslateY() + t.getFontSizeInPt() * 0.3f;
            int[] c = counts.get(current);
            if (c == null) {
                c = new int[2];
                counts.put(current, c);
            }
            c[1]++;
            for (float[] r : regions) {
                if (cx >= r[0] && cx <= r[2] && cy >= r[1] && cy <= r[3]) {
                    c[0]++;
                    break;
                }
            }
        }

        /** Operators whose glyphs are mostly inside the regions. */
        @NonNull
        Set<Integer> removable() {
            Set<Integer> out = new HashSet<>();
            for (Map.Entry<Integer, int[]> e : counts.entrySet()) {
                int[] c = e.getValue();
                if (c[1] > 0 && c[0] * 2 > c[1]) out.add(e.getKey());
            }
            return out;
        }
    }

    /**
     * Removes the glyphs drawn inside regions of a page by rewriting its content stream.
     *
     * @return number of text operators removed
     */
    static int removeText(@NonNull PDDocument doc, @NonNull PDPage page, @NonNull List<float[]> regions) throws IOException {
        OperatorTracker tracker = new OperatorTracker(regions);
        int pageIndex = doc.getPages().indexOf(page);
        tracker.setStartPage(pageIndex + 1);
        tracker.setEndPage(pageIndex + 1);
        tracker.writeText(doc, new StringWriter());
        Set<Integer> remove = tracker.removable();
        if (remove.isEmpty()) return 0;

        PDFStreamParser parser = new PDFStreamParser(page);
        parser.parse();
        List<Object> tokens = parser.getTokens();
        List<Object> out = new ArrayList<>(tokens.size());
        List<Object> operands = new ArrayList<>();
        int showIndex = -1;
        int removed = 0;
        for (Object token : tokens) {
            if (!(token instanceof Operator)) {
                operands.add(token);
                continue;
            }
            Operator op = (Operator) token;
            String name = op.getName();
            if (OperatorTracker.isShow(name)) {
                showIndex++;
                if (remove.contains(showIndex)) {
                    out.addAll(replacement(name, operands, tracker.advance.get(showIndex)));
                    operands.clear();
                    removed++;
                    continue;
                }
            }
            out.addAll(operands);
            operands.clear();
            out.add(op);
        }
        out.addAll(operands);
        PDStream stream = new PDStream(doc);
        try (OutputStream os = stream.createOutputStream(COSName.FLATE_DECODE)) {
            new ContentStreamWriter(os).writeTokens(out);
        }
        page.setContents(stream);
        return removed;
    }

    /** Tokens moving the text position like the removed operator, without drawing anything. */
    @NonNull
    static List<Object> replacement(@NonNull String name, @NonNull List<Object> operands, @Nullable Float advance) {
        List<Object> out = new ArrayList<>();
        if ("\"".equals(name) && operands.size() >= 3) {
            out.add(operands.get(0));
            out.add(Operator.getOperator("Tw"));
            out.add(operands.get(1));
            out.add(Operator.getOperator("Tc"));
        }
        if ("'".equals(name) || "\"".equals(name)) out.add(Operator.getOperator("T*"));
        if (advance != null && Math.abs(advance) > 1e-3f) {
            COSArray array = new COSArray();
            array.add(new COSFloat(advance));
            out.add(array);
            out.add(Operator.getOperator("TJ"));
        }
        return out;
    }
}
