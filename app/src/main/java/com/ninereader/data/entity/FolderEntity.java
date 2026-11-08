package com.ninereader.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** A folder (SAF tree) the user granted access to, shown in the file explorer (BIB-02). */
@Entity(tableName = "folders")
public class FolderEntity {

    @PrimaryKey
    @NonNull
    public String uri = "";

    @NonNull
    public String name = "";

    @ColumnInfo(name = "added_at")
    public long addedAt;
}
