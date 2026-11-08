package com.ninereader.reader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class SentenceSplitterTest {

    @Test
    public void splitsSentencesWithRanges() {
        String text = "Bonjour. Comment allez-vous ? Très bien !";
        List<SentenceSplitter.Sentence> s = SentenceSplitter.split(text);
        assertEquals(3, s.size());
        assertEquals("Bonjour.", s.get(0).text);
        assertEquals("Comment allez-vous ?", s.get(1).text);
        assertEquals(text.indexOf("Très"), s.get(2).start);
        assertEquals(text.length(), s.get(2).end);
    }

    @Test
    public void ignoresAbbreviationsAndInitials() {
        List<SentenceSplitter.Sentence> s = SentenceSplitter.split("M. Dupont et J. Martin voir fig. 3. Fin.");
        assertEquals(2, s.size());
        assertTrue(s.get(0).text.startsWith("M. Dupont"));
    }

    @Test
    public void cleansHyphenationAndSkipsEmpty() {
        List<SentenceSplitter.Sentence> s = SentenceSplitter.split("Une infor-\nmation. ... 123.");
        assertEquals("Une information.", s.get(0).text);
        assertEquals(2, s.size());
    }

    @Test
    public void breaksVeryLongSentences() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) sb.append("mot, ");
        List<SentenceSplitter.Sentence> s = SentenceSplitter.split(sb.toString());
        assertTrue(s.size() > 1);
        for (SentenceSplitter.Sentence x : s) assertTrue(x.text.length() <= SentenceSplitter.MAX_LEN + 10);
    }

    @Test
    public void paragraphBreakIsBoundary() {
        List<SentenceSplitter.Sentence> s = SentenceSplitter.split("Titre sans point\n\nTexte suivant");
        assertEquals(2, s.size());
    }
}
