package com.ninereader.data.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

/** Many-to-many link between documents and collections. */
@Entity(tableName = "document_collections",
        primaryKeys = {"document_id", "collection_id"},
        foreignKeys = {
                @ForeignKey(entity = DocumentEntity.class, parentColumns = "id",
                        childColumns = "document_id", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = CollectionEntity.class, parentColumns = "id",
                        childColumns = "collection_id", onDelete = ForeignKey.CASCADE)
        },
        indices = {@Index("collection_id")})
public class DocumentCollectionCrossRef {

    @ColumnInfo(name = "document_id")
    public long documentId;

    @ColumnInfo(name = "collection_id")
    public long collectionId;

    public DocumentCollectionCrossRef() {
    }

    public DocumentCollectionCrossRef(long documentId, long collectionId) {
        this.documentId = documentId;
        this.collectionId = collectionId;
    }
}
