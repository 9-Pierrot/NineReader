package com.ninereader.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ReadingStatsTest {

    @Test
    public void usesDefaultSpeedWithoutHistory() {
        ReadingStats s = new ReadingStats(0, 0, 10);
        assertEquals(ReadingStats.DEFAULT_MS_PER_PAGE, s.msPerPage());
        assertEquals(10 * ReadingStats.DEFAULT_MS_PER_PAGE, s.estimatedRemainingMs());
    }

    @Test
    public void usesMeasuredSpeed() {
        ReadingStats s = new ReadingStats(600_000, 10, 5);
        assertEquals(60_000, s.msPerPage());
        assertEquals(300_000, s.estimatedRemainingMs());
    }

    @Test
    public void clampsNegativeValues() {
        ReadingStats s = new ReadingStats(-5, -1, -3);
        assertEquals(0, s.totalTimeMs);
        assertEquals(0, s.pagesRead);
        assertEquals(0, s.pagesRemaining);
    }

    @Test
    public void formatsDurations() {
        assertEquals("< 1", ReadingStats.formatDuration(30_000, "h", "min", "< 1"));
        assertEquals("12 min", ReadingStats.formatDuration(12 * 60_000, "h", "min", "< 1"));
        assertEquals("1 h 05 min", ReadingStats.formatDuration(65 * 60_000, "h", "min", "< 1"));
        assertEquals("2 h 30 min", ReadingStats.formatDuration(150 * 60_000, "h", "min", "< 1"));
    }
}
