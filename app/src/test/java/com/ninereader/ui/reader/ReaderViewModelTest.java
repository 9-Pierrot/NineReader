package com.ninereader.ui.reader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.net.Uri;
import android.os.Looper;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.test.core.app.ApplicationProvider;

import com.ninereader.NineReaderApp;
import com.ninereader.reader.ReaderTheme;
import com.ninereader.reader.ReadingMode;
import com.ninereader.util.AppExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

/** Reader state that does not need the native renderer (no document loaded). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ReaderViewModelTest {

    @Rule
    public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();

    private ReaderViewModel vm;

    @Before
    public void setUp() throws Exception {
        NineReaderApp app = ApplicationProvider.getApplicationContext();
        AppExecutors.get().diskIo().submit(() -> { }).get();
        AppExecutors.setDirectForTests();
        vm = new ReaderViewModel(app);
    }

    @After
    public void tearDown() {
        AppExecutors.resetForTests();
    }

    @Test
    public void initialState() {
        assertEquals(ReaderViewModel.Status.IDLE, vm.status().getValue());
        assertEquals(0, vm.page());
        assertEquals(0, vm.pageCount());
        assertNull(vm.engine());
        assertNull(vm.document());
        assertEquals(1f, vm.takeInitialZoom(), 0f);
        assertFalse(vm.isBookmarked(0));
    }

    @Test
    public void readingModeDefaults() {
        assertEquals(ReadingMode.DOUBLE, vm.effectiveMode(true));
        assertEquals(ReadingMode.CONTINUOUS, vm.effectiveMode(false));
        vm.setExplicitMode(ReadingMode.PAGED);
        assertEquals(ReadingMode.PAGED, vm.effectiveMode(true));
        assertEquals(ReadingMode.PAGED, vm.explicitMode());
        vm.setTheme(ReaderTheme.SEPIA);
        assertEquals(ReaderTheme.SEPIA, vm.theme());
    }

    @Test
    public void pageTrackingAndSessions() {
        vm.setCurrentPage(4);
        assertEquals(4, vm.page());
        vm.resumeSession();
        vm.flushSession();   // no document: nothing recorded, no crash
        vm.savePosition(2f); // idem
        assertEquals(Integer.valueOf(4), vm.currentPage().getValue());
    }

    @Test
    public void searchAndSelectionWithoutDocument() {
        vm.search("mot");
        assertTrue(vm.search().getValue().query.isEmpty());
        vm.searchStep(1);
        vm.selectSearchResult(3);
        vm.clearSearch();
        assertTrue(vm.search().getValue().results.isEmpty());
        vm.clearSelection();
        assertNull(vm.selection().getValue());
        vm.setCropEnabled(false);
        assertFalse(vm.isCropEnabled());
        assertNull(vm.crop().getValue());
    }

    @Test
    public void openingAnUnreadableFileReportsAnError() {
        vm.open(Uri.parse("file:///nonexistent/doc.pdf"), null);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(ReaderViewModel.Status.ERROR, vm.status().getValue());
        vm.closeDocument();
    }
}
