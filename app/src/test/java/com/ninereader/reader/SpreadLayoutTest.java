package com.ninereader.reader;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SpreadLayoutTest {

    @Test
    public void singlePage() {
        SpreadLayout l = new SpreadLayout(5, false, true);
        assertEquals(5, l.spreadCount());
        assertEquals(3, l.firstPage(3));
        assertEquals(1, l.pagesIn(3));
        assertEquals(4, l.spreadOf(4));
        assertEquals(4, l.spreadOf(99));
    }

    @Test
    public void doublePageWithCover() {
        SpreadLayout l = new SpreadLayout(6, true, true);
        // [0] [1,2] [3,4] [5]
        assertEquals(4, l.spreadCount());
        assertEquals(0, l.firstPage(0));
        assertEquals(1, l.pagesIn(0));
        assertEquals(1, l.firstPage(1));
        assertEquals(2, l.pagesIn(1));
        assertEquals(5, l.firstPage(3));
        assertEquals(1, l.pagesIn(3));
        assertEquals(0, l.spreadOf(0));
        assertEquals(1, l.spreadOf(2));
        assertEquals(2, l.spreadOf(3));
        assertEquals(3, l.spreadOf(5));
    }

    @Test
    public void doublePageWithoutCover() {
        SpreadLayout l = new SpreadLayout(5, true, false);
        // [0,1] [2,3] [4]
        assertEquals(3, l.spreadCount());
        assertEquals(2, l.firstPage(1));
        assertEquals(1, l.pagesIn(2));
        assertEquals(1, l.spreadOf(3));
    }

    @Test
    public void edgeCases() {
        assertEquals(0, new SpreadLayout(0, true, true).spreadCount());
        assertEquals(1, new SpreadLayout(1, true, true).spreadCount());
        assertEquals(2, new SpreadLayout(2, true, true).spreadCount());
        assertEquals(2, new SpreadLayout(3, true, true).spreadCount());
    }

    @Test
    public void readingModes() {
        assertEquals(ReadingMode.PAGED, ReadingMode.fromKey("paged", ReadingMode.CONTINUOUS));
        assertEquals(ReadingMode.CONTINUOUS, ReadingMode.fromKey("bogus", ReadingMode.CONTINUOUS));
        assertEquals(ReadingMode.DOUBLE, ReadingMode.fromKey(null, ReadingMode.DOUBLE));
        assertEquals(2, ReadingMode.DOUBLE.pagesPerSpread());
        assertEquals(false, ReadingMode.CONTINUOUS.isHorizontal());
    }
}
