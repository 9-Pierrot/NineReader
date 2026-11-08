package com.ninereader.reader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class TextSearchTest {

    @Test
    public void foldKeepsLength() {
        String s = "Éléphant\r\nÇa Œuvre";
        assertEquals(s.length(), TextSearch.fold(s).length());
        assertEquals("elephant  ca ouvre", TextSearch.fold(s));
    }

    @Test
    public void findsCaseAndAccentInsensitive() {
        String page = "Le Théorème de Pythagore. le theoreme est connu.";
        List<TextSearch.Match> m = TextSearch.findInPage(3, page, TextSearch.normalizeQuery("THÉORÈME"), 100);
        assertEquals(2, m.size());
        assertEquals(3, m.get(0).page);
        assertEquals(3, m.get(0).start);
        assertEquals(8, m.get(0).length);
        assertEquals("Théorème", page.substring(m.get(0).start, m.get(0).start + m.get(0).length));
    }

    @Test
    public void matchesAcrossLineBreaks() {
        String page = "la loi de\r\nOhm s'applique";
        List<TextSearch.Match> m = TextSearch.findInPage(0, page, TextSearch.normalizeQuery("de   ohm"), 10);
        assertEquals(1, m.size());
        assertEquals("de\r\nOhm", page.substring(m.get(0).start, m.get(0).start + m.get(0).length));
    }

    @Test
    public void matchesHyphenatedWords() {
        String page = "une infor-\r\nmation utile";
        List<TextSearch.Match> m = TextSearch.findInPage(0, page, TextSearch.normalizeQuery("information"), 10);
        assertEquals(1, m.size());
    }

    @Test
    public void respectsMaxResultsAndEmptyQuery() {
        String page = "a a a a a";
        assertEquals(2, TextSearch.findInPage(0, page, "a", 2).size());
        assertTrue(TextSearch.findInPage(0, page, "", 10).isEmpty());
        assertTrue(TextSearch.findInPage(0, "", "a", 10).isEmpty());
    }

    @Test
    public void excerptIsShortened() {
        String page = "0123456789012345678901234567890123456789 cible 0123456789012345678901234567890123456789012345678901234567890";
        TextSearch.Match m = TextSearch.findInPage(0, page, "cible", 1).get(0);
        assertTrue(m.excerpt.startsWith("…"));
        assertTrue(m.excerpt.contains("cible"));
        assertTrue(m.excerpt.endsWith("…"));
    }
}
