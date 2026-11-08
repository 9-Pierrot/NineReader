package com.ninereader.pdfops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class PagePlanTest {

    @Test
    public void identity() {
        PagePlan p = PagePlan.identity(3);
        assertEquals(3, p.size());
        assertTrue(p.isIdentity(3));
        assertTrue(p.keepsOrder());
        assertFalse(p.isIdentity(4));
    }

    @Test
    public void operations() {
        PagePlan p = PagePlan.identity(4);
        p.move(0, 3);           // 1 2 3 0
        p.rotate(0, 90);        // 1(90)
        p.rotate(0, -180);      // 1(270)
        p.duplicate(1);         // 1 2 2 3 0
        p.insertBlank(0);       // B 1 2 2 3 0
        p.remove(5);            // B 1 2 2 3
        assertEquals("-1:0,1:270,2:0,2:0,3:0", p.encode());
        assertFalse(p.isIdentity(4));
        assertTrue(p.get(0).isBlank());
        assertFalse(p.keepsOrder()); // duplicate 2,2 is not strictly increasing
    }

    @Test
    public void reorderBreaksOrder() {
        PagePlan p = PagePlan.of(Arrays.asList(2, 0, 1));
        assertFalse(p.keepsOrder());
        assertTrue(PagePlan.of(Arrays.asList(0, 2, 5)).keepsOrder());
    }

    @Test
    public void encodeDecodeRoundTrip() {
        PagePlan p = PagePlan.decode("3:90, 1:0,-1:0, 0");
        assertEquals(4, p.size());
        assertEquals(90, p.get(0).rotation);
        assertEquals(0, p.get(3).source);
        assertEquals("3:90,1:0,-1:0,0:0", p.encode());
        assertEquals(0, PagePlan.decode("  ").size());
    }

    @Test
    public void normalizesRotation() {
        assertEquals(270, PagePlan.normalize(-90));
        assertEquals(0, PagePlan.normalize(720));
        assertEquals(90, PagePlan.normalize(100));
    }

    @Test
    public void insertBlankIsClamped() {
        PagePlan p = PagePlan.identity(1);
        p.insertBlank(99);
        p.insertBlank(-5);
        assertEquals("-1:0,0:0,-1:0", p.encode());
    }
}
