package com.ninereader.ui.reader;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TextSelectionTest {

    @Test
    public void wordAtExpandsToWord() {
        String t = "Le chat-noir dort.";
        assertArrayEquals(new int[]{3, 12}, TextSelection.wordAt(t, 5));
        assertArrayEquals(new int[]{2, 3}, TextSelection.wordAt(t, 2)); // space
        assertArrayEquals(new int[]{17, 18}, TextSelection.wordAt(t, 99)); // clamped to the final '.'
    }

    @Test
    public void emptyText() {
        assertArrayEquals(new int[]{0, 0}, TextSelection.wordAt("", 3));
    }

    @Test
    public void cleanJoinsLines() {
        assertEquals("une information sur deux lignes", TextSelection.clean("une infor-\r\nmation sur\r\ndeux  lignes "));
    }
}
