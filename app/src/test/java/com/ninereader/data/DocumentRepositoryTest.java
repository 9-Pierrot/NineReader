package com.ninereader.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ninereader.data.entity.BookmarkEntity;
import com.ninereader.data.entity.DocumentEntity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = android.app.Application.class)
public class DocumentRepositoryTest extends DbTestBase {

    private DocumentRepository repo;

    @Before
    public void setUpRepo() {
        repo = new DocumentRepository(db, direct);
    }

    @Test
    public void registerCreatesThenUpdates() {
        DocumentEntity d = repo.registerOpened("content://a", "a.pdf", 100, null, 10, 1000);
        assertTrue(d.id > 0);
        assertEquals(1000, d.lastOpened);
        DocumentEntity again = repo.registerOpened("content://a", "renamed.pdf", 120, "h1", 10, 2000);
        assertEquals(d.id, again.id);
        assertEquals("renamed.pdf", again.name);
        assertEquals("h1", again.hash);
        assertEquals(2000, repo.byId(d.id).lastOpened);
    }

    @Test
    public void sameContentThroughAnotherUriIsRecognised() {
        DocumentEntity d = repo.registerOpened("content://old", "a.pdf", 100, "hash", 10, 1000);
        repo.savePosition(d.id, 7, 2f, "paged");
        DocumentEntity moved = repo.registerOpened("content://new", "a.pdf", 100, "hash", 10, 2000);
        assertEquals(d.id, moved.id);
        assertEquals("content://new", moved.uri);
        assertEquals(7, moved.lastPage);
        assertEquals("paged", moved.readingMode);
    }

    @Test
    public void lastPageIsClampedWhenDocumentShrinks() {
        DocumentEntity d = repo.registerOpened("content://a", "a.pdf", 100, null, 10, 1000);
        repo.savePosition(d.id, 9, 1f, null);
        DocumentEntity reopened = repo.registerOpened("content://a", "a.pdf", 100, null, 5, 2000);
        assertEquals(4, reopened.lastPage);
    }

    @Test
    public void recentsFavoritesAndLastOpened() {
        DocumentEntity a = repo.registerOpened("content://a", "a.pdf", 1, null, 1, 1000);
        DocumentEntity b = repo.registerOpened("content://b", "b.pdf", 1, null, 1, 3000);
        repo.ensure("content://c", "c.pdf", 1, "content://tree");
        List<DocumentEntity> recents = value(repo.recents());
        assertEquals(2, recents.size());
        assertEquals(b.id, recents.get(0).id);
        assertEquals(b.id, repo.lastOpened().id);
        repo.setFavorite(a.id, true);
        assertEquals(1, value(repo.favorites()).size());
        repo.removeFromRecents(b.id);
        assertEquals(a.id, repo.lastOpened().id);
        assertEquals(3, value(repo.all()).size());
        repo.clearRecents();
        assertNull(repo.lastOpened());
    }

    @Test
    public void ensureKeepsParentFolder() {
        DocumentEntity c = repo.ensure("content://c", "c.pdf", 5, null);
        DocumentEntity c2 = repo.ensure("content://c", "c.pdf", 5, "content://tree/doc");
        assertEquals(c.id, c2.id);
        assertEquals("content://tree/doc", repo.byUri("content://c").parentUri);
    }

    @Test
    public void updateLocationReplacesUri() {
        DocumentEntity c = repo.ensure("content://c", "c.pdf", 5, null);
        repo.ensure("content://d", "d.pdf", 5, null);
        repo.updateLocation(c.id, "content://d", "d.pdf", "content://parent");
        DocumentEntity moved = repo.byId(c.id);
        assertEquals("content://d", moved.uri);
        assertEquals("content://parent", moved.parentUri);
        assertEquals(1, value(repo.all()).size());
    }

    @Test
    public void bookmarksToggle() {
        DocumentEntity d = repo.registerOpened("content://a", "a.pdf", 1, null, 10, 1);
        Boolean[] result = new Boolean[1];
        repo.toggleBookmark(d.id, 3, "Page 4", v -> result[0] = v);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertTrue(result[0]);
        List<BookmarkEntity> list = value(repo.bookmarks(d.id));
        assertEquals(1, list.size());
        repo.renameBookmark(list.get(0).id, "Chapitre");
        assertEquals("Chapitre", value(repo.bookmarks(d.id)).get(0).label);
        repo.toggleBookmark(d.id, 3, "x", v -> result[0] = v);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertFalse(result[0]);
        assertTrue(value(repo.bookmarks(d.id)).isEmpty());
    }

    @Test
    public void sessionsAndStats() {
        DocumentEntity d = repo.registerOpened("content://a", "a.pdf", 1, null, 100, 1);
        repo.recordSession(d.id, 0, 1000, 1); // too short, ignored
        repo.recordSession(d.id, 10_000, 610_000, 10);
        d.lastPage = 49;
        ReadingStats stats = repo.statsFor(d);
        assertEquals(600_000, stats.totalTimeMs);
        assertEquals(10, stats.pagesRead);
        assertEquals(50, stats.pagesRemaining);
        assertEquals(60_000 * 50, stats.estimatedRemainingMs());
        assertEquals(Long.valueOf(600_000), value(repo.readingTimeSince(0)));
        assertEquals(Integer.valueOf(10), value(repo.pagesReadSince(0)));
        assertNotNull(value(repo.observe(d.id)));
    }
}
