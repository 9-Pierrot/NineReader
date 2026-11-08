package com.ninereader.annotations;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;

import com.ninereader.pdfops.PdfBoxHelper;
import com.tom_roush.pdfbox.cos.COSName;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.pdmodel.font.PDFont;
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor;
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory;
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLine;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquareCircle;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary;
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm;
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox;
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice;
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField;
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

/**
 * Writes pending annotations and form values into a PDF with PdfBox (ANN-07, SIG-01..04, MOD-01/02).
 * Markup annotations are written as standard PDF annotations with appearance streams so that other
 * readers display them; free text, images and white-out are drawn into the page content.
 */
public final class PdfAnnotationWriter {

    public static final String AUTHOR = "NineReader";

    private PdfAnnotationWriter() {
    }

    /** Result of a write: number of items that could not be written. */
    public static final class Result {
        public int written;
        public final List<String> failures = new ArrayList<>();
    }

    @NonNull
    public static Result apply(@NonNull PDDocument doc, @NonNull List<Annotation> annotations,
                               @NonNull Map<String, String> fields) {
        Result result = new Result();
        PDFont[] font = new PDFont[1];
        for (Annotation a : annotations) {
            if (a.page < 0 || a.page >= doc.getNumberOfPages()) {
                result.failures.add(a.type.name());
                continue;
            }
            PDPage page = doc.getPage(a.page);
            try {
                switch (a.type) {
                    case HIGHLIGHT:
                    case UNDERLINE:
                    case STRIKEOUT:
                        addTextMarkup(doc, page, a);
                        break;
                    case NOTE:
                        addNote(doc, page, a);
                        break;
                    case INK:
                        addInk(doc, page, a);
                        break;
                    case RECT:
                    case CIRCLE:
                        addSquareCircle(doc, page, a);
                        break;
                    case LINE:
                    case ARROW:
                        addLine(doc, page, a);
                        break;
                    case FREETEXT:
                        if (font[0] == null) font[0] = PdfBoxHelper.unicodeFont(doc);
                        drawFreeText(doc, page, a, font[0]);
                        break;
                    case IMAGE:
                        drawImage(doc, page, a);
                        break;
                    case WHITEOUT:
                        drawWhiteout(doc, page, a);
                        break;
                    default:
                        break;
                }
                result.written++;
            } catch (IOException | RuntimeException e) {
                result.failures.add(a.type.name() + ": " + e.getMessage());
            }
        }
        applyFields(doc, fields, result);
        return result;
    }

    // ------------------------------------------------------------------ annotations

    static PDColor color(int argb) {
        return new PDColor(new float[]{((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f},
                PDDeviceRGB.INSTANCE);
    }

    private static void common(PDAnnotationMarkup m, Annotation a, PDPage page) {
        Calendar now = Calendar.getInstance();
        m.setColor(color(a.color));
        m.setTitlePopup(AUTHOR);
        m.setCreationDate(now);
        m.setModifiedDate(now);
        m.setAnnotationName(a.id);
        m.setPrinted(true);
        m.setPage(page);
        if (a.opacity < 1f) m.setConstantOpacity(a.opacity);
    }

    private static PDBorderStyleDictionary border(float width) {
        PDBorderStyleDictionary bs = new PDBorderStyleDictionary();
        bs.setWidth(width);
        return bs;
    }

    private static PDRectangle rect(float l, float b, float r, float t) {
        PDRectangle rect = new PDRectangle();
        rect.setLowerLeftX(Math.min(l, r));
        rect.setLowerLeftY(Math.min(b, t));
        rect.setUpperRightX(Math.max(l, r));
        rect.setUpperRightY(Math.max(b, t));
        return rect;
    }

    private static void addTextMarkup(PDDocument doc, PDPage page, Annotation a) throws IOException {
        if (a.rects.isEmpty()) return;
        String subtype;
        switch (a.type) {
            case UNDERLINE:
                subtype = PDAnnotationTextMarkup.SUB_TYPE_UNDERLINE;
                break;
            case STRIKEOUT:
                subtype = PDAnnotationTextMarkup.SUB_TYPE_STRIKEOUT;
                break;
            default:
                subtype = PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT;
                break;
        }
        PDAnnotationTextMarkup m = new PDAnnotationTextMarkup(subtype);
        float[] quads = new float[a.rects.size() * 8];
        float[] bb = a.boundingBox();
        int i = 0;
        for (float[] q : a.rects) {
            float l = Math.min(q[0], q[2]);
            float r = Math.max(q[0], q[2]);
            float b = Math.min(q[1], q[3]);
            float t = Math.max(q[1], q[3]);
            quads[i++] = l;
            quads[i++] = t;
            quads[i++] = r;
            quads[i++] = t;
            quads[i++] = l;
            quads[i++] = b;
            quads[i++] = r;
            quads[i++] = b;
        }
        m.setQuadPoints(quads);
        m.setRectangle(rect(bb[0] - 1, bb[1] - 1, bb[2] + 1, bb[3] + 1));
        if (a.text != null) m.setContents(a.text);
        common(m, a, page);
        m.constructAppearances(doc);
        page.getAnnotations().add(m);
    }

    private static void addNote(PDDocument doc, PDPage page, Annotation a) throws IOException {
        if (a.bounds == null) return;
        PDAnnotationText t = new PDAnnotationText();
        t.setRectangle(rect(a.bounds[0], a.bounds[1], a.bounds[2], a.bounds[3]));
        t.setContents(a.text == null ? "" : a.text);
        t.setName(PDAnnotationText.NAME_COMMENT);
        common(t, a, page);
        t.constructAppearances(doc);
        page.getAnnotations().add(t);
    }

    private static void addInk(PDDocument doc, PDPage page, Annotation a) throws IOException {
        if (a.strokes.isEmpty()) return;
        PDAnnotationMarkup ink = new PDAnnotationMarkup();
        ink.getCOSObject().setName(COSName.SUBTYPE, PDAnnotationMarkup.SUB_TYPE_INK);
        float[][] list = new float[a.strokes.size()][];
        for (int i = 0; i < a.strokes.size(); i++) list[i] = a.strokes.get(i);
        ink.setInkList(list);
        float[] bb = a.boundingBox();
        ink.setRectangle(rect(bb[0], bb[1], bb[2], bb[3]));
        ink.setBorderStyle(border(a.width));
        common(ink, a, page);
        ink.constructAppearances(doc);
        page.getAnnotations().add(ink);
    }

    private static void addSquareCircle(PDDocument doc, PDPage page, Annotation a) throws IOException {
        if (a.bounds == null) return;
        PDAnnotationSquareCircle sc = new PDAnnotationSquareCircle(a.type == Annotation.Type.RECT
                ? PDAnnotationSquareCircle.SUB_TYPE_SQUARE : PDAnnotationSquareCircle.SUB_TYPE_CIRCLE);
        float half = a.width / 2f;
        sc.setRectangle(rect(a.bounds[0] - half, a.bounds[1] - half, a.bounds[2] + half, a.bounds[3] + half));
        sc.setBorderStyle(border(a.width));
        common(sc, a, page);
        sc.constructAppearances(doc);
        page.getAnnotations().add(sc);
    }

    private static void addLine(PDDocument doc, PDPage page, Annotation a) throws IOException {
        if (a.bounds == null) return;
        PDAnnotationLine line = new PDAnnotationLine();
        line.setLine(new float[]{a.bounds[0], a.bounds[1], a.bounds[2], a.bounds[3]});
        float pad = a.width * 6 + 4;
        line.setRectangle(rect(Math.min(a.bounds[0], a.bounds[2]) - pad, Math.min(a.bounds[1], a.bounds[3]) - pad,
                Math.max(a.bounds[0], a.bounds[2]) + pad, Math.max(a.bounds[1], a.bounds[3]) + pad));
        if (a.type == Annotation.Type.ARROW) line.setEndPointEndingStyle(PDAnnotationLine.LE_OPEN_ARROW);
        line.setBorderStyle(border(a.width));
        common(line, a, page);
        line.constructAppearances(doc);
        page.getAnnotations().add(line);
    }

    // ------------------------------------------------------------------ content drawing

    private static PDPageContentStream append(PDDocument doc, PDPage page) throws IOException {
        return new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true);
    }

    private static void drawFreeText(PDDocument doc, PDPage page, Annotation a, PDFont font) throws IOException {
        if (a.bounds == null || a.text == null || a.text.isEmpty()) return;
        float size = Math.max(4f, a.fontSize);
        float left = Math.min(a.bounds[0], a.bounds[2]);
        float top = Math.max(a.bounds[1], a.bounds[3]);
        float width = Math.abs(a.bounds[2] - a.bounds[0]);
        List<String> lines = wrap(a.text, font, size, width);
        try (PDPageContentStream cs = append(doc, page)) {
            cs.setNonStrokingColor(((a.color >> 16) & 0xFF) / 255f, ((a.color >> 8) & 0xFF) / 255f, (a.color & 0xFF) / 255f);
            cs.beginText();
            cs.setFont(font, size);
            cs.setLeading(size * 1.2f);
            cs.newLineAtOffset(left, top - size);
            boolean first = true;
            for (String line : lines) {
                if (!first) cs.newLine();
                first = false;
                cs.showText(encodable(font, line));
            }
            cs.endText();
        }
    }

    /** Wraps text to a width (in points) using the font metrics. */
    @NonNull
    static List<String> wrap(@NonNull String text, @NonNull PDFont font, float size, float width) {
        List<String> out = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            String[] words = paragraph.split(" ");
            StringBuilder line = new StringBuilder();
            for (String w : words) {
                String candidate = line.length() == 0 ? w : line + " " + w;
                if (line.length() > 0 && textWidth(font, candidate, size) > width) {
                    out.add(line.toString());
                    line = new StringBuilder(w);
                } else {
                    line = new StringBuilder(candidate);
                }
            }
            out.add(line.toString());
        }
        return out;
    }

    private static float textWidth(PDFont font, String s, float size) {
        try {
            return font.getStringWidth(encodable(font, s)) / 1000f * size;
        } catch (IOException | RuntimeException e) {
            return s.length() * size * 0.5f;
        }
    }

    /** Replaces characters the font cannot encode. */
    @NonNull
    public static String encodable(@NonNull PDFont font, @NonNull String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            try {
                font.encode(ch);
                sb.append(ch);
            } catch (IOException | IllegalArgumentException e) {
                sb.append('?');
            }
            i += Character.charCount(cp);
        }
        return sb.toString();
    }

    private static void drawImage(PDDocument doc, PDPage page, Annotation a) throws IOException {
        if (a.bounds == null || a.imagePath == null) return;
        PDImageXObject img;
        String lower = a.imagePath.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            try (InputStream in = new FileInputStream(a.imagePath)) {
                img = JPEGFactory.createFromStream(doc, in);
            }
        } else {
            Bitmap bmp = BitmapFactory.decodeFile(a.imagePath);
            if (bmp == null) throw new IOException("Cannot decode " + a.imagePath);
            img = LosslessFactory.createFromImage(doc, bmp);
            bmp.recycle();
        }
        float l = Math.min(a.bounds[0], a.bounds[2]);
        float b = Math.min(a.bounds[1], a.bounds[3]);
        float w = Math.abs(a.bounds[2] - a.bounds[0]);
        float h = Math.abs(a.bounds[3] - a.bounds[1]);
        try (PDPageContentStream cs = append(doc, page)) {
            cs.drawImage(img, l, b, w, h);
        }
    }

    private static void drawWhiteout(PDDocument doc, PDPage page, Annotation a) throws IOException {
        if (a.bounds == null) return;
        try (PDPageContentStream cs = append(doc, page)) {
            cs.setNonStrokingColor(1f, 1f, 1f);
            float l = Math.min(a.bounds[0], a.bounds[2]);
            float b = Math.min(a.bounds[1], a.bounds[3]);
            cs.addRect(l, b, Math.abs(a.bounds[2] - a.bounds[0]), Math.abs(a.bounds[3] - a.bounds[1]));
            cs.fill();
        }
    }

    // ------------------------------------------------------------------ forms

    private static void applyFields(PDDocument doc, Map<String, String> fields, Result result) {
        if (fields.isEmpty()) return;
        PDAcroForm form = doc.getDocumentCatalog().getAcroForm();
        if (form == null) {
            result.failures.add("AcroForm missing");
            return;
        }
        for (Map.Entry<String, String> e : fields.entrySet()) {
            PDField f = form.getField(e.getKey());
            if (f == null) {
                result.failures.add(e.getKey());
                continue;
            }
            try {
                String v = e.getValue();
                if (f instanceof PDCheckBox) {
                    if ("true".equals(v)) {
                        ((PDCheckBox) f).check();
                    } else {
                        ((PDCheckBox) f).unCheck();
                    }
                } else if (f instanceof PDRadioButton) {
                    ((PDRadioButton) f).setValue(v);
                } else if (f instanceof PDChoice) {
                    ((PDChoice) f).setValue(v);
                } else {
                    f.setValue(v);
                }
                result.written++;
            } catch (IOException | RuntimeException ex) {
                result.failures.add(e.getKey() + ": " + ex.getMessage());
            }
        }
    }

    /** Lists annotations already stored in the PDF (for the annotation panel, ANN-06). */
    @NonNull
    public static List<AnnotationExporter.Entry> existing(@NonNull PDDocument doc) throws IOException {
        List<AnnotationExporter.Entry> out = new ArrayList<>();
        int index = 0;
        for (PDPage page : doc.getPages()) {
            for (PDAnnotation an : page.getAnnotations()) {
                String sub = an.getSubtype();
                if (sub == null || "Link".equals(sub) || "Widget".equals(sub) || "Popup".equals(sub)) continue;
                String contents = an.getContents();
                out.add(new AnnotationExporter.Entry(index, sub, contents == null ? "" : contents.trim()));
            }
            index++;
        }
        return out;
    }
}
