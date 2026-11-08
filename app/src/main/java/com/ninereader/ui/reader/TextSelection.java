package com.ninereader.ui.reader;

import android.graphics.RectF;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

/** Current text selection on a page (LEC-11): character range and its rectangles in page coordinates. */
public final class TextSelection {

    public final int page;
    public final int start;
    public final int end;
    @NonNull
    public final String text;
    @NonNull
    public final List<RectF> rects;

    public TextSelection(int page, int start, int end, @NonNull String text, @NonNull List<RectF> rects) {
        this.page = page;
        this.start = start;
        this.end = end;
        this.text = text;
        this.rects = Collections.unmodifiableList(rects);
    }

    public int length() {
        return end - start;
    }

    /** Expands a character index to the surrounding word in a page text: returns {start, end}. */
    @NonNull
    public static int[] wordAt(@NonNull String text, int index) {
        if (text.isEmpty()) return new int[]{0, 0};
        int i = Math.max(0, Math.min(text.length() - 1, index));
        if (!isWordChar(text.charAt(i))) return new int[]{i, i + 1};
        int s = i;
        while (s > 0 && isWordChar(text.charAt(s - 1))) s--;
        int e = i + 1;
        while (e < text.length() && isWordChar(text.charAt(e))) e++;
        return new int[]{s, e};
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '\'' || c == '’' || c == '-' || c == '_';
    }

    /** Cleans selected text for copy/share: joins hyphenated line breaks and collapses whitespace. */
    @NonNull
    public static String clean(@NonNull String raw) {
        return raw.replaceAll("-\\s*\\r?\\n\\s*", "")
                .replaceAll("\\s*\\r?\\n\\s*", " ")
                .replaceAll("[ \\t]+", " ")
                .trim();
    }
}
