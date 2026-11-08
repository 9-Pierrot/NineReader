package com.ninereader.reader;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PageTransformTest {

    @Test
    public void simpleTransformFlipsY() {
        PageTransform t = PageTransform.simple(600, 800);
        float[] out = new float[2];
        t.toNormalized(0, 800, out);
        assertArrayEquals(new float[]{0, 0}, out, 1e-5f);
        t.toNormalized(600, 0, out);
        assertArrayEquals(new float[]{1, 1}, out, 1e-5f);
    }

    @Test
    public void inverseRoundTrip() {
        // 90° rotated page: x' = y / h, y' = x / w
        PageTransform t = PageTransform.fromSamples(100, 0, 0, 0, 100 / 600.0, 100 / 800.0, 0);
        float[] n = new float[2];
        float[] p = new float[2];
        t.toNormalized(150, 300, n);
        t.toPage(n[0], n[1], p);
        assertEquals(150, p[0], 1e-3f);
        assertEquals(300, p[1], 1e-3f);
    }

    @Test
    public void rectToNormalizedIsOrdered() {
        PageTransform t = PageTransform.simple(100, 100);
        float[] r = t.rectToNormalized(10, 90, 30, 70);
        assertArrayEquals(new float[]{0.1f, 0.1f, 0.3f, 0.3f}, r, 1e-5f);
        assertEquals(0.01, t.unitScaleX(), 1e-9);
    }

    @Test
    public void degenerateInverse() {
        PageTransform t = new PageTransform(0, 0, 0, 0, 0, 0);
        float[] p = new float[]{5, 5};
        t.toPage(0.5, 0.5, p);
        assertArrayEquals(new float[]{0, 0}, p, 0f);
    }
}
