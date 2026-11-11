package com.ninereader.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.pressImeActionButton;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.ninereader.R;
import com.ninereader.TestPdfs;
import com.ninereader.annotations.Annotation;
import com.ninereader.ui.reader.DocumentEditor;
import com.ninereader.ui.reader.ReaderActivity;
import com.ninereader.ui.reader.ReaderViewModel;
import com.ninereader.ui.reader.SearchState;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** End-to-end path of §10 on a device: open → read → search → navigate → annotate → save; main navigation. */
@RunWith(AndroidJUnit4.class)
public class ReaderFlowTest {

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

    @Test
    public void openReadSearchAnnotateAndSave() throws IOException {
        Uri uri = TestPdfs.text(context, 12, "flow.pdf");
        TestPdfs.resetState(context, uri);
        Intent intent = new Intent(context, ReaderActivity.class).setData(uri);
        try (ActivityScenario<ReaderActivity> scenario = ActivityScenario.launch(intent)) {
            AtomicReference<ReaderViewModel> vmRef = new AtomicReference<>();
            scenario.onActivity(a -> vmRef.set(new ViewModelProvider(a).get(ReaderViewModel.class)));
            ReaderViewModel vm = vmRef.get();
            waitUntil("document ready", 10_000, () -> onMain(() -> vm.status().getValue() == ReaderViewModel.Status.READY));
            onView(withId(R.id.page_label)).check(matches(withText("1 / 12")));

            // Full-text search through the UI (LEC-07)
            onView(withId(R.id.action_search)).perform(click());
            onView(withId(R.id.search_input)).perform(replaceText(TestPdfs.WORD), pressImeActionButton());
            waitUntil("search finished", 10_000, () -> onMain(() -> {
                SearchState s = vm.search().getValue();
                return s != null && s.finished && !s.results.isEmpty();
            }));
            assertEquals(12, onMain(() -> vm.search().getValue().results.size()).intValue());
            onView(withId(R.id.search_next)).perform(click());
            waitUntil("moved to next result", 5_000, () -> onMain(vm::page) >= 1);
            onView(withId(R.id.search_close)).perform(click());

            // Outline navigation (LEC-06)
            onView(withId(R.id.btn_panel)).perform(click());
            onView(withText("Chapitre 5")).perform(click());
            waitUntil("page 5", 5_000, () -> onMain(vm::page) == 4);
            scenario.onActivity(a -> {
                TextView label = a.findViewById(R.id.page_label);
                assertEquals("5 / 12", label.getText().toString());
            });
            onView(withId(R.id.pages)).check(matches(isDisplayed()));

            // Annotate (ANN-01) and save a copy (ANN-07)
            Annotation h = new Annotation();
            h.type = Annotation.Type.HIGHLIGHT;
            h.page = 4;
            h.rects.add(new float[]{72, 560, 300, 575});
            h.text = TestPdfs.WORD;
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> vm.editor().store().add(h));
            assertTrue(onMain(() -> vm.editor().hasPendingChanges()));
            File copy = new File(context.getCacheDir(), "flow_copy.pdf");
            AtomicReference<DocumentEditor.SaveResult> result = new AtomicReference<>();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    vm.editor().save(uri, null, Uri.fromFile(copy), () -> { }, result::set));
            waitUntil("save finished", 15_000, () -> result.get() != null);
            assertTrue("save failed: " + result.get().error, result.get().success);
            assertTrue(onMain(() -> !vm.editor().hasPendingChanges()));
            try (PDDocument doc = PDDocument.load(copy)) {
                List<PDAnnotation> annots = doc.getPage(4).getAnnotations();
                assertEquals("Highlight", annots.get(annots.size() - 1).getSubtype());
            }

            // Changing the reading mode (LEC-03) keeps the current page
            onView(withId(R.id.btn_mode)).perform(click());
            SystemClock.sleep(500);
            waitUntil("relaid out on page 5", 5_000, () -> onMain(vm::page) == 4);
        }
    }

    private static void askSave(ReaderActivity a, boolean thenFinish) {
        try {
            java.lang.reflect.Method m = ReaderActivity.class.getDeclaredMethod("askSave", boolean.class);
            m.setAccessible(true);
            m.invoke(a, thenFinish);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void discard(ReaderActivity a, boolean thenFinish) {
        try {
            java.lang.reflect.Method m = ReaderActivity.class.getDeclaredMethod("discardChanges", boolean.class);
            m.setAccessible(true);
            m.invoke(a, thenFinish);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** "No" in the save dialog drops the pending changes and their draft (undo from the snackbar in the reader). */
    @Test
    public void saveDialogNoDiscardsTheChanges() throws IOException {
        Uri uri = TestPdfs.text(context, 3, "discard.pdf");
        TestPdfs.resetState(context, uri);
        Intent intent = new Intent(context, ReaderActivity.class).setData(uri);
        try (ActivityScenario<ReaderActivity> scenario = ActivityScenario.launch(intent)) {
            AtomicReference<ReaderViewModel> vmRef = new AtomicReference<>();
            scenario.onActivity(a -> vmRef.set(new ViewModelProvider(a).get(ReaderViewModel.class)));
            ReaderViewModel vm = vmRef.get();
            waitUntil("document ready", 10_000, () -> onMain(() -> vm.status().getValue() == ReaderViewModel.Status.READY));
            Annotation h = new Annotation();
            h.type = Annotation.Type.HIGHLIGHT;
            h.page = 0;
            h.rects.add(new float[]{72, 560, 300, 575});
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> vm.editor().store().add(h));
            waitUntil("draft written", 5_000, () -> com.ninereader.data.AppDatabase.get(context).drafts().get(uri.toString()) != null);

            // The dialog offers Yes (replace, copy) and No; "Later" only when leaving.
            scenario.onActivity(a -> askSave(a, false));
            onView(withText(R.string.save_in_file)).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText(R.string.save_in_copy)).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText(R.string.discard_changes)).inRoot(isDialog()).check(matches(isDisplayed()));
            pressBack();
            scenario.onActivity(a -> askSave(a, true));
            onView(withText(R.string.keep_draft)).inRoot(isDialog()).check(matches(isDisplayed()));
            pressBack();

            // In the reader: "No" discards, the snackbar brings the changes back.
            scenario.onActivity(a -> discard(a, false));
            waitUntil("changes discarded", 5_000, () -> onMain(() -> !vm.editor().hasPendingChanges()));
            // Clicked directly: the device keeps its animations, which make Espresso clicks on a
            // snackbar that is still sliding in unreliable.
            AtomicReference<ReaderActivity> act = new AtomicReference<>();
            scenario.onActivity(act::set);
            waitUntil("undo offered", 5_000, () -> onMain(() -> {
                android.view.View v = act.get().findViewById(com.google.android.material.R.id.snackbar_action);
                return v != null && v.isShown() && context.getString(R.string.undo).equalsIgnoreCase(((TextView) v).getText().toString());
            }));
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    act.get().findViewById(com.google.android.material.R.id.snackbar_action).performClick());
            waitUntil("changes restored", 5_000, () -> onMain(() -> vm.editor().hasPendingChanges()));

            // When leaving: "No" discards, deletes the draft and closes the document.
            scenario.onActivity(a -> discard(a, true));
            waitUntil("draft deleted", 5_000, () -> com.ninereader.data.AppDatabase.get(context).drafts().get(uri.toString()) == null);
            waitUntil("reader closed", 5_000, () -> scenario.getState() == androidx.lifecycle.Lifecycle.State.DESTROYED);
        }
    }

    @Test
    public void mainNavigationTabs() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            onView(withText(R.string.recent_files)).check(matches(isDisplayed()));
            onView(withId(R.id.filesFragment)).perform(click());
            onView(withId(R.id.section_library)).check(matches(isDisplayed()));
            onView(withId(R.id.section_trash)).perform(scrollTo(), click());
            onView(withText(R.string.trash_retention)).check(matches(isDisplayed()));
            onView(withId(R.id.createFragment)).perform(click());
            onView(withText(R.string.tool_images_to_pdf)).check(matches(isDisplayed()));
            onView(withId(R.id.toolsFragment)).perform(click());
            // Category filter (Tous / Lire / ... / Organiser / Protéger)
            onView(withText(R.string.cat_organize)).perform(scrollTo(), click());
            onView(withText(R.string.tool_compress)).check(matches(isDisplayed()));
            onView(withText(R.string.tool_compress)).perform(click());
            onView(withId(R.id.run)).check(matches(isDisplayed()));
            pressBack();
            onView(withId(R.id.homeFragment)).perform(click());
            onView(withText(R.string.recent_files)).check(matches(isDisplayed()));
        }
    }
}
