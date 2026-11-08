package com.ninereader.ui.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.net.Uri;
import android.os.Looper;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.test.core.app.ApplicationProvider;

import com.ninereader.pdfops.PagePlan;
import com.ninereader.util.AppExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class OrganizeViewModelTest {

    @Rule
    public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();

    private OrganizeViewModel vm;

    @Before
    public void setUp() {
        AppExecutors.setDirectForTests();
        vm = new OrganizeViewModel(ApplicationProvider.getApplicationContext());
        vm.plan = PagePlan.identity(4);
        for (long i = 0; i < 4; i++) vm.ids.add(i);
    }

    @After
    public void tearDown() {
        AppExecutors.resetForTests();
    }

    @Test
    public void selectionAndBulkActions() {
        vm.toggle(1);
        vm.toggle(3);
        vm.rotateSelected(90);
        assertEquals(90, vm.plan.get(1).rotation);
        assertEquals(90, vm.plan.get(3).rotation);
        vm.duplicateSelected();           // 0 1 1 2 3 3
        assertEquals(6, vm.plan.size());
        assertEquals(6, vm.ids.size());
        assertTrue(vm.selection.isEmpty());
        vm.toggle(0);
        vm.insertBlankAfterSelection();   // 0 B 1 1 2 3 3
        assertTrue(vm.plan.get(1).isBlank());
        vm.toggle(1);
        vm.toggle(2);
        vm.deleteSelected();              // 0 1 2 3 3
        assertEquals("0:0,1:90,2:0,3:90,3:90", vm.plan.encode());
        assertEquals(5, vm.ids.size());
        vm.selectAll();
        assertEquals(5, vm.selection.size());
        vm.selectAll();
        assertTrue(vm.selection.isEmpty());
        assertTrue(vm.version.getValue() > 0);
    }

    @Test
    public void moveKeepsSelectionOnSamePages() {
        vm.toggle(0);
        vm.toggle(2);
        vm.move(0, 3);                    // 1 2 3 0
        assertEquals("1:0,2:0,3:0,0:0", vm.plan.encode());
        assertEquals(Arrays.asList(3, 1), new java.util.ArrayList<>(vm.selection));
        assertEquals(Long.valueOf(0), vm.ids.get(3));
        vm.move(3, 0);
        assertEquals("0:0,1:0,2:0,3:0", vm.plan.encode());
        assertTrue(vm.selection.contains(0) && vm.selection.contains(2));
    }

    @Test
    public void openingAnInvalidFileReportsAnError() {
        vm.open(Uri.parse("file:///nonexistent/doc.pdf"), null);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(OrganizeViewModel.Status.ERROR, vm.status.getValue());
        vm.reset(); // no document: no-op
    }
}
