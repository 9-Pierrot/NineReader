package com.ninereader.ui.files;

import androidx.annotation.NonNull;

import com.ninereader.reader.TextSearch;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Sorting (name, date, size) and name filtering of explorer rows (BIB-02). Folders always come first. */
public final class FileSorter {

    public static final String SORT_NAME = "name";
    public static final String SORT_DATE = "date";
    public static final String SORT_SIZE = "size";

    private FileSorter() {
    }

    @NonNull
    public static List<FileRow> apply(@NonNull List<FileRow> rows, @NonNull String sort, @NonNull String query) {
        String q = TextSearch.normalizeQuery(query);
        List<FileRow> out = new ArrayList<>();
        for (FileRow r : rows) {
            if (q.isEmpty() || TextSearch.fold(r.title).contains(q)) out.add(r);
        }
        Collator collator = Collator.getInstance(Locale.getDefault());
        collator.setStrength(Collator.SECONDARY);
        Comparator<FileRow> byName = (a, b) -> collator.compare(a.title, b.title);
        Comparator<FileRow> cmp;
        switch (sort) {
            case SORT_DATE:
                cmp = Comparator.comparingLong((FileRow r) -> r.date).reversed().thenComparing(byName);
                break;
            case SORT_SIZE:
                cmp = Comparator.comparingLong((FileRow r) -> r.size).reversed().thenComparing(byName);
                break;
            case SORT_NAME:
            default:
                cmp = byName;
                break;
        }
        Comparator<FileRow> foldersFirst = Comparator.comparingInt((FileRow r) -> r.isFolderLike() ? 0 : 1);
        out.sort(foldersFirst.thenComparing(cmp));
        return out;
    }
}
