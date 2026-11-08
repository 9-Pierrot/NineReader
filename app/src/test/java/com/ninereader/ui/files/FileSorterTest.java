package com.ninereader.ui.files;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FileSorterTest {

    private static FileRow row(FileRow.Kind kind, String title, long size, long date) {
        FileRow r = new FileRow(kind, title, "content://" + title);
        r.size = size;
        r.date = date;
        return r;
    }

    private final List<FileRow> rows = Arrays.asList(
            row(FileRow.Kind.DOCUMENT, "zèbre.pdf", 10, 3),
            row(FileRow.Kind.DOCUMENT, "Écologie.pdf", 30, 1),
            row(FileRow.Kind.FOLDER, "Cours", -1, 0),
            row(FileRow.Kind.DOCUMENT, "agenda.pdf", 20, 2));

    private static List<String> titles(List<FileRow> list) {
        List<String> out = new ArrayList<>();
        for (FileRow r : list) out.add(r.title);
        return out;
    }

    @Test
    public void sortsByNameFoldersFirst() {
        assertEquals(Arrays.asList("Cours", "agenda.pdf", "Écologie.pdf", "zèbre.pdf"),
                titles(FileSorter.apply(rows, FileSorter.SORT_NAME, "")));
    }

    @Test
    public void sortsByDateDescending() {
        assertEquals(Arrays.asList("Cours", "zèbre.pdf", "agenda.pdf", "Écologie.pdf"),
                titles(FileSorter.apply(rows, FileSorter.SORT_DATE, "")));
    }

    @Test
    public void sortsBySizeDescending() {
        assertEquals(Arrays.asList("Cours", "Écologie.pdf", "agenda.pdf", "zèbre.pdf"),
                titles(FileSorter.apply(rows, FileSorter.SORT_SIZE, "")));
    }

    @Test
    public void filtersAccentInsensitive() {
        assertEquals(Arrays.asList("Écologie.pdf"), titles(FileSorter.apply(rows, FileSorter.SORT_NAME, "ecolo")));
        assertEquals(0, FileSorter.apply(rows, FileSorter.SORT_NAME, "xyz").size());
    }
}
