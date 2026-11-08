package com.ninereader.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.os.Looper;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.ninereader.NineReaderApp;
import com.ninereader.data.AppDatabase;
import com.ninereader.data.entity.CollectionEntity;
import com.ninereader.data.entity.DocumentEntity;
import com.ninereader.data.entity.FolderEntity;
import com.ninereader.ui.files.FileRow;
import com.ninereader.ui.files.FileSorter;
import com.ninereader.ui.files.FilesViewModel;
import com.ninereader.ui.home.HomeViewModel;
import com.ninereader.util.AppExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.List;

/** Home and Files view models on an in-memory database. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ViewModelsTest {

    @Rule
    public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();

    private NineReaderApp app;
    private AppDatabase db;

    @Before
    public void setUp() throws Exception {
        app = ApplicationProvider.getApplicationContext();
        // Let the start-up work of the application (trash purge) finish before swapping the database.
        AppExecutors.get().diskIo().submit(() -> { }).get();
        AppExecutors.setDirectForTests();
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase.class)
                .allowMainThreadQueries()
                .setQueryExecutor(Runnable::run)
                .setTransactionExecutor(Runnable::run)
                .build();
        AppDatabase.setInstanceForTests(db);
    }

    @After
    public void tearDown() {
        db.close();
        AppDatabase.setInstanceForTests(null);
        AppExecutors.resetForTests();
    }

    private static <T> T value(LiveData<T> live) {
        Object[] holder = new Object[1];
        Observer<T> obs = v -> holder[0] = v;
        live.observeForever(obs);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        live.removeObserver(obs);
        @SuppressWarnings("unchecked")
        T t = (T) holder[0];
        return t;
    }

    @Test
    public void homeShowsRecentsFavoritesAndStats() {
        DocumentEntity a = app.documents().registerOpened("content://a", "a.pdf", 1, null, 10, 1000);
        app.documents().registerOpened("content://b", "b.pdf", 1, null, 10, System.currentTimeMillis());
        app.documents().recordSession(a.id, System.currentTimeMillis() - 120_000, System.currentTimeMillis(), 5);
        HomeViewModel vm = new HomeViewModel(app);
        assertEquals(2, value(vm.recents()).size());
        assertEquals("b.pdf", value(vm.lastOpened()).name);
        assertTrue(value(vm.todayTime()) >= 120_000);
        assertEquals(Integer.valueOf(5), value(vm.todayPages()));
        assertTrue(value(vm.weekTime()) >= 120_000);
        vm.toggleFavorite(a);
        assertEquals(1, value(vm.favorites()).size());
    }

    @Test
    public void filesSectionsSearchAndSort() {
        app.documents().ensure("content://z", "zeta.pdf", 300, null);
        app.documents().ensure("content://a", "alpha.pdf", 100, null);
        FilesViewModel vm = new FilesViewModel(app);
        List<FileRow> rows = value(vm.rows());
        assertEquals(2, rows.size());
        assertEquals("alpha.pdf", rows.get(0).title);

        vm.setSort(FileSorter.SORT_SIZE);
        assertEquals("zeta.pdf", value(vm.rows()).get(0).title);
        assertEquals(FileSorter.SORT_SIZE, vm.sortMode());

        vm.setQuery("alp");
        assertEquals(1, value(vm.rows()).size());
        vm.setQuery("");

        // Collections
        long id = app.collections().create("Cours", 0);
        DocumentEntity alpha = db.documents().byUri("content://a");
        db.collections().link(new com.ninereader.data.entity.DocumentCollectionCrossRef(alpha.id, id));
        vm.setSection(FilesViewModel.Section.COLLECTIONS);
        List<FileRow> cols = value(vm.rows());
        assertEquals(1, cols.size());
        assertEquals(FileRow.Kind.COLLECTION, cols.get(0).kind);
        assertFalse(vm.canGoUp());
        CollectionEntity c = cols.get(0).collection;
        assertNotNull(c);
        vm.setCollection(c);
        assertTrue(vm.canGoUp());
        assertEquals("Cours", vm.path());
        assertEquals(Collections.singletonList("alpha.pdf"), titles(value(vm.rows())));
        vm.goUp();
        assertFalse(vm.canGoUp());

        // Folders and trash
        FolderEntity f = new FolderEntity();
        f.uri = "content://tree/primary";
        f.name = "Documents";
        db.folders().insert(f);
        vm.setSection(FilesViewModel.Section.FOLDERS);
        List<FileRow> folders = value(vm.rows());
        assertEquals(FileRow.Kind.FOLDER_ROOT, folders.get(0).kind);
        vm.setSection(FilesViewModel.Section.TRASH);
        assertTrue(value(vm.rows()).isEmpty());
        assertEquals(FilesViewModel.Section.TRASH, value(vm.section()));
        assertFalse(Boolean.TRUE.equals(value(vm.loading())));
    }

    private static List<String> titles(List<FileRow> rows) {
        List<String> out = new java.util.ArrayList<>();
        for (FileRow r : rows) out.add(r.title);
        return out;
    }
}
