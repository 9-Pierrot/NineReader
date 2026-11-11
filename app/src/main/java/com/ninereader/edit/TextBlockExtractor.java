package com.ninereader.edit;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.ninereader.reader.PageTransform;
import com.ninereader.reader.PdfEngine;
import com.tom_roush.pdfbox.contentstream.PDFGraphicsStreamEngine;
import com.tom_roush.pdfbox.cos.COSName;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.pdmodel.font.PDFont;
import com.tom_roush.pdfbox.pdmodel.font.PDFontDescriptor;
import com.tom_roush.pdfbox.pdmodel.font.PDType3CharProc;
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor;
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject;
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDTransparencyGroup;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImage;
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import com.tom_roush.pdfbox.text.TextPosition;
import com.tom_roush.pdfbox.util.Matrix;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Finds the editable text blocks of a page: from the PDF glyphs (font, size, weight, colour, highlight),
 * or by OCR when the page is a scan without text layer ("recognise the text", MOD-02).
 */
public final class TextBlockExtractor {

    /** A fill above this height (relative to the text size) is a panel behind the text, not a highlight. */
    static final float HIGHLIGHT_MAX_HEIGHT = 2.2f;

    private TextBlockExtractor() {
    }

    /** Collects the glyphs drawn by a page, in user space, with their font, weight and fill colour. */
    static final class GlyphCollector extends PDFTextStripper {
        final List<Glyph> glyphs = new ArrayList<>();
        private final Map<PDFont, boolean[]> styles = new HashMap<>();

        GlyphCollector() throws IOException {
            super();
            // PDFTextStripper ignores the colour operators: without them every text would be read as black.
            addOperator(new com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingColorSpace());
            addOperator(new com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingColor());
            addOperator(new com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingColorN());
            addOperator(new com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingDeviceGrayColor());
            addOperator(new com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingDeviceRGBColor());
            addOperator(new com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingDeviceCMYKColor());
            addOperator(new com.tom_roush.pdfbox.contentstream.operator.color.SetStrokingColorSpace());
            addOperator(new com.tom_roush.pdfbox.contentstream.operator.color.SetStrokingColor());
            addOperator(new com.tom_roush.pdfbox.contentstream.operator.color.SetStrokingColorN());
            addOperator(new com.tom_roush.pdfbox.contentstream.operator.color.SetStrokingDeviceGrayColor());
            addOperator(new com.tom_roush.pdfbox.contentstream.operator.color.SetStrokingDeviceRGBColor());
            addOperator(new com.tom_roush.pdfbox.contentstream.operator.color.SetStrokingDeviceCMYKColor());
        }

        @Override
        protected void processTextPosition(TextPosition t) {
            Matrix m = t.getTextMatrix();
            // Only horizontal text can be edited as blocks.
            if (Math.abs(m.getShearY()) > Math.abs(m.getScaleX()) * 0.2f) return;
            String unicode = t.getUnicode();
            if (unicode == null || unicode.isEmpty()) return;
            unicode = decomposeLigatures(unicode);
            int color = 0xFF000000;
            boolean fakeBold = false;
            try {
                PDColor c = getGraphicsState().getNonStrokingColor();
                color = 0xFF000000 | (c.toRGB() & 0xFFFFFF);
                fakeBold = getGraphicsState().getTextState().getRenderingMode() == RenderingMode.FILL_STROKE;
            } catch (IOException | RuntimeException ignored) {
                // Unknown colour space: keep black.
            }
            PDFont pdFont = t.getFont();
            String font = pdFont == null ? null : pdFont.getName();
            boolean[] style = styleOf(pdFont, font);
            glyphs.add(new Glyph(unicode, m.getTranslateX(), m.getTranslateY(), t.getWidthDirAdj(), sizeOf(t), font, color,
                    style[0] || fakeBold, style[1], 0));
            int[] codes = t.getCharacterCodes();
            if (pdFont != null && unicode.length() == 1 && codes != null && codes.length == 1) {
                Map<Character, Float> widths = charWidths.get(pdFont);
                if (widths == null) {
                    widths = new HashMap<>();
                    charWidths.put(pdFont, widths);
                }
                if (!widths.containsKey(unicode.charAt(0))) {
                    try {
                        widths.put(unicode.charAt(0), pdFont.getWidth(codes[0]) / 1000f);
                    } catch (IOException | RuntimeException ignored) {
                        // No width: this letter cannot help recognise ligatures.
                    }
                }
            }
        }

        // -------------------------------------------------------------- ligatures without Unicode

        /**
         * Ligatures that have no Unicode code point ("ti", "tt" of Calibri and the other ClearType fonts):
         * exporters often leave them out of the ToUnicode map and PDFTextStripper drops them, so that
         * "Entretiens" reads "Entre ens" and would be written back like this. Text, advance relative to
         * the sum of the letters, and likelihood.
         */
        private static final Object[][] LIGATURES = {
                {"ti", 0.988f, 1.0f}, {"tt", 0.946f, 0.9f}, {"ft", 1f, 0.5f}, {"tf", 1f, 0.4f}, {"fj", 1f, 0.3f},
        };

        /** Width (em) of the single characters drawn with each font. */
        private final Map<PDFont, Map<Character, Float>> charWidths = new HashMap<>();
        /** Glyphs of composite fonts without Unicode mapping, recognised once the page is read. */
        private final List<Object[]> unmapped = new ArrayList<>();

        @Override
        protected void showGlyph(Matrix trm, PDFont font, int code, String unicode, com.tom_roush.pdfbox.util.Vector displacement)
                throws IOException {
            super.showGlyph(trm, font, code, unicode, displacement);
            if (font == null || font instanceof com.tom_roush.pdfbox.pdmodel.font.PDSimpleFont || font.isVertical()) return;
            String u = font.toUnicode(code);
            if (u != null && !u.isEmpty()) return;
            if (Math.abs(trm.getShearY()) > Math.abs(trm.getScaleX()) * 0.2f) return;
            PDRectangle crop = getCurrentPage().getCropBox();
            float em = font.getWidth(code) / 1000f;
            float size = (float) Math.hypot(trm.getShearX(), trm.getScaleY());
            if (em <= 0 || size <= 0) return;
            int color = 0xFF000000;
            try {
                color = 0xFF000000 | (getGraphicsState().getNonStrokingColor().toRGB() & 0xFFFFFF);
            } catch (IOException | RuntimeException ignored) {
                // Unknown colour space: keep black.
            }
            unmapped.add(new Object[]{font, em, trm.getTranslateX() - crop.getLowerLeftX(), trm.getTranslateY() - crop.getLowerLeftY(),
                    em * trm.getScalingFactorX(), size, color});
        }

        /** Glyphs of the page, with the ligatures recognised from their width. */
        @NonNull
        List<Glyph> result() {
            for (Object[] p : unmapped) {
                PDFont font = (PDFont) p[0];
                float em = (Float) p[1];
                float x = (Float) p[2];
                float baseline = (Float) p[3];
                float width = (Float) p[4];
                float size = (Float) p[5];
                String text = recognise(charWidths.get(font), em);
                if (text == null || !insideWord(x, baseline, width, size)) continue;
                String name = font.getName();
                boolean[] style = styleOf(font, name);
                glyphs.add(new Glyph(text, x, baseline, width, size, name, (Integer) p[6], style[0], style[1], 0));
            }
            unmapped.clear();
            return glyphs;
        }

        @Nullable
        static String recognise(@Nullable Map<Character, Float> widths, float em) {
            if (widths == null) return null;
            String best = null;
            float bestErr = Float.MAX_VALUE;
            float bestPrior = 0;
            for (Object[] l : LIGATURES) {
                String text = (String) l[0];
                float sum = 0;
                boolean known = true;
                for (int i = 0; i < text.length() && known; i++) {
                    Float w = widths.get(text.charAt(i));
                    if (w == null) known = false;
                    else sum += w;
                }
                if (!known || sum <= 0) continue;
                float err = Math.abs(em - sum * (Float) l[1]) / (sum * (Float) l[1]);
                float prior = (Float) l[2];
                if (err < bestErr - 0.004f || Math.abs(err - bestErr) <= 0.004f && prior > bestPrior) {
                    best = text;
                    bestErr = err;
                    bestPrior = prior;
                }
            }
            return bestErr <= 0.03f ? best : null;
        }

        /** Whether a glyph touches a letter on its left or right (a ligature is part of a word, not a symbol). */
        private boolean insideWord(float x, float baseline, float width, float size) {
            float tol = size * 0.15f;
            for (Glyph g : glyphs) {
                if (Math.abs(g.baseline - baseline) > size * 0.2f) continue;
                if (Math.abs(g.right() - x) < tol || Math.abs(g.x - (x + width)) < tol) return true;
            }
            return false;
        }

        /** {bold, italic} from the font descriptor, then from the font name. */
        private boolean[] styleOf(@Nullable PDFont pdFont, @Nullable String name) {
            boolean[] s = pdFont == null ? null : styles.get(pdFont);
            if (s != null) return s;
            boolean bold = EditFonts.guessBold(name);
            boolean italic = EditFonts.guessItalic(name);
            try {
                PDFontDescriptor fd = pdFont == null ? null : pdFont.getFontDescriptor();
                if (fd != null) {
                    bold |= fd.isForceBold() || fd.getFontWeight() >= 600;
                    italic |= fd.isItalic() || Math.abs(fd.getItalicAngle()) > 1f;
                }
            } catch (RuntimeException ignored) {
                // Broken descriptor: the name is enough.
            }
            s = new boolean[]{bold, italic};
            if (pdFont != null) styles.put(pdFont, s);
            return s;
        }
    }

    /**
     * "ﬀ", "ﬁ", "ﬂ", "ﬃ", "ﬄ", "ﬅ", "ﬆ" as letters: the text is edited and rewritten with another font,
     * which may not have the ligature characters.
     */
    @NonNull
    static String decomposeLigatures(@NonNull String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'ﬀ' && c <= 'ﬆ') return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKC);
        }
        return s;
    }

    /**
     * Font size of a glyph in user space (points). {@link TextPosition#getFontSizeInPt()} is truncated to
     * an integer and ignores the CTM: it is 0 for PDFs that draw with "1 Tf" and scale through "cm" (or
     * use sizes below 1), which hid all their text. The text rendering matrix holds the real size.
     */
    static float sizeOf(@NonNull TextPosition t) {
        Matrix m = t.getTextMatrix();
        float size = (float) Math.hypot(m.getShearX(), m.getScaleY());
        return size > 0.01f && !Float.isInfinite(size) ? size : t.getFontSizeInPt();
    }

    // ------------------------------------------------------------------ vector fills

    /** A filled path of the page (highlight, bullet, panel, table rule...), in the glyphs' coordinates. */
    static final class Fill {
        /** {left, bottom, right, top}. */
        final float[] box;
        final int color;
        /** Index among the fill operators of the page content stream, -1 inside a form or a Type 3 glyph. */
        final int index;
        /** The path is a single axis-aligned rectangle. */
        final boolean rect;

        Fill(float[] box, int color, int index, boolean rect) {
            this.box = box;
            this.color = color;
            this.index = index;
            this.rect = rect;
        }

        float width() {
            return box[2] - box[0];
        }

        float height() {
            return box[3] - box[1];
        }

        boolean contains(float x, float y) {
            return x >= box[0] && x <= box[2] && y >= box[1] && y <= box[3];
        }

        boolean isWhite() {
            return (color & 0xFFFFFF) == 0xFFFFFF;
        }
    }

    /** Records the filled paths of a page (graphics pass, no rendering). */
    static final class FillCollector extends PDFGraphicsStreamEngine {
        final List<Fill> fills = new ArrayList<>();
        /** Horizontal stroked lines {left, bottom, right, top}. */
        final List<float[]> rules = new ArrayList<>();
        /** Number of fill operators met at the top level of the page content. */
        int topLevelFills;
        private final float dx;
        private final float dy;
        private int depth;
        private float l = Float.MAX_VALUE;
        private float b = Float.MAX_VALUE;
        private float r = -Float.MAX_VALUE;
        private float t = -Float.MAX_VALUE;
        private int points;
        private boolean axisAligned = true;
        private int rectangles;
        private final PointF current = new PointF();
        private boolean hasCurrent;

        FillCollector(@NonNull PDPage page) {
            super(page);
            PDRectangle crop = page.getCropBox();
            // Same origin as the text positions (PDFTextStripper translates by the crop box).
            dx = crop.getLowerLeftX();
            dy = crop.getLowerLeftY();
        }

        private void extend(float x, float y) {
            l = Math.min(l, x - dx);
            r = Math.max(r, x - dx);
            b = Math.min(b, y - dy);
            t = Math.max(t, y - dy);
            points++;
        }

        private void reset() {
            l = Float.MAX_VALUE;
            b = Float.MAX_VALUE;
            r = -Float.MAX_VALUE;
            t = -Float.MAX_VALUE;
            points = 0;
            rectangles = 0;
            axisAligned = true;
            hasCurrent = false;
        }

        @Override
        public void appendRectangle(PointF p0, PointF p1, PointF p2, PointF p3) {
            extend(p0.x, p0.y);
            extend(p1.x, p1.y);
            extend(p2.x, p2.y);
            extend(p3.x, p3.y);
            if (Math.abs(p0.y - p1.y) > 0.01f && Math.abs(p0.x - p1.x) > 0.01f) axisAligned = false;
            rectangles++;
            current.set(p0.x, p0.y);
            hasCurrent = true;
        }

        @Override
        public void moveTo(float x, float y) {
            extend(x, y);
            current.set(x, y);
            hasCurrent = true;
        }

        @Override
        public void lineTo(float x, float y) {
            if (hasCurrent && Math.abs(x - current.x) > 0.01f && Math.abs(y - current.y) > 0.01f) axisAligned = false;
            extend(x, y);
            current.set(x, y);
            hasCurrent = true;
        }

        @Override
        public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
            extend(x1, y1);
            extend(x2, y2);
            extend(x3, y3);
            axisAligned = false;
            current.set(x3, y3);
            hasCurrent = true;
        }

        @Override
        public PointF getCurrentPoint() {
            return hasCurrent ? new PointF(current.x, current.y) : null;
        }

        @Override
        public void closePath() {
        }

        @Override
        public void endPath() {
            reset();
        }

        @Override
        public void strokePath() {
            // Thin strokes (table borders, underlines of headings) separate blocks and cells.
            if (points > 0 && (r - l >= 8f && t - b <= 2f || t - b >= 8f && r - l <= 2f)) rules.add(new float[]{l, b, r, t});
            reset();
        }

        @Override
        public void fillPath(Path.FillType rule) {
            recordFill();
        }

        @Override
        public void fillAndStrokePath(Path.FillType rule) {
            recordFill();
        }

        private void recordFill() {
            int index = -1;
            if (depth == 0) index = topLevelFills++;
            if (points > 0 && r > l && t > b) {
                int color = 0xFF000000;
                try {
                    color = 0xFF000000 | (getGraphicsState().getNonStrokingColor().toRGB() & 0xFFFFFF);
                } catch (IOException | RuntimeException ignored) {
                    // Pattern or unknown colour space.
                }
                boolean rect = axisAligned && (rectangles == 1 && points == 4 || rectangles == 0 && points <= 5);
                fills.add(new Fill(new float[]{l, b, r, t}, color, index, rect));
            }
            reset();
        }

        @Override
        public void clip(Path.FillType rule) {
        }

        @Override
        public void drawImage(PDImage image) {
        }

        @Override
        public void shadingFill(COSName name) {
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
        protected void processTransparencyGroup(PDTransparencyGroup group) throws IOException {
            depth++;
            try {
                super.processTransparencyGroup(group);
            } finally {
                depth--;
            }
        }

        @Override
        protected void processType3Stream(PDType3CharProc charProc, Matrix textRenderingMatrix) throws IOException {
            depth++;
            try {
                super.processType3Stream(charProc, textRenderingMatrix);
            } finally {
                depth--;
            }
        }
    }

    /** Filled paths of a page (0-based). */
    @WorkerThread
    @NonNull
    static FillCollector fills(@NonNull PDDocument doc, int page) throws IOException {
        PDPage p = doc.getPage(page);
        FillCollector c = new FillCollector(p);
        c.processPage(p);
        return c;
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
        return collector.result();
    }

    /** Whether a fill is a highlight behind text of the given size: coloured, not taller than a line or two. */
    static boolean isHighlight(@NonNull Fill f, float textSize) {
        return !f.isWhite() && f.height() <= textSize * HIGHLIGHT_MAX_HEIGHT && f.height() >= textSize * 0.5f && f.width() >= textSize * 0.3f;
    }

    /** Glyphs with the colour of the highlight drawn behind them. */
    @NonNull
    static List<Glyph> withHighlights(@NonNull List<Glyph> glyphs, @NonNull List<Fill> fills) {
        if (fills.isEmpty()) return glyphs;
        List<Glyph> out = new ArrayList<>(glyphs.size());
        for (Glyph g : glyphs) {
            float cx = g.x + g.width / 2f;
            float cy = g.baseline + g.size * 0.3f;
            Fill best = null;
            for (Fill f : fills) {
                if (!f.contains(cx, cy) || !isHighlight(f, g.size)) continue;
                if (best == null || f.width() * f.height() < best.width() * best.height()) best = f;
            }
            out.add(best == null ? g : g.withHighlight(best.color));
        }
        return out;
    }

    /** Colour under a block: the smallest panel (fill taller than a highlight) containing it, else white. */
    static int backgroundOf(@NonNull TextBlock block, @NonNull List<Fill> fills) {
        float cx = (block.bounds[0] + block.bounds[2]) / 2f;
        float cy = (block.bounds[1] + block.bounds[3]) / 2f;
        Fill best = null;
        for (Fill f : fills) {
            if (!f.contains(cx, cy) || f.height() <= block.fontSize * HIGHLIGHT_MAX_HEIGHT) continue;
            if (f.box[0] > block.bounds[0] + 1 || f.box[2] < block.bounds[2] - 1) continue;
            if (best == null || f.width() * f.height() < best.width() * best.height()) best = f;
        }
        return best == null ? 0xFFFFFFFF : best.color;
    }

    /** Text blocks of a page from its glyphs and its vector shapes (highlights, bullets, panels). */
    @WorkerThread
    @NonNull
    public static List<TextBlock> fromPdf(@NonNull PDDocument doc, int page) throws IOException {
        List<Glyph> glyphs = glyphs(doc, page);
        List<Fill> fills;
        List<float[]> rules = new ArrayList<>();
        try {
            FillCollector c = fills(doc, page);
            fills = c.fills;
            rules.addAll(c.rules);
        } catch (IOException | RuntimeException e) {
            fills = Collections.emptyList(); // the text alone is still editable
        }
        for (Fill f : fills) {
            if (!f.isWhite() && (f.height() <= 1.6f && f.width() >= 8f || f.width() <= 1.6f && f.height() >= 8f)) rules.add(f.box);
        }
        List<float[]> marks = new ArrayList<>();
        for (Fill f : fills) {
            if (!f.isWhite() && f.width() < 14f && f.height() < 14f && f.width() > 0.8f && f.height() > 0.8f) marks.add(f.box);
        }
        List<TextBlock> blocks = TextLayoutAnalyzer.analyze(page, withHighlights(glyphs, fills), marks, rules);
        for (TextBlock b : blocks) b.background = backgroundOf(b, fills);
        return blocks;
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
                TextBlock tb = new TextBlock(page,
                        new float[]{Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[0], b[0]), Math.max(a[1], b[1])},
                        sb.toString(), fontSize, pitch > fontSize ? pitch : 0, EditFonts.Family.SANS, false, false, 0xFF000000, true);
                tb.lineCount = Math.max(1, block.getLines().size());
                out.add(tb);
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
