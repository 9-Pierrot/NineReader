package com.ninereader.annotations;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Exports the annotation list as plain text or Markdown (ANN-06). */
public final class AnnotationExporter {

    /** One exported entry (pending draft or annotation already stored in the PDF). */
    public static final class Entry {
        public final int page;
        @NonNull
        public final String kind;
        @NonNull
        public final String text;

        public Entry(int page, @NonNull String kind, @NonNull String text) {
            this.page = page;
            this.kind = kind;
            this.text = text;
        }
    }

    private AnnotationExporter() {
    }

    @NonNull
    public static String toText(@NonNull String title, @NonNull List<Entry> entries, @NonNull String pageWord) {
        StringBuilder sb = new StringBuilder(title).append("\n");
        for (int i = 0; i < title.length(); i++) sb.append('=');
        sb.append("\n\n");
        for (Entry e : sorted(entries)) {
            sb.append('[').append(pageWord).append(' ').append(e.page + 1).append("] ").append(e.kind);
            if (!e.text.isEmpty()) sb.append(" : ").append(e.text.replace('\n', ' '));
            sb.append('\n');
        }
        return sb.toString();
    }

    @NonNull
    public static String toMarkdown(@NonNull String title, @NonNull List<Entry> entries, @NonNull String pageWord) {
        StringBuilder sb = new StringBuilder("# ").append(escape(title)).append("\n\n");
        int lastPage = -1;
        for (Entry e : sorted(entries)) {
            if (e.page != lastPage) {
                sb.append("\n## ").append(pageWord).append(' ').append(e.page + 1).append("\n\n");
                lastPage = e.page;
            }
            sb.append("- **").append(escape(e.kind)).append("**");
            if (!e.text.isEmpty()) {
                sb.append(" — ");
                if (e.text.contains("\n")) {
                    sb.append("\n\n  > ").append(escape(e.text).replace("\n", "\n  > ")).append("\n");
                } else {
                    sb.append(escape(e.text));
                }
            }
            sb.append('\n');
        }
        return sb.toString().replaceAll("\n{3,}", "\n\n");
    }

    private static List<Entry> sorted(List<Entry> entries) {
        List<Entry> copy = new ArrayList<>(entries);
        copy.sort(Comparator.comparingInt(e -> e.page));
        return copy;
    }

    static String escape(String s) {
        return s.replace("*", "\\*").replace("_", "\\_").replace("#", "\\#");
    }
}
