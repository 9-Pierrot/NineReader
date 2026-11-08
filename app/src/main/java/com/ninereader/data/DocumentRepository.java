package com.ninereader.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;

import com.ninereader.data.entity.BookmarkEntity;
import com.ninereader.data.entity.DocumentEntity;
import com.ninereader.data.entity.ReadingSessionEntity;
import com.ninereader.util.AppExecutors;

import java.util.List;
import java.util.concurrent.Executor;

/** Documents, reading positions, bookmarks and reading sessions. */
public class DocumentRepository {

    public static final int RECENTS_LIMIT = 30;

    private final AppDatabase db;
    private final Executor io;

    public DocumentRepository(@NonNull AppDatabase db) {
        this(db, AppExecutors.get().diskIo());
    }

    public DocumentRepository(@NonNull AppDatabase db, @NonNull Executor io) {
        this.db = db;
        this.io = io;
    }

    /**
     * Registers that a document was opened: finds it by URI (or by content hash when the same file is
     * reached through another URI), creates it otherwise, and updates its metadata.
     */
    @WorkerThread
    @NonNull
    public DocumentEntity registerOpened(@NonNull String uri, @NonNull String name, long size,
                                         @Nullable String hash, int pageCount, long now) {
        DocumentEntity doc = db.documents().byUri(uri);
        if (doc == null && hash != null) {
            DocumentEntity sameContent = db.documents().byHash(hash);
            if (sameContent != null) {
                doc = sameContent;
                doc.uri = uri;
            }
        }
        if (doc == null) {
            doc = new DocumentEntity();
            doc.uri = uri;
            doc.addedAt = now;
            doc.name = name;
            doc.size = size;
            doc.hash = hash;
            doc.pageCount = pageCount;
            doc.lastOpened = now;
            doc.id = db.documents().insert(doc);
            if (doc.id <= 0) {
                // Lost an insert race: read the existing row back.
                DocumentEntity existing = db.documents().byUri(uri);
                if (existing != null) doc = existing;
            }
            return doc;
        }
        doc.name = name;
        if (size >= 0) doc.size = size;
        if (hash != null) doc.hash = hash;
        doc.pageCount = pageCount;
        doc.lastOpened = now;
        doc.inTrash = false;
        if (doc.lastPage >= pageCount) doc.lastPage = Math.max(0, pageCount - 1);
        db.documents().update(doc);
        return doc;
    }

    /** Ensures a library entry exists for a file seen in the explorer (without marking it as opened). */
    @WorkerThread
    @NonNull
    public DocumentEntity ensure(@NonNull String uri, @NonNull String name, long size, @Nullable String parentUri) {
        DocumentEntity doc = db.documents().byUri(uri);
        if (doc != null) {
            if (parentUri != null && !parentUri.equals(doc.parentUri)) {
                doc.parentUri = parentUri;
                db.documents().update(doc);
            }
            return doc;
        }
        doc = new DocumentEntity();
        doc.uri = uri;
        doc.name = name;
        doc.size = size;
        doc.parentUri = parentUri;
        doc.addedAt = System.currentTimeMillis();
        doc.id = db.documents().insert(doc);
        if (doc.id <= 0) {
            DocumentEntity existing = db.documents().byUri(uri);
            if (existing != null) return existing;
        }
        return doc;
    }

    @WorkerThread
    @Nullable
    public DocumentEntity byId(long id) {
        return db.documents().byId(id);
    }

    @WorkerThread
    @Nullable
    public DocumentEntity byUri(@NonNull String uri) {
        return db.documents().byUri(uri);
    }

    @WorkerThread
    @Nullable
    public DocumentEntity lastOpened() {
        return db.documents().lastOpened();
    }

    @WorkerThread
    public void update(@NonNull DocumentEntity doc) {
        db.documents().update(doc);
    }

    public LiveData<DocumentEntity> observe(long id) {
        return db.documents().observeById(id);
    }

    public LiveData<List<DocumentEntity>> recents() {
        return db.documents().observeRecents(RECENTS_LIMIT);
    }

    public LiveData<List<DocumentEntity>> favorites() {
        return db.documents().observeFavorites();
    }

    public LiveData<DocumentEntity> lastOpenedLive() {
        return db.documents().observeLastOpened();
    }

    public LiveData<List<DocumentEntity>> all() {
        return db.documents().observeAll();
    }

    public LiveData<List<DocumentEntity>> byCollection(long collectionId) {
        return db.documents().observeByCollection(collectionId);
    }

    public void savePosition(long id, int page, float zoom, @Nullable String mode) {
        io.execute(() -> db.documents().updatePosition(id, page, zoom, mode));
    }

    public void setFavorite(long id, boolean favorite) {
        io.execute(() -> db.documents().setFavorite(id, favorite));
    }

    public void removeFromRecents(long id) {
        io.execute(() -> db.documents().removeFromRecents(id));
    }

    public void clearRecents() {
        io.execute(() -> db.documents().clearRecents());
    }

    /** Renames/relocates a document entry after a SAF rename or move. */
    public void updateLocation(long id, @NonNull String newUri, @NonNull String newName, @Nullable String parentUri) {
        io.execute(() -> {
            DocumentEntity doc = db.documents().byId(id);
            if (doc == null) return;
            DocumentEntity clash = db.documents().byUri(newUri);
            if (clash != null && clash.id != id) db.documents().delete(clash.id);
            doc.uri = newUri;
            doc.name = newName;
            if (parentUri != null) doc.parentUri = parentUri;
            db.documents().update(doc);
        });
    }

    // ---- Bookmarks (LEC-10) ----

    public LiveData<List<BookmarkEntity>> bookmarks(long documentId) {
        return db.bookmarks().observeForDocument(documentId);
    }

    /** Toggles the bookmark of a page; returns via callback whether the page is now bookmarked. */
    public void toggleBookmark(long documentId, int page, @NonNull String label, @Nullable Callback<Boolean> done) {
        io.execute(() -> {
            boolean exists = db.bookmarks().count(documentId, page) > 0;
            if (exists) {
                db.bookmarks().delete(documentId, page);
            } else {
                BookmarkEntity b = new BookmarkEntity();
                b.documentId = documentId;
                b.page = page;
                b.label = label;
                b.createdAt = System.currentTimeMillis();
                db.bookmarks().insert(b);
            }
            if (done != null) AppExecutors.get().mainThread().execute(() -> done.onResult(!exists));
        });
    }

    public void deleteBookmark(long id) {
        io.execute(() -> db.bookmarks().deleteById(id));
    }

    public void renameBookmark(long id, @NonNull String label) {
        io.execute(() -> db.bookmarks().rename(id, label));
    }

    // ---- Reading sessions (RAP-07) ----

    public void recordSession(long documentId, long start, long end, int pagesRead) {
        if (end - start < 2000) return; // ignore accidental opens
        io.execute(() -> {
            ReadingSessionEntity s = new ReadingSessionEntity();
            s.documentId = documentId;
            s.start = start;
            s.end = end;
            s.pagesRead = pagesRead;
            db.sessions().insert(s);
        });
    }

    public LiveData<Long> readingTimeSince(long since) {
        return db.sessions().observeTotalTimeSince(since);
    }

    public LiveData<Integer> pagesReadSince(long since) {
        return db.sessions().observePagesSince(since);
    }

    @WorkerThread
    @NonNull
    public ReadingStats statsFor(@NonNull DocumentEntity doc) {
        long time = db.sessions().totalTimeForDocument(doc.id);
        int pages = db.sessions().totalPagesForDocument(doc.id);
        int remaining = Math.max(0, doc.pageCount - doc.lastPage - 1);
        return new ReadingStats(time, pages, remaining);
    }

    /** Simple result callback delivered on the main thread. */
    public interface Callback<T> {
        void onResult(T value);
    }
}
