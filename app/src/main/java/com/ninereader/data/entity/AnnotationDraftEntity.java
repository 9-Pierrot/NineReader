package com.ninereader.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Unsaved annotations of a document, serialized as JSON. Kept until they are written into the PDF so
 * that nothing is lost on crash (§5.4).
 */
@Entity(tableName = "annotation_drafts")
public class AnnotationDraftEntity {

    @PrimaryKey
    @NonNull
    public String uri = "";

    @NonNull
    public String json = "[]";

    @ColumnInfo(name = "updated_at")
    public long updatedAt;
}
