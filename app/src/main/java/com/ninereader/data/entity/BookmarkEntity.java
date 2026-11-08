package com.ninereader.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** A user bookmark on a document page (LEC-10). */
@Entity(tableName = "bookmarks",
        foreignKeys = @ForeignKey(entity = DocumentEntity.class, parentColumns = "id",
                childColumns = "document_id", onDelete = ForeignKey.CASCADE),
        indices = {@Index(value = {"document_id", "page"}, unique = true)})
public class BookmarkEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "document_id")
    public long documentId;

    public int page;

    @NonNull
    public String label = "";

    @ColumnInfo(name = "created_at")
    public long createdAt;
}
