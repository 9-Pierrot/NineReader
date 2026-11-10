package com.ninereader.pdfops;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;


public class OcrOpTest {

    @Test
    public void detectsExistingTextLayer() {
        assertFalse(OcrOp.hasTextLayer(""));
        assertFalse(OcrOp.hasTextLayer(" 12 - "));
        assertTrue(OcrOp.hasTextLayer("NINEREADER Lecture rapide"));
    }
}
