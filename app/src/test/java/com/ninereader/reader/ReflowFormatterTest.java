package com.ninereader.reader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class ReflowFormatterTest {

    @Test
    public void joinsWrappedLinesAndHyphenation() {
        String page = "Ceci est une longue ligne de texte qui conti-\r\nnue sur la ligne suivante sans coupure\r\nvisible pour le lecteur final ici.\r\n\r\nNouveau paragraphe.";
        List<String> p = ReflowFormatter.paragraphs(page);
        assertEquals(2, p.size());
        assertTrue(p.get(0).contains("continue sur la ligne"));
        assertTrue(p.get(0).contains("coupure visible"));
        assertEquals("Nouveau paragraphe.", p.get(1));
    }

    @Test
    public void splitsListItems() {
        String page = "Introduction de la liste :\n- premier\n- second\n• troisième";
        List<String> p = ReflowFormatter.paragraphs(page);
        assertEquals(4, p.size());
        assertEquals("- second", p.get(2));
    }

    @Test
    public void shortLineEndingWithPeriodEndsParagraph() {
        String page = "Une ligne longue qui occupe toute la largeur de la colonne de texte.\nFin.\nSuite du texte ici qui est assez long pour une ligne.";
        List<String> p = ReflowFormatter.paragraphs(page);
        assertEquals(2, p.size());
    }

    @Test
    public void detectsUsableText() {
        assertFalse(ReflowFormatter.hasUsableText("12 34 -- ."));
        assertTrue(ReflowFormatter.hasUsableText("Lorem ipsum dolor sit amet, consectetur adipiscing elit sed do"));
    }
}
