package com.ninereader.data.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** A deleted document kept in the internal trash for 30 days (BIB-04). */
@Entity(tableName = "trash")
public class TrashItemEntity {

    public static final long RETENTION_MS = 30L * 24 * 60 * 60 * 1000;

    @PrimaryKey(autoGenerate = true)
    public long id;

    @Nullable
    @ColumnInfo(name = "document_id")
    public Long documentId;

    @NonNull
    public String name = "";

    /** Copy of the file in internal storage. */
    @NonNull
    @ColumnInfo(name = "temp_path")
    public String tempPath = "";

    @Nullable
    @ColumnInfo(name = "original_uri")
    public String originalUri;

    /** SAF tree folder the file was deleted from (used to restore it in place). */
    @Nullable
    @ColumnInfo(name = "original_parent_uri")
    public String originalParentUri;

    @ColumnInfo(name = "deleted_at")
    public long deletedAt;

    public long size;

    public boolean isExpired(long now) {
        return now - deletedAt > RETENTION_MS;
    }

    public int daysLeft(long now) {
        long left = RETENTION_MS - (now - deletedAt);
        return (int) Math.max(0, Math.ceil(left / (24.0 * 60 * 60 * 1000)));
    }
}
