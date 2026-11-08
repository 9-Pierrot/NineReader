package com.ninereader.reader;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Page layouts of the reader: continuous scroll (LEC-02), page by page (LEC-03), double page (LEC-14). */
public enum ReadingMode {
    CONTINUOUS("continuous"),
    PAGED("paged"),
    DOUBLE("double");

    @NonNull
    public final String key;

    ReadingMode(@NonNull String key) {
        this.key = key;
    }

    @NonNull
    public static ReadingMode fromKey(@Nullable String key, @NonNull ReadingMode fallback) {
        if (key == null) return fallback;
        for (ReadingMode m : values()) {
            if (m.key.equals(key)) return m;
        }
        return fallback;
    }

    /** Whether pages are laid out horizontally with snapping (one spread per screen). */
    public boolean isHorizontal() {
        return this != CONTINUOUS;
    }

    public int pagesPerSpread() {
        return this == DOUBLE ? 2 : 1;
    }
}
