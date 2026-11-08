package com.ninereader.data;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;

import com.ninereader.data.entity.CollectionEntity;
import com.ninereader.data.entity.DocumentCollectionCrossRef;
import com.ninereader.util.AppExecutors;

import java.util.List;
import java.util.concurrent.Executor;

/** Collections and tags (BIB-03). */
public class CollectionRepository {

    private final AppDatabase db;
    private final Executor io;

    public CollectionRepository(@NonNull AppDatabase db) {
        this(db, AppExecutors.get().diskIo());
    }

    public CollectionRepository(@NonNull AppDatabase db, @NonNull Executor io) {
        this.db = db;
        this.io = io;
    }

    public LiveData<List<CollectionEntity>> all() {
        return db.collections().observeAll();
    }

    @WorkerThread
    public List<CollectionEntity> allSync() {
        return db.collections().all();
    }

    /** Creates a collection (or returns the existing one with the same name). */
    @WorkerThread
    public long create(@NonNull String name, int color) {
        String n = name.trim();
        CollectionEntity existing = db.collections().byName(n);
        if (existing != null) return existing.id;
        CollectionEntity c = new CollectionEntity();
        c.name = n;
        c.color = color;
        c.createdAt = System.currentTimeMillis();
        return db.collections().insert(c);
    }

    public void createAsync(@NonNull String name, int color) {
        io.execute(() -> create(name, color));
    }

    public void rename(long id, @NonNull String name) {
        io.execute(() -> db.collections().rename(id, name.trim()));
    }

    public void delete(long id) {
        io.execute(() -> db.collections().delete(id));
    }

    @WorkerThread
    public List<Long> collectionsOf(long documentId) {
        return db.collections().collectionIdsForDocument(documentId);
    }

    /** Replaces the set of collections of a document. */
    public void setCollections(long documentId, @NonNull List<Long> collectionIds) {
        io.execute(() -> {
            db.runInTransaction(() -> {
                for (Long current : db.collections().collectionIdsForDocument(documentId)) {
                    if (!collectionIds.contains(current)) db.collections().unlink(documentId, current);
                }
                for (Long id : collectionIds) {
                    db.collections().link(new DocumentCollectionCrossRef(documentId, id));
                }
            });
        });
    }

    @WorkerThread
    public int countDocuments(long collectionId) {
        return db.collections().countDocuments(collectionId);
    }
}
