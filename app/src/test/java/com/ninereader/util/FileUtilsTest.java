package com.ninereader.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;

public class FileUtilsTest {

    @Test
    public void formatsSizesPerLocale() {
        assertEquals("512 o", FileUtils.formatSize(512, Locale.FRENCH));
        assertEquals("1,5 Ko", FileUtils.formatSize(1536, Locale.FRENCH));
        assertEquals("1.5 KB", FileUtils.formatSize(1536, Locale.ENGLISH));
        assertEquals("2 MB", FileUtils.formatSize(2L * 1024 * 1024, Locale.ENGLISH));
        assertEquals("?", FileUtils.formatSize(-1, Locale.ENGLISH));
    }

    @Test
    public void baseNameAndSanitize() {
        assertEquals("rapport", FileUtils.baseName("rapport.pdf"));
        assertEquals(".hidden", FileUtils.baseName(".hidden"));
        assertEquals("a_b_c", FileUtils.sanitizeFileName("a/b:c"));
        assertEquals("document", FileUtils.sanitizeFileName("   "));
    }

    @Test
    public void copiesStreams() throws IOException {
        byte[] data = new byte[200_000];
        for (int i = 0; i < data.length; i++) data[i] = (byte) i;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long n = FileUtils.copy(new ByteArrayInputStream(data), out);
        assertEquals(data.length, n);
        assertEquals(data.length, out.size());
    }

    @Test
    public void hex() {
        assertEquals("00ff10", FileUtils.toHex(new byte[]{0, (byte) 0xFF, 0x10}));
    }
}
