package com.ninereader.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** A named group of documents ("Cours", "Datasheets", "Contrats"...) used as collection or tag (BIB-03). */
@Entity(tableName = "collections", indices = {@Index(value = "name", unique = true)})
public class CollectionEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String name = "";

    public int color;

    @ColumnInfo(name = "created_at")
    public long createdAt;
}
