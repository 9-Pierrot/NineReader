package com.ninereader.pdfops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CompressionLevelTest {

    @Test
    public void estimateNeverExceedsCurrentSize() {
        assertEquals(1000, CompressionLevel.LOW.estimateImage(4000, 3000, 1000));
    }

    @Test
    public void higherLevelsAreSmaller() {
        long low = CompressionLevel.LOW.estimateImage(4000, 3000, 10_000_000);
        long medium = CompressionLevel.MEDIUM.estimateImage(4000, 3000, 10_000_000);
        long high = CompressionLevel.HIGH.estimateImage(4000, 3000, 10_000_000);
        assertTrue(low > medium);
        assertTrue(medium > high);
    }

    @Test
    public void downsamplesToMaxDimension() {
        // 2200 px max at LOW: 4400x2200 -> 2200x1100 px * 0.32 B/px
        assertEquals(Math.round(2200 * 1100 * 0.32), CompressionLevel.LOW.estimateImage(4400, 2200, Long.MAX_VALUE));
    }

    @Test
    public void fileEstimate() {
        assertEquals(Math.round(1000 * 0.95) + 500, CompressionLevel.estimateFile(3000, 2000, 500));
        assertEquals(1, CompressionLevel.estimateFile(0, 0, 0));
    }

    @Test
    public void fromKey() {
        assertEquals(CompressionLevel.HIGH, CompressionLevel.fromKey("high"));
        assertEquals(CompressionLevel.MEDIUM, CompressionLevel.fromKey("nope"));
        assertEquals(CompressionLevel.MEDIUM, CompressionLevel.fromKey(null));
    }
}
