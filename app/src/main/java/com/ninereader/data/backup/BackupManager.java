package com.ninereader.data.backup;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.ninereader.data.AppDatabase;
import com.ninereader.data.entity.BookmarkEntity;
import com.ninereader.data.entity.CollectionEntity;
import com.ninereader.data.entity.DocumentCollectionCrossRef;
import com.ninereader.data.entity.DocumentEntity;
import com.ninereader.data.entity.ReadingSessionEntity;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Exports and imports bookmarks, collections and statistics as JSON (BIB-05). */
public class BackupManager {

    private final AppDatabase db;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public BackupManager(@NonNull AppDatabase db) {
        this.db = db;
    }

    /** Builds the backup model from the database. */
    @WorkerThread
    @NonNull
    public BackupData snapshot(long now) {
        BackupData data = new BackupData();
        data.createdAt = now;
        Map<Long, String> collectionNames = new HashMap<>();
        for (CollectionEntity c : db.collections().all()) {
            BackupData.Collection bc = new BackupData.Collection();
            bc.name = c.name;
            bc.color = c.color;
            data.collections.add(bc);
            collectionNames.put(c.id, c.name);
        }
        Map<Long, BackupData.Doc> docs = new HashMap<>();
        for (DocumentEntity d : db.documents().all()) {
            if (d.inTrash) continue;
            BackupData.Doc bd = new BackupData.Doc();
            bd.uri = d.uri;
            bd.name = d.name;
            bd.hash = d.hash;
            bd.size = d.size;
            bd.pageCount = d.pageCount;
            bd.lastPage = d.lastPage;
            bd.lastZoom = d.lastZoom;
            bd.readingMode = d.readingMode;
            bd.lastOpened = d.lastOpened;
            bd.favorite = d.favorite;
            docs.put(d.id, bd);
            data.documents.add(bd);
        }
        for (BookmarkEntity b : db.bookmarks().all()) {
            BackupData.Doc bd = docs.get(b.documentId);
            if (bd == null) continue;
            BackupData.Bookmark bb = new BackupData.Bookmark();
            bb.page = b.page;
            bb.label = b.label;
            bb.createdAt = b.createdAt;
            bd.bookmarks.add(bb);
        }
        for (ReadingSessionEntity s : db.sessions().all()) {
            BackupData.Doc bd = docs.get(s.documentId);
            if (bd == null) continue;
            BackupData.Session bs = new BackupData.Session();
            bs.start = s.start;
            bs.end = s.end;
            bs.pagesRead = s.pagesRead;
            bd.sessions.add(bs);
        }
        for (DocumentCollectionCrossRef ref : db.collections().allLinks()) {
            BackupData.Doc bd = docs.get(ref.documentId);
            String name = collectionNames.get(ref.collectionId);
            if (bd != null && name != null) bd.collections.add(name);
        }
        return data;
    }

    @WorkerThread
    public void export(@NonNull Writer writer, long now) throws IOException {
        gson.toJson(snapshot(now), writer);
        writer.flush();
    }

    @NonNull
    public String toJson(@NonNull BackupData data) {
        return gson.toJson(data);
    }

    @NonNull
    public BackupData parse(@NonNull Reader reader) throws IOException {
        try {
            BackupData data = gson.fromJson(reader, BackupData.class);
            if (data == null || data.documents == null) throw new IOException("Empty backup");
            if (data.version > BackupData.CURRENT_VERSION) throw new IOException("Unsupported backup version " + data.version);
            return data;
        } catch (JsonParseException e) {
            throw new IOException("Invalid backup file", e);
        }
    }

    /**
     * Merges a backup into the database. Documents are matched by URI, then by content hash; unknown
     * documents are added so that they reappear in the library. Existing data is never removed.
     *
     * @return number of documents imported or updated
     */
    @WorkerThread
    public int importData(@NonNull BackupData data) {
        final int[] count = {0};
        db.runInTransaction(() -> {
            Map<String, Long> collectionIds = new HashMap<>();
            if (data.collections != null) {
                for (BackupData.Collection c : data.collections) {
                    if (c.name == null || c.name.trim().isEmpty()) continue;
                    CollectionEntity existing = db.collections().byName(c.name.trim());
                    long id;
                    if (existing != null) {
                        id = existing.id;
                    } else {
                        CollectionEntity ce = new CollectionEntity();
                        ce.name = c.name.trim();
                        ce.color = c.color;
                        ce.createdAt = System.currentTimeMillis();
                        id = db.collections().insert(ce);
                    }
                    collectionIds.put(c.name.trim(), id);
                }
            }
            for (BackupData.Doc bd : data.documents) {
                if (bd.uri == null) continue;
                DocumentEntity doc = db.documents().byUri(bd.uri);
                if (doc == null && bd.hash != null) doc = db.documents().byHash(bd.hash);
                if (doc == null) {
                    doc = new DocumentEntity();
                    doc.uri = bd.uri;
                    doc.name = bd.name != null ? bd.name : "document.pdf";
                    doc.hash = bd.hash;
                    doc.size = bd.size;
                    doc.pageCount = bd.pageCount;
                    doc.addedAt = System.currentTimeMillis();
                    doc.id = db.documents().insert(doc);
                    if (doc.id <= 0) continue;
                }
                if (bd.lastOpened >= doc.lastOpened) {
                    doc.lastPage = bd.lastPage;
                    doc.lastZoom = bd.lastZoom;
                    doc.readingMode = bd.readingMode;
                    doc.lastOpened = bd.lastOpened;
                }
                doc.favorite = doc.favorite || bd.favorite;
                db.documents().update(doc);
                if (bd.bookmarks != null) {
                    for (BackupData.Bookmark bb : bd.bookmarks) {
                        if (db.bookmarks().count(doc.id, bb.page) > 0) continue;
                        BookmarkEntity b = new BookmarkEntity();
                        b.documentId = doc.id;
                        b.page = bb.page;
                        b.label = bb.label != null ? bb.label : "";
                        b.createdAt = bb.createdAt;
                        db.bookmarks().insert(b);
                    }
                }
                if (bd.sessions != null) {
                    Set<Long> knownStarts = new HashSet<>();
                    for (ReadingSessionEntity s : db.sessions().forDocument(doc.id)) knownStarts.add(s.start);
                    for (BackupData.Session bs : bd.sessions) {
                        if (knownStarts.contains(bs.start)) continue;
                        ReadingSessionEntity s = new ReadingSessionEntity();
                        s.documentId = doc.id;
                        s.start = bs.start;
                        s.end = bs.end;
                        s.pagesRead = bs.pagesRead;
                        db.sessions().insert(s);
                    }
                }
                if (bd.collections != null) {
                    for (String name : bd.collections) {
                        Long cid = collectionIds.get(name);
                        if (cid != null) db.collections().link(new DocumentCollectionCrossRef(doc.id, cid));
                    }
                }
                count[0]++;
            }
        });
        return count[0];
    }

    /** Lists document names contained in a backup (for confirmation dialogs). */
    @NonNull
    public static String summary(@NonNull BackupData data) {
        int bookmarks = 0;
        for (BackupData.Doc d : data.documents) bookmarks += d.bookmarks == null ? 0 : d.bookmarks.size();
        List<BackupData.Collection> cols = data.collections;
        return data.documents.size() + "/" + bookmarks + "/" + (cols == null ? 0 : cols.size());
    }
}
