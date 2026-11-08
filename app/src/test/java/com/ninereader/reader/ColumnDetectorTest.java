package com.ninereader.reader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.graphics.RectF;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = android.app.Application.class)
public class ColumnDetectorTest {

    /** Two columns: [50..280] and [320..550] on a 600 wide page. */
    private static List<RectF> twoColumns() {
        List<RectF> lines = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            float y = 100 + i * 20;
            lines.add(new RectF(50, y, i % 5 == 4 ? 180 : 280, y + 12));
            lines.add(new RectF(320, y, 550, y + 12));
        }
        lines.add(new RectF(20, 40, 580, 60)); // full width title
        return lines;
    }

    @Test
    public void findsLeftAndRightColumns() {
        float[] left = ColumnDetector.columnAt(twoColumns(), 100, 600);
        assertNotNull(left);
        assertEquals(50, left[0], 0.1);
        assertEquals(280, left[1], 0.1);
        float[] right = ColumnDetector.columnAt(twoColumns(), 400, 600);
        assertNotNull(right);
        assertEquals(320, right[0], 0.1);
    }

    @Test
    public void singleColumnTextIsNotAColumn() {
        List<RectF> lines = new ArrayList<>();
        for (int i = 0; i < 10; i++) lines.add(new RectF(30, i * 20, 570, i * 20 + 12));
        assertNull(ColumnDetector.columnAt(lines, 300, 600));
    }

    @Test
    public void gutterHasNoColumn() {
        assertNull(ColumnDetector.columnAt(twoColumns(), 300, 600));
    }
}
