package com.ninereader.ui.reader;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.graphics.RectF;
import android.net.Uri;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.action.GeneralClickAction;
import androidx.test.espresso.action.Press;
import androidx.test.espresso.action.Tap;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.ninereader.R;
import com.ninereader.TestPdfs;
import com.ninereader.annotations.Annotation;
import com.ninereader.edit.TextBlock;
import com.ninereader.reader.view.PageFrame;
import com.ninereader.reader.view.SpreadView;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Edit mode on a device (MOD-02): detect blocks, edit one inline, add a text box, save; OCR on a scan. */
@RunWith(AndroidJUnit4.class)
public class EditModeFlowTest {

    private final Context context = ApplicationProvider.getApplicationContext();

    private static void waitUntil(String what, long timeoutMs, BooleanSupplier condition) {
        long end = SystemClock.elapsedRealtime() + timeoutMs;
        while (SystemClock.elapsedRealtime() < end) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            if (condition.getAsBoolean()) return;
            SystemClock.sleep(100);
        }
        throw new AssertionError("Timeout waiting for " + what);
    }

    private static <T> T onMain(Supplier<T> s) {
        AtomicReference<T> ref = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> ref.set(s.get()));
        return ref.get();
    }

    /** Screen position of a point of a page (PDF user space), or null when the page is not laid out. */
    private static float[] screenPoint(ReaderActivity a, int page, float x, float y) {
        RecyclerView.ViewHolder vh = a.b.pages.findViewHolderForAdapterPosition(a.adapter.spreads().spreadOf(page));
        if (vh == null || !(vh.itemView instanceof SpreadView)) return null;
        SpreadView sv = (SpreadView) vh.itemView;
        PageFrame f = sv.frameForPage(page);
        RectF r = new RectF();
        if (f == null || !f.pageToView(new RectF(x, y, x, y), r)) return null;
        float[] p = new float[2];
        a.b.pages.spreadToScreen(sv, r.left, r.top, p);
        int[] loc = new int[2];
        a.b.pages.getLocationOnScreen(loc);
        return new float[]{loc[0] + p[0], loc[1] + p[1]};
    }

    /** Real tap on the page view at a page point (goes through the gesture detector and the edit mode). */
    private static void tapPage(ActivityScenario<ReaderActivity> scenario, int page, float x, float y) {
        AtomicReference<float[]> point = new AtomicReference<>();
        scenario.onActivity(a -> point.set(screenPoint(a, page, x, y)));
        assertNotNull("page " + page + " not laid out", point.get());
        onView(withId(R.id.pages)).perform(new GeneralClickAction(Tap.SINGLE, v -> point.get(), Press.FINGER,
                InputDevice.SOURCE_TOUCHSCREEN, MotionEvent.BUTTON_PRIMARY));
    }

    private static String pageText(File file, int page) throws IOException {
        try (PDDocument doc = PDDocument.load(file)) {
            PDFTextStripper s = new PDFTextStripper();
            s.setStartPage(page + 1);
            s.setEndPage(page + 1);
            return s.getText(doc);
        }
    }

    @Test
    public void editExistingTextAndAddTextThenSave() throws IOException {
        Uri uri = TestPdfs.text(context, 2, "edit_flow.pdf");
        TestPdfs.resetState(context, uri);
        Intent intent = new Intent(context, ReaderActivity.class).setData(uri).putExtra(ReaderActivity.EXTRA_ACTION, "edit");
        try (ActivityScenario<ReaderActivity> scenario = ActivityScenario.launch(intent)) {
            AtomicReference<ReaderActivity> act = new AtomicReference<>();
            scenario.onActivity(act::set);
            ReaderViewModel vm = onMain(() -> new ViewModelProvider(act.get()).get(ReaderViewModel.class));
            waitUntil("document ready", 10_000, () -> onMain(() -> vm.status().getValue() == ReaderViewModel.Status.READY));
            waitUntil("edit mode", 5_000, () -> onMain(() -> act.get().editUi != null && act.get().editUi.isActive()));
            onView(withId(R.id.edit_add_text)).check(matches(isDisplayed()));
            waitUntil("blocks of page 1", 15_000, () -> onMain(() -> {
                List<TextBlock> b = vm.textEdits().blocks(0);
                return b != null && !b.isEmpty();
            }));

            // The title is its own block, with its font.
            TextBlock title = onMain(() -> vm.textEdits().blocks(0).get(0));
            assertEquals("Chapitre 1", title.text);
            assertTrue(title.bold);

            // Tap it: the inline editor opens with the original text.
            tapPage(scenario, 0, (title.bounds[0] + title.bounds[2]) / 2, (title.bounds[1] + title.bounds[3]) / 2);
            waitUntil("inline editor", 5_000, () -> onMain(() -> act.get().b.inlineEditor.getVisibility() == View.VISIBLE));
            assertEquals("Chapitre 1", onMain(() -> act.get().b.inlineEditor.getText().toString()));
            onView(withId(R.id.inline_editor)).perform(replaceText("Introduction générale"));
            onView(withId(R.id.edit_commit)).perform(click());
            waitUntil("edit stored", 5_000, () -> onMain(() -> {
                for (Annotation a : vm.editor().store().all()) {
                    if (a.type == Annotation.Type.TEXT_EDIT && "Introduction générale".equals(a.text)) return true;
                }
                return false;
            }));

            // Add a new text box in the empty bottom of the page.
            onView(withId(R.id.edit_add_text)).perform(click());
            tapPage(scenario, 0, 72, 200);
            waitUntil("new box editor", 5_000, () -> onMain(() -> act.get().b.inlineEditor.getVisibility() == View.VISIBLE));
            onView(withId(R.id.inline_editor)).perform(replaceText("Note ajoutée"));
            onView(withId(R.id.edit_done)).perform(click());
            waitUntil("edit mode left", 5_000, () -> onMain(() -> !act.get().editUi.isActive()));

            StringBuilder debug = new StringBuilder();
            for (Annotation an : onMain(() -> vm.editor().store().all())) {
                debug.append(" | ").append(an.text).append(' ').append(java.util.Arrays.toString(an.bounds))
                        .append(" size=").append(an.fontSize).append(" lh=").append(an.lineHeight);
            }

            File copy = new File(context.getCacheDir(), "edit_flow_copy.pdf");
            AtomicReference<DocumentEditor.SaveResult> result = new AtomicReference<>();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    vm.editor().save(uri, null, Uri.fromFile(copy), () -> { }, result::set));
            waitUntil("save finished", 20_000, () -> result.get() != null);
            assertTrue("save failed: " + result.get().error, result.get().success);

            String text = pageText(copy, 0) + debug;
            assertFalse("original title must be removed: " + text, text.contains("Chapitre 1"));
            assertTrue(text, text.contains("Introduction générale"));
            assertTrue(text, text.contains("Note ajoutée"));
            assertTrue("rest of the page untouched", text.contains(TestPdfs.WORD));
            assertTrue("other pages untouched", pageText(copy, 1).contains("Chapitre 2"));
        }
    }

    @Test
    public void scannedPageIsRecognisedByOcr() throws IOException {
        Uri uri = TestPdfs.scanned(context);
        TestPdfs.resetState(context, uri);
        Intent intent = new Intent(context, ReaderActivity.class).setData(uri).putExtra(ReaderActivity.EXTRA_ACTION, "edit");
        try (ActivityScenario<ReaderActivity> scenario = ActivityScenario.launch(intent)) {
            AtomicReference<ReaderActivity> act = new AtomicReference<>();
            scenario.onActivity(act::set);
            ReaderViewModel vm = onMain(() -> new ViewModelProvider(act.get()).get(ReaderViewModel.class));
            waitUntil("document ready", 10_000, () -> onMain(() -> vm.status().getValue() == ReaderViewModel.Status.READY));
            waitUntil("OCR blocks", 30_000, () -> onMain(() -> {
                List<TextBlock> b = vm.textEdits().blocks(0);
                return b != null && !b.isEmpty();
            }));
            List<TextBlock> blocks = onMain(() -> vm.textEdits().blocks(0));
            boolean found = false;
            for (TextBlock b : blocks) {
                assertTrue(b.fromOcr);
                if (b.text.contains(TestPdfs.OCR_WORD)) found = true;
            }
            assertTrue("OCR text: " + blocks.get(0).text, found);
        }
    }
}
