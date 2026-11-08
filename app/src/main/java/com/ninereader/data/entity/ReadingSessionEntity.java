package com.ninereader.data.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** One reading session of a document, used for reading statistics (RAP-07). */
@Entity(tableName = "reading_sessions",
        foreignKeys = @ForeignKey(entity = DocumentEntity.class, parentColumns = "id",
                childColumns = "document_id", onDelete = ForeignKey.CASCADE),
        indices = {@Index("document_id")})
public class ReadingSessionEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "document_id")
    public long documentId;

    public long start;

    public long end;

    @ColumnInfo(name = "pages_read")
    public int pagesRead;

    public long durationMs() {
        return Math.max(0, end - start);
    }
}
