package com.ninereader.data;

import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;

import com.ninereader.data.entity.DocumentEntity;
import com.ninereader.data.entity.TrashItemEntity;
import com.ninereader.util.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

/**
 * Internal trash (BIB-04): deleted files are copied to internal storage and kept for 30 days so they
 * can be restored.
 */
public class TrashRepository {

    private final Context context;
    private final AppDatabase db;

    public TrashRepository(@NonNull Context context, @NonNull AppDatabase db) {
        this.context = context.getApplicationContext();
        this.db = db;
    }

    public LiveData<List<TrashItemEntity>> all() {
        return db.trash().observeAll();
    }

    private File trashDir() throws IOException {
        File dir = new File(context.getFilesDir(), "trash");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        return dir;
    }

    /**
     * Moves a document to the trash: copies it internally, then deletes the original through SAF.
     *
     * @throws IOException when the copy or the deletion fails (nothing is lost in that case)
     */
    @WorkerThread
    public void moveToTrash(@NonNull DocumentEntity doc) throws IOException {
        Uri uri = Uri.parse(doc.uri);
        File copy = new File(trashDir(), System.currentTimeMillis() + "_" + FileUtils.sanitizeFileName(doc.name));
        FileUtils.copyUriToFile(context, uri, copy);
        boolean deleted;
        try {
            deleted = DocumentsContract.deleteDocument(context.getContentResolver(), uri);
        } catch (FileNotFoundException | SecurityException | IllegalArgumentException | UnsupportedOperationException e) {
            deleted = false;
        }
        if (!deleted) {
            FileUtils.deleteQuietly(copy);
            throw new IOException("Delete not supported for " + uri);
        }
        TrashItemEntity item = new TrashItemEntity();
        item.documentId = doc.id;
        item.name = doc.name;
        item.tempPath = copy.getAbsolutePath();
        item.originalUri = doc.uri;
        item.originalParentUri = doc.parentUri;
        item.deletedAt = System.currentTimeMillis();
        item.size = copy.length();
        db.runInTransaction(() -> {
            db.trash().insert(item);
            db.documents().setInTrash(doc.id, true);
        });
    }

    /**
     * Restores an item into its original folder when known.
     *
     * @return the new URI, or null when the original folder is unknown (the caller must then ask the
     * user for a destination and call {@link #restoreTo}).
     */
    @WorkerThread
    @Nullable
    public Uri restore(@NonNull TrashItemEntity item) throws IOException {
        if (item.originalParentUri == null) return null;
        Uri parent = Uri.parse(item.originalParentUri);
        Uri created;
        try {
            created = DocumentsContract.createDocument(context.getContentResolver(), parent, "application/pdf", item.name);
        } catch (FileNotFoundException | SecurityException | IllegalArgumentException e) {
            return null;
        }
        if (created == null) return null;
        restoreTo(item, created);
        return created;
    }

    /** Restores an item into a destination chosen by the user. */
    @WorkerThread
    public void restoreTo(@NonNull TrashItemEntity item, @NonNull Uri destination) throws IOException {
        File src = new File(item.tempPath);
        FileUtils.copyFileToUri(context, src, destination);
        db.runInTransaction(() -> {
            db.trash().delete(item.id);
            if (item.documentId != null) {
                DocumentEntity doc = db.documents().byId(item.documentId);
                if (doc != null) {
                    DocumentEntity clash = db.documents().byUri(destination.toString());
                    if (clash != null && clash.id != doc.id) db.documents().delete(clash.id);
                    doc.uri = destination.toString();
                    doc.inTrash = false;
                    db.documents().update(doc);
                }
            }
        });
        FileUtils.deleteQuietly(src);
    }

    /** Permanently deletes an item. */
    @WorkerThread
    public void deleteForever(@NonNull TrashItemEntity item) {
        FileUtils.deleteQuietly(new File(item.tempPath));
        db.runInTransaction(() -> {
            db.trash().delete(item.id);
            if (item.documentId != null) {
                DocumentEntity doc = db.documents().byId(item.documentId);
                if (doc != null && doc.inTrash) db.documents().delete(doc.id);
            }
        });
    }

    /** Deletes items older than the retention period. Returns the number of purged items. */
    @WorkerThread
    public int purgeExpired(long now) {
        List<TrashItemEntity> expired = db.trash().olderThan(now - TrashItemEntity.RETENTION_MS);
        for (TrashItemEntity item : expired) deleteForever(item);
        return expired.size();
    }
}
