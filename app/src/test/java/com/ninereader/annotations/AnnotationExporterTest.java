package com.ninereader.annotations;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class AnnotationExporterTest {

    private final List<AnnotationExporter.Entry> entries = Arrays.asList(
            new AnnotationExporter.Entry(4, "Note", "relire *ce* passage"),
            new AnnotationExporter.Entry(0, "Surligner", "Introduction"),
            new AnnotationExporter.Entry(0, "Dessin", ""));

    @Test
    public void textIsSortedByPage() {
        String t = AnnotationExporter.toText("Cours", entries, "Page");
        assertTrue(t.startsWith("Cours\n=====\n"));
        assertTrue(t.indexOf("[Page 1] Surligner : Introduction") < t.indexOf("[Page 5] Note"));
        assertTrue(t.contains("[Page 1] Dessin\n"));
    }

    @Test
    public void markdownGroupsPagesAndEscapes() {
        String md = AnnotationExporter.toMarkdown("Cours_1", entries, "Page");
        assertTrue(md.startsWith("# Cours\\_1"));
        assertTrue(md.contains("## Page 1"));
        assertTrue(md.contains("## Page 5"));
        assertTrue(md.contains("relire \\*ce\\* passage"));
    }

    @Test
    public void markdownQuotesMultilineText() {
        String md = AnnotationExporter.toMarkdown("T",
                Arrays.asList(new AnnotationExporter.Entry(1, "Note", "ligne 1\nligne 2")), "Page");
        assertTrue(md.contains("> ligne 1\n  > ligne 2"));
    }
}
