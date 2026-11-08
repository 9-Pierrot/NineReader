package com.ninereader.reader;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class MarginDetectorTest {

    private static int[] page(int w, int h, int l, int t, int r, int b) {
        int[] px = new int[w * h];
        Arrays.fill(px, 0xFFFFFFFF);
        for (int y = t; y < b; y++) {
            for (int x = l; x < r; x++) px[y * w + x] = 0xFF000000;
        }
        return px;
    }

    @Test
    public void detectsContentBox() {
        float[] c = MarginDetector.detect(page(100, 200, 20, 40, 80, 160), 100, 200);
        assertEquals(0.18f, c[0], 0.01f);
        assertEquals(0.18f, c[1], 0.01f);
        assertEquals(0.82f, c[2], 0.01f);
        assertEquals(0.82f, c[3], 0.01f);
    }

    @Test
    public void blankPageIsNotCropped() {
        assertArrayEquals(new float[]{0, 0, 1, 1}, MarginDetector.detect(page(50, 50, 0, 0, 0, 0), 50, 50), 0f);
    }

    @Test
    public void fullBleedIsNotCropped() {
        assertArrayEquals(new float[]{0, 0, 1, 1}, MarginDetector.detect(page(50, 50, 0, 0, 50, 50), 50, 50), 0f);
    }

    @Test
    public void tinyContentIsIgnored() {
        float[] c = MarginDetector.detect(page(100, 100, 50, 50, 55, 55), 100, 100);
        assertArrayEquals(new float[]{0, 0, 1, 1}, c, 0f);
    }

    @Test
    public void union() {
        float[] u = MarginDetector.union(new float[]{0.1f, 0.2f, 0.8f, 0.9f}, new float[]{0.2f, 0.1f, 0.9f, 0.8f});
        assertArrayEquals(new float[]{0.1f, 0.1f, 0.9f, 0.9f}, u, 0f);
        assertTrue(u[2] > u[0]);
    }
}
