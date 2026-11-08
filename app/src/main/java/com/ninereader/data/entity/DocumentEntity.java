package com.ninereader.data.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** A PDF document known by the library (opened at least once). */
@Entity(tableName = "documents", indices = {@Index(value = "uri", unique = true), @Index("hash")})
public class DocumentEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String uri = "";

    @NonNull
    public String name = "";

    public long size;

    @ColumnInfo(name = "page_count")
    public int pageCount;

    @Nullable
    public String hash;

    @ColumnInfo(name = "last_page")
    public int lastPage;

    @ColumnInfo(name = "last_zoom")
    public float lastZoom = 1f;

    /** "continuous", "paged" or "double". Null means "use default". */
    @Nullable
    @ColumnInfo(name = "reading_mode")
    public String readingMode;

    @ColumnInfo(name = "last_opened")
    public long lastOpened;

    public boolean favorite;

    @ColumnInfo(name = "added_at")
    public long addedAt;

    /** SAF tree the document was found in (enables rename/move/delete), if any. */
    @Nullable
    @ColumnInfo(name = "parent_uri")
    public String parentUri;

    /** True while the document only exists in the internal trash. */
    @ColumnInfo(name = "in_trash")
    public boolean inTrash;

    public DocumentEntity() {
    }

    /** Reading progress in [0..1]. */
    public float progress() {
        if (pageCount <= 1) return pageCount == 1 ? 1f : 0f;
        return Math.min(1f, Math.max(0f, lastPage / (float) (pageCount - 1)));
    }
}
