package com.ninereader.reader;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits page text into sentences for text-to-speech (LEC-13), keeping their character ranges in the
 * original text so the sentence being read can be highlighted on the page.
 */
public final class SentenceSplitter {

    /** Maximum sentence length sent to the speech engine at once. */
    static final int MAX_LEN = 380;

    private SentenceSplitter() {
    }

    /** A sentence: [start, end) in the source text plus the cleaned text to speak. */
    public static final class Sentence {
        public final int start;
        public final int end;
        @NonNull
        public final String text;

        Sentence(int start, int end, @NonNull String text) {
            this.start = start;
            this.end = end;
            this.text = text;
        }
    }

    @NonNull
    public static List<Sentence> split(@NonNull String text) {
        List<Sentence> out = new ArrayList<>();
        int n = text.length();
        int start = 0;
        while (start < n && Character.isWhitespace(text.charAt(start))) start++;
        int i = start;
        while (i < n) {
            char c = text.charAt(i);
            boolean boundary = false;
            if (c == '.' || c == '!' || c == '?' || c == '…') {
                int j = i + 1;
                while (j < n && (text.charAt(j) == '.' || text.charAt(j) == '"' || text.charAt(j) == '»'
                        || text.charAt(j) == ')')) j++;
                if (j >= n || Character.isWhitespace(text.charAt(j))) {
                    boundary = !isAbbreviation(text, i);
                    i = j - 1;
                }
            } else if (c == '\n' && i + 1 < n && (text.charAt(i + 1) == '\n'
                    || (i + 2 < n && text.charAt(i + 1) == '\r' && text.charAt(i + 2) == '\n'))) {
                boundary = true;
            } else if (i - start >= MAX_LEN && (c == ',' || c == ';' || Character.isWhitespace(c))) {
                boundary = true;
            }
            if (boundary) {
                add(out, text, start, i + 1);
                start = i + 1;
                while (start < n && Character.isWhitespace(text.charAt(start))) start++;
                i = start;
                continue;
            }
            i++;
        }
        if (start < n) add(out, text, start, n);
        return out;
    }

    private static boolean isAbbreviation(String text, int dot) {
        int k = dot - 1;
        while (k >= 0 && Character.isLetter(text.charAt(k))) k--;
        String word = text.substring(k + 1, dot);
        if (word.length() == 1 && Character.isUpperCase(word.charAt(0))) return true; // initials "J. Doe"
        switch (word.toLowerCase(java.util.Locale.ROOT)) {
            case "m":
            case "mme":
            case "mr":
            case "mrs":
            case "dr":
            case "etc":
            case "p":
            case "fig":
            case "cf":
            case "vs":
            case "ex":
            case "no":
                return !"etc".equalsIgnoreCase(word);
            default:
                return false;
        }
    }

    private static void add(List<Sentence> out, String text, int start, int end) {
        String s = text.substring(start, end)
                .replaceAll("-\\s*\\r?\\n\\s*", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (!s.isEmpty() && containsLetterOrDigit(s)) out.add(new Sentence(start, end, s));
    }

    private static boolean containsLetterOrDigit(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetterOrDigit(s.charAt(i))) return true;
        }
        return false;
    }
}
