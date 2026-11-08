package com.ninereader.reader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class RsvpEngineTest {

    @Test
    public void splitsWords() {
        assertEquals(Arrays.asList("Un", "information,", "deux."), RsvpEngine.words(" Un  infor-\nmation,\ndeux. "));
    }

    @Test
    public void clampsSpeed() {
        assertEquals(RsvpEngine.MIN_WPM, RsvpEngine.clampWpm(10));
        assertEquals(RsvpEngine.MAX_WPM, RsvpEngine.clampWpm(5000));
        assertEquals(350, RsvpEngine.clampWpm(350));
    }

    @Test
    public void punctuationAndLongWordsLastLonger() {
        long base = RsvpEngine.durationMs("mot", 300);
        assertEquals(200, base);
        assertTrue(RsvpEngine.durationMs("fin.", 300) > base);
        assertTrue(RsvpEngine.durationMs("pause,", 300) > base);
        assertTrue(RsvpEngine.durationMs("anticonstitutionnellement", 300) > base);
    }

    @Test
    public void pivotPositions() {
        assertEquals(0, RsvpEngine.pivot("a"));
        assertEquals(1, RsvpEngine.pivot("chat"));
        assertEquals(2, RsvpEngine.pivot("lecture"));
        assertEquals(3, RsvpEngine.pivot("«lecture"));
        assertEquals(4, RsvpEngine.pivot("extraordinairement"));
    }
}
