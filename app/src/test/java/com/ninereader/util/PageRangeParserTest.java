package com.ninereader.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class PageRangeParserTest {

    @Test
    public void emptyMeansAllPages() {
        assertEquals(Arrays.asList(0, 1, 2), PageRangeParser.parse("", 3));
        assertEquals(Arrays.asList(0, 1, 2), PageRangeParser.parse(" * ", 3));
    }

    @Test
    public void parsesSinglesAndRanges() {
        assertEquals(Arrays.asList(0, 1, 2, 4), PageRangeParser.parse("1-3, 5", 10));
    }

    @Test
    public void openRanges() {
        assertEquals(Arrays.asList(7, 8, 9), PageRangeParser.parse("8-", 10));
        assertEquals(Arrays.asList(0, 1), PageRangeParser.parse("-2", 10));
    }

    @Test
    public void reversedRangeKeepsOrderAndDeduplicates() {
        assertEquals(Arrays.asList(2, 1, 0), PageRangeParser.parse("3-1;2", 5));
    }

    @Test(expected = IllegalArgumentException.class)
    public void outOfRangeFails() {
        PageRangeParser.parse("4", 3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void garbageFails() {
        PageRangeParser.parse("a-b", 3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyDocumentFails() {
        PageRangeParser.parse("1", 0);
    }

    @Test
    public void formatsCompactRanges() {
        List<Integer> pages = Arrays.asList(0, 1, 2, 4, 6, 7);
        assertEquals("1-3, 5, 7-8", PageRangeParser.format(pages));
    }
}
