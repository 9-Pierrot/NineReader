package com.ninereader.edit;

import android.content.Context;
import android.graphics.Typeface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.font.PDFont;
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Fonts used to edit text (MOD-02): the Liberation family (SIL OFL), metric-compatible with Arial,
 * Times New Roman and Courier New, so edited text keeps the layout of most documents.
 */
public final class EditFonts {

    /** Font families. */
    public enum Family { SANS, SERIF, MONO }

    private static final Map<String, Typeface> TYPEFACES = new HashMap<>();

    private EditFonts() {
    }

    @NonNull
    public static String assetPath(@NonNull Family family, boolean bold, boolean italic) {
        String name;
        switch (family) {
            case SERIF:
                name = "LiberationSerif";
                break;
            case MONO:
                name = "LiberationMono";
                break;
            default:
                name = "LiberationSans";
                break;
        }
        String style = bold && italic ? "BoldItalic" : bold ? "Bold" : italic ? "Italic" : "Regular";
        return "fonts/" + name + "-" + style + ".ttf";
    }

    /** Typeface for on-screen preview and inline editing. */
    @NonNull
    public static Typeface typeface(@NonNull Context context, @NonNull Family family, boolean bold, boolean italic) {
        String path = assetPath(family, bold, italic);
        synchronized (TYPEFACES) {
            Typeface t = TYPEFACES.get(path);
            if (t == null) {
                try {
                    t = Typeface.createFromAsset(context.getApplicationContext().getAssets(), path);
                } catch (RuntimeException e) {
                    t = Typeface.create(family == Family.SERIF ? Typeface.SERIF : family == Family.MONO ? Typeface.MONOSPACE
                            : Typeface.SANS_SERIF, bold && italic ? Typeface.BOLD_ITALIC : bold ? Typeface.BOLD : italic ? Typeface.ITALIC : Typeface.NORMAL);
                }
                TYPEFACES.put(path, t);
            }
            return t;
        }
    }

    /** Measures styled text on screen with the substitute fonts, in points (text size = font size). */
    @NonNull
    public static RichTextLayout.Measurer screenMeasurer(@NonNull Context context, @NonNull Family family, float size, float scaleX) {
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(size);
        paint.setTextScaleX(scaleX);
        return (s, style) -> {
            paint.setTypeface(typeface(context, family, style.bold, style.italic));
            return paint.measureText(s);
        };
    }

    /**
     * Horizontal scale giving the substitute font the width of the original one (e.g. Calibri is about 10%
     * narrower than Liberation Sans), so that unchanged text breaks lines at the same places. 1 when the
     * original width is unknown.
     */
    public static float fitScaleX(@NonNull Context context, @NonNull TextBlock b) {
        if (b.fromOcr || b.originalWidth <= 0 || b.text.isEmpty()) return 1f;
        RichTextLayout.Measurer m = screenMeasurer(context, b.family, b.fontSize, 1f);
        int breaks = 0;
        for (int i = 0; i < b.text.length(); i++) if (b.text.charAt(i) == '\n') breaks++;
        float natural = 0;
        for (StyleRun r : b.runs) {
            String piece = b.text.substring(r.start, r.end).replace("\n", "");
            if (!piece.isEmpty()) natural += m.width(piece, r);
        }
        // The text was joined with spaces where the original wrapped: they were not drawn.
        int joins = Math.max(0, b.lineCount - 1 - breaks);
        if (!b.runs.isEmpty()) natural -= joins * m.width(" ", b.runs.get(0));
        if (natural <= 0) return 1f;
        // 1% margin: rounding in the measures must not push the last word of a full line to the next one.
        float scale = b.originalWidth / natural * 0.99f;
        if (Math.abs(scale - 1f) < 0.02f) return 1f;
        return Math.max(0.7f, Math.min(1.3f, scale));
    }

    /** Loads (and embeds as a subset) the PDF font matching a style, cached per document. */
    @NonNull
    public static PDFont pdFont(@NonNull Context context, @NonNull PDDocument doc, @NonNull Map<String, PDFont> cache,
                                @NonNull Family family, boolean bold, boolean italic) throws IOException {
        String path = assetPath(family, bold, italic);
        PDFont f = cache.get(path);
        if (f != null) return f;
        try (InputStream in = context.getApplicationContext().getAssets().open(path)) {
            f = PDType0Font.load(doc, in, true);
        }
        cache.put(path, f);
        return f;
    }

    /** Guesses the family of a PDF font from its name (e.g. "ABCDEF+TimesNewRomanPS-BoldMT"). */
    @NonNull
    public static Family guessFamily(@Nullable String fontName) {
        if (fontName == null) return Family.SANS;
        String n = fontName.toLowerCase(Locale.ROOT);
        if (n.contains("courier") || n.contains("mono") || n.contains("consol") || n.contains("cousine")) return Family.MONO;
        if (n.contains("times") || n.contains("serif") && !n.contains("sans") || n.contains("roman") || n.contains("georgia")
                || n.contains("garamond") || n.contains("cambria") || n.contains("tinos") || n.contains("book")
                || n.contains("palatino") || n.contains("minion")) {
            return Family.SERIF;
        }
        return Family.SANS;
    }

    /** Qt / Kanwrite style names: "Calibri-1-700" = italic flag, then CSS weight. */
    private static final java.util.regex.Pattern QT_STYLE = java.util.regex.Pattern.compile("-([01])-(\\d{3})$");

    public static boolean guessBold(@Nullable String fontName) {
        if (fontName == null) return false;
        java.util.regex.Matcher m = QT_STYLE.matcher(fontName);
        if (m.find()) return Integer.parseInt(m.group(2)) >= 600;
        String n = fontName.toLowerCase(Locale.ROOT);
        return n.contains("bold") || n.contains("black") || n.contains("heavy") || n.contains("semibold") || n.contains("demi");
    }

    public static boolean guessItalic(@Nullable String fontName) {
        if (fontName == null) return false;
        java.util.regex.Matcher m = QT_STYLE.matcher(fontName);
        if (m.find()) return "1".equals(m.group(1));
        String n = fontName.toLowerCase(Locale.ROOT);
        return n.contains("italic") || n.contains("oblique");
    }
}
