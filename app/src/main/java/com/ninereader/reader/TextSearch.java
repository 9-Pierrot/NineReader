package com.ninereader.reader;

import androidx.annotation.NonNull;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Full-text search helpers (LEC-07): case and accent insensitive matching on page text, returning
 * character ranges that map 1:1 to Pdfium character indexes.
 */
public final class TextSearch {

    private TextSearch() {
    }

    /** A match on a page. */
    public static final class Match {
        public final int page;
        public final int start;
        public final int length;
        @NonNull
        public final String excerpt;

        public Match(int page, int start, int length, @NonNull String excerpt) {
            this.page = page;
            this.start = start;
            this.length = length;
            this.excerpt = excerpt;
        }
    }

    /**
     * Folds a string for comparison while keeping the same length as the input, so indexes stay
     * aligned: lower case, accents removed, line breaks and tabs turned into spaces.
     */
    @NonNull
    public static String fold(@NonNull String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\r' || c == '\n' || c == '\t' || c == ' ') {
                sb.append(' ');
                continue;
            }
            char lower = Character.toLowerCase(c);
            if (lower < 128) {
                sb.append(lower);
                continue;
            }
            String decomposed = Normalizer.normalize(String.valueOf(lower), Normalizer.Form.NFD);
            char base = decomposed.isEmpty() ? lower : decomposed.charAt(0);
            switch (lower) {
                case 'œ': // oe ligature, keep one char
                    base = 'o';
                    break;
                case '’':
                case '‘':
                    base = '\'';
                    break;
                default:
                    break;
            }
            sb.append(base);
        }
        return sb.toString();
    }

    /** Normalises a query (folded, whitespace collapsed). */
    @NonNull
    public static String normalizeQuery(@NonNull String query) {
        return fold(query.trim()).replaceAll("\\s+", " ");
    }

    /**
     * Finds every occurrence of a normalised query in a page text. Runs of whitespace in the page text
     * match a single space of the query (lines are separated by CR/LF in extracted text).
     */
    @NonNull
    public static List<Match> findInPage(int page, @NonNull String pageText, @NonNull String normalizedQuery, int maxResults) {
        List<Match> out = new ArrayList<>();
        if (normalizedQuery.isEmpty() || pageText.isEmpty()) return out;
        String folded = fold(pageText);
        int i = 0;
        while (i < folded.length() && out.size() < maxResults) {
            int end = matchAt(folded, i, normalizedQuery);
            if (end > 0) {
                out.add(new Match(page, i, end - i, excerpt(pageText, i, end)));
                i = end;
            } else {
                i++;
            }
        }
        return out;
    }

    /** Returns the end index (exclusive) of a match starting at {@code start}, or -1. */
    static int matchAt(@NonNull String text, int start, @NonNull String query) {
        int t = start;
        int q = 0;
        if (text.charAt(start) == ' ' && query.charAt(0) != ' ') return -1;
        while (q < query.length()) {
            if (t >= text.length()) return -1;
            char qc = query.charAt(q);
            char tc = text.charAt(t);
            if (qc == ' ') {
                if (tc != ' ' && tc != '-') return -1;
                // Swallow a hyphenation at the end of a line ("infor-\nmation") or whitespace runs.
                while (t < text.length() && (text.charAt(t) == ' ' || text.charAt(t) == '-')) t++;
                q++;
                continue;
            }
            if (qc != tc) {
                // Allow "infor-\r\nmation" to match "information".
                if (tc == '-' && t + 1 < text.length() && text.charAt(t + 1) == ' ') {
                    int skip = t + 1;
                    while (skip < text.length() && text.charAt(skip) == ' ') skip++;
                    if (skip < text.length() && text.charAt(skip) == qc) {
                        t = skip;
                        continue;
                    }
                }
                return -1;
            }
            t++;
            q++;
        }
        return t;
    }

    @NonNull
    static String excerpt(@NonNull String text, int start, int end) {
        int from = Math.max(0, start - 30);
        int to = Math.min(text.length(), end + 50);
        String s = text.substring(from, to).replaceAll("\\s+", " ").trim();
        return (from > 0 ? "…" : "") + s + (to < text.length() ? "…" : "");
    }

    /** Locale-safe lower-casing for display purposes. */
    @NonNull
    public static String lower(@NonNull String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
