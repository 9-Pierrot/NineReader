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

    public static boolean guessBold(@Nullable String fontName) {
        if (fontName == null) return false;
        String n = fontName.toLowerCase(Locale.ROOT);
        return n.contains("bold") || n.contains("black") || n.contains("heavy") || n.contains("semibold") || n.contains("demi");
    }

    public static boolean guessItalic(@Nullable String fontName) {
        if (fontName == null) return false;
        String n = fontName.toLowerCase(Locale.ROOT);
        return n.contains("italic") || n.contains("oblique");
    }
}
