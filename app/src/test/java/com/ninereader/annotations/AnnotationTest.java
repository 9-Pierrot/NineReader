package com.ninereader.annotations;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class AnnotationTest {

    @Test
    public void boundingBoxOfMarkup() {
        Annotation a = new Annotation();
        a.rects.add(new float[]{10, 20, 50, 30});
        a.rects.add(new float[]{5, 5, 40, 15});
        assertArrayEquals(new float[]{5, 5, 50, 30}, a.boundingBox(), 0f);
    }

    @Test
    public void inkHitTestAndPaddedBox() {
        Annotation a = new Annotation();
        a.type = Annotation.Type.INK;
        a.width = 2;
        a.strokes.add(new float[]{0, 0, 10, 10});
        assertTrue(a.hitTest(10, 11, 1));
        assertFalse(a.hitTest(50, 50, 1));
        assertArrayEquals(new float[]{-2, -2, 12, 12}, a.boundingBox(), 0f);
    }

    @Test
    public void lineHitTestUsesSegmentDistance() {
        Annotation a = new Annotation();
        a.type = Annotation.Type.LINE;
        a.width = 1;
        a.bounds = new float[]{0, 0, 100, 0};
        assertTrue(a.hitTest(50, 1.5f, 1));
        assertFalse(a.hitTest(50, 10, 1));
        assertEquals(5.0, Annotation.distanceToSegment(0, 5, 0, 0, 0, 0), 1e-9);
    }

    @Test
    public void translateMovesEverything() {
        Annotation a = new Annotation();
        a.rects.add(new float[]{0, 0, 1, 1});
        a.strokes.add(new float[]{0, 0});
        a.bounds = new float[]{0, 0, 2, 2};
        a.translate(5, -1);
        assertArrayEquals(new float[]{5, -1, 6, 0}, a.rects.get(0), 0f);
        assertArrayEquals(new float[]{5, -1}, a.strokes.get(0), 0f);
        assertArrayEquals(new float[]{5, -1, 7, 1}, a.bounds, 0f);
    }

    @Test
    public void copyIsDeep() {
        Annotation a = new Annotation();
        a.bounds = new float[]{1, 2, 3, 4};
        a.rects.add(new float[]{1, 1, 2, 2});
        Annotation c = a.copy();
        c.bounds[0] = 99;
        c.rects.get(0)[0] = 99;
        assertEquals(1f, a.bounds[0], 0f);
        assertEquals(1f, a.rects.get(0)[0], 0f);
        assertEquals(a.id, c.id);
    }

    @Test
    public void typeHelpers() {
        assertTrue(Annotation.Type.HIGHLIGHT.isTextMarkup());
        assertTrue(Annotation.Type.IMAGE.isBoxed());
        assertTrue(Annotation.Type.REDACT.isBoxed());
        assertTrue(Annotation.Type.ARROW.isLine());
        assertFalse(Annotation.Type.INK.isBoxed());
        assertEquals(13, Arrays.asList(Annotation.Type.values()).size());
    }

    @Test
    public void emptyBoundingBox() {
        assertArrayEquals(new float[]{0, 0, 0, 0}, new Annotation().boundingBox(), 0f);
    }
}
