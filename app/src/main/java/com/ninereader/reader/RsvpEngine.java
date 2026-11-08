package com.ninereader.reader;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Rapid Serial Visual Presentation (RAP-04): splits text into words and computes per-word display
 * durations and the optimal recognition point (fixation letter).
 */
public final class RsvpEngine {

    public static final int MIN_WPM = 200;
    public static final int MAX_WPM = 600;

    private RsvpEngine() {
    }

    @NonNull
    public static List<String> words(@NonNull String text) {
        List<String> out = new ArrayList<>();
        String cleaned = text.replaceAll("-\\s*\\r?\\n\\s*", "");
        for (String w : cleaned.split("\\s+")) {
            if (!w.isEmpty()) out.add(w);
        }
        return out;
    }

    public static int clampWpm(int wpm) {
        return Math.max(MIN_WPM, Math.min(MAX_WPM, wpm));
    }

    /** Display time of a word: longer words and punctuation get extra time. */
    public static long durationMs(@NonNull String word, int wpm) {
        double base = 60_000.0 / clampWpm(wpm);
        double factor = 1.0;
        int len = word.length();
        if (len > 8) factor += 0.3;
        if (len > 12) factor += 0.3;
        char last = word.charAt(len - 1);
        if (last == '.' || last == '!' || last == '?' || last == ':' || last == ';') {
            factor += 1.0;
        } else if (last == ',') {
            factor += 0.5;
        }
        return Math.round(base * factor);
    }

    /** Index of the fixation (optimal recognition point) letter of a word. */
    public static int pivot(@NonNull String word) {
        int len = word.length();
        int p;
        if (len <= 1) {
            p = 0;
        } else if (len <= 5) {
            p = 1;
        } else if (len <= 9) {
            p = 2;
        } else if (len <= 13) {
            p = 3;
        } else {
            p = 4;
        }
        // Skip leading punctuation such as quotes or parentheses.
        int lead = 0;
        while (lead < len - 1 && !Character.isLetterOrDigit(word.charAt(lead))) lead++;
        return Math.min(len - 1, p + lead);
    }
}
