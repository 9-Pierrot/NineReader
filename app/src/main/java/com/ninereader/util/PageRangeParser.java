package com.ninereader.util;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses page range expressions such as "1-3, 5, 8-" (1-based, inclusive) into 0-based indexes.
 */
public final class PageRangeParser {

    private PageRangeParser() {
    }

    /**
     * @param expr      range expression; "" or "*" means all pages
     * @param pageCount number of pages of the document
     * @return ordered, de-duplicated 0-based page indexes
     * @throws IllegalArgumentException when the expression is invalid or out of bounds
     */
    @NonNull
    public static List<Integer> parse(@NonNull String expr, int pageCount) {
        if (pageCount <= 0) throw new IllegalArgumentException("Empty document");
        String e = expr.trim();
        Set<Integer> out = new LinkedHashSet<>();
        if (e.isEmpty() || "*".equals(e)) {
            for (int i = 0; i < pageCount; i++) out.add(i);
            return new ArrayList<>(out);
        }
        for (String rawPart : e.split("[,;]")) {
            String part = rawPart.trim();
            if (part.isEmpty()) continue;
            int dash = part.indexOf('-');
            int from;
            int to;
            if (dash < 0) {
                from = to = parseNumber(part);
            } else {
                String a = part.substring(0, dash).trim();
                String b = part.substring(dash + 1).trim();
                from = a.isEmpty() ? 1 : parseNumber(a);
                to = b.isEmpty() ? pageCount : parseNumber(b);
            }
            if (from < 1 || to < 1 || from > pageCount || to > pageCount) {
                throw new IllegalArgumentException("Out of range: " + part);
            }
            if (from <= to) {
                for (int p = from; p <= to; p++) out.add(p - 1);
            } else {
                for (int p = from; p >= to; p--) out.add(p - 1);
            }
        }
        if (out.isEmpty()) throw new IllegalArgumentException("No page selected");
        return new ArrayList<>(out);
    }

    private static int parseNumber(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Not a number: " + s, ex);
        }
    }

    /** Formats 0-based indexes back to a compact 1-based expression ("1-3, 5"). */
    @NonNull
    public static String format(@NonNull List<Integer> pages) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < pages.size()) {
            int start = pages.get(i);
            int end = start;
            while (i + 1 < pages.size() && pages.get(i + 1) == end + 1) {
                end = pages.get(++i);
            }
            if (sb.length() > 0) sb.append(", ");
            sb.append(start + 1);
            if (end != start) sb.append('-').append(end + 1);
            i++;
        }
        return sb.toString();
    }
}
