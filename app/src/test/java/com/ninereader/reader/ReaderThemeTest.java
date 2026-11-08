package com.ninereader.reader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReaderThemeTest {

    @Test
    public void resolvesKeys() {
        assertEquals(ReaderTheme.DARK, ReaderTheme.fromKey("auto", true));
        assertEquals(ReaderTheme.LIGHT, ReaderTheme.fromKey(null, false));
        assertEquals(ReaderTheme.SEPIA, ReaderTheme.fromKey("sepia", true));
        assertEquals(ReaderTheme.LIGHT, ReaderTheme.fromKey("unknown", true));
    }

    @Test
    public void lightIsIdentity() {
        assertNull(ReaderTheme.LIGHT.matrix());
        assertEquals(0xFF123456, ReaderTheme.LIGHT.apply(0xFF123456));
    }

    @Test
    public void invertedSwapsBlackAndWhite() {
        assertEquals(0xFF000000, ReaderTheme.INVERTED.apply(0xFFFFFFFF));
        assertEquals(0xFFFFFFFF, ReaderTheme.INVERTED.apply(0xFF000000));
    }

    @Test
    public void darkHasDarkBackground() {
        int bg = ReaderTheme.DARK.apply(0xFFFFFFFF);
        assertTrue(((bg >> 16) & 0xFF) < 40);
        assertTrue(ReaderTheme.DARK.isDark());
        int sepia = ReaderTheme.SEPIA.apply(0xFFFFFFFF);
        assertTrue(((sepia >> 16) & 0xFF) > ((sepia) & 0xFF)); // warm tone
    }
}
