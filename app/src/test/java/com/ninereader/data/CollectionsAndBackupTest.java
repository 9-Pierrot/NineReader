package com.ninereader.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.room.Room;

import com.ninereader.data.backup.BackupData;
import com.ninereader.data.backup.BackupManager;
import com.ninereader.data.entity.DocumentEntity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = android.app.Application.class)
public class CollectionsAndBackupTest extends DbTestBase {

    @Test
    public void collections() {
        CollectionRepository repo = new CollectionRepository(db, direct);
        DocumentRepository docs = new DocumentRepository(db, direct);
        DocumentEntity d = docs.registerOpened("content://a", "a.pdf", 1, null, 1, 1);
        long cours = repo.create("Cours", 0);
        long contrats = repo.create(" Contrats ", 0);
        assertEquals(cours, repo.create("Cours", 0));
        repo.setCollections(d.id, Arrays.asList(cours, contrats));
        assertEquals(2, repo.collectionsOf(d.id).size());
        assertEquals(1, repo.countDocuments(cours));
        assertEquals(1, value(docs.byCollection(cours)).size());
        repo.setCollections(d.id, Collections.singletonList(contrats));
        assertEquals(Collections.singletonList(contrats), repo.collectionsOf(d.id));
        repo.rename(contrats, "Juridique");
        assertEquals("Juridique", repo.allSync().get(1).name.equals("Juridique") ? "Juridique" : repo.allSync().get(0).name);
        repo.delete(cours);
        assertEquals(1, value(repo.all()).size());
    }

    @Test
    public void backupRoundTrip() throws IOException {
        DocumentRepository docs = new DocumentRepository(db, direct);
        CollectionRepository cols = new CollectionRepository(db, direct);
        DocumentEntity d = docs.registerOpened("content://a", "a.pdf", 10, "hash-a", 20, 5000);
        docs.savePosition(d.id, 12, 1.5f, "paged");
        docs.setFavorite(d.id, true);
        docs.toggleBookmark(d.id, 3, "Intro", null);
        docs.recordSession(d.id, 0, 60_000, 4);
        long c = cols.create("Cours", 7);
        cols.setCollections(d.id, Collections.singletonList(c));

        BackupManager manager = new BackupManager(db);
        StringWriter w = new StringWriter();
        manager.export(w, 123);
        String json = w.toString();
        assertTrue(json.contains("\"hash-a\""));

        AppDatabase other = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries().setQueryExecutor(direct).setTransactionExecutor(direct).build();
        try {
            BackupManager importer = new BackupManager(other);
            BackupData data = importer.parse(new StringReader(json));
            assertEquals("1/1/1", BackupManager.summary(data));
            assertEquals(1, importer.importData(data));
            // Importing twice does not duplicate anything.
            assertEquals(1, importer.importData(data));
            DocumentEntity imported = other.documents().byUri("content://a");
            assertEquals(12, imported.lastPage);
            assertTrue(imported.favorite);
            assertEquals(1, other.bookmarks().forDocument(imported.id).size());
            assertEquals(1, other.sessions().forDocument(imported.id).size());
            assertEquals(1, other.collections().collectionIdsForDocument(imported.id).size());
        } finally {
            other.close();
        }
    }

    @Test(expected = IOException.class)
    public void invalidBackupIsRejected() throws IOException {
        new BackupManager(db).parse(new StringReader("{\"version\": 99, \"documents\": []}"));
    }

    @Test(expected = IOException.class)
    public void garbageBackupIsRejected() throws IOException {
        new BackupManager(db).parse(new StringReader("not json at all {"));
    }
}
