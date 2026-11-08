package com.ninereader.reader;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Liquid mode (LEC-12): rebuilds paragraphs from the raw text of a PDF page so that it can be
 * re-flowed to the screen width. Heuristics: join wrapped lines, remove end-of-line hyphenation,
 * split paragraphs on blank lines, short lines ending with punctuation and list markers.
 */
public final class ReflowFormatter {

    private ReflowFormatter() {
    }

    @NonNull
    public static List<String> paragraphs(@NonNull String pageText) {
        String[] rawLines = pageText.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        List<String> lines = new ArrayList<>();
        int maxLen = 0;
        for (String l : rawLines) {
            String t = l.replaceAll("[ \\t\\u00A0]+", " ").trim();
            lines.add(t);
            maxLen = Math.max(maxLen, t.length());
        }
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isEmpty()) {
                flush(current, out);
                continue;
            }
            if (current.length() > 0 && startsNewBlock(line)) flush(current, out);
            if (current.length() > 0) {
                int last = current.length() - 1;
                char lc = current.charAt(last);
                if (lc == '-' && last > 0 && Character.isLetter(current.charAt(last - 1))
                        && Character.isLowerCase(line.charAt(0))) {
                    current.setLength(last); // "infor-" + "mation"
                } else {
                    current.append(' ');
                }
            }
            current.append(line);
            boolean shortLine = maxLen > 0 && line.length() < maxLen * 0.6;
            if (shortLine && endsSentence(line)) flush(current, out);
        }
        flush(current, out);
        return out;
    }

    private static boolean startsNewBlock(String line) {
        char c = line.charAt(0);
        return c == '•' || c == '•' || c == '–' || c == '▪' || line.matches("^(\\d+[.)]|[-*])\\s.*");
    }

    private static boolean endsSentence(String line) {
        char c = line.charAt(line.length() - 1);
        return c == '.' || c == ':' || c == '!' || c == '?' || c == '»' || c == '"';
    }

    private static void flush(StringBuilder sb, List<String> out) {
        String s = sb.toString().trim();
        if (!s.isEmpty()) out.add(s);
        sb.setLength(0);
    }

    /** Whether a page has enough extracted text for the liquid mode to be meaningful. */
    public static boolean hasUsableText(@NonNull String pageText) {
        int letters = 0;
        for (int i = 0; i < pageText.length() && letters < 40; i++) {
            if (Character.isLetter(pageText.charAt(i))) letters++;
        }
        return letters >= 40;
    }
}
