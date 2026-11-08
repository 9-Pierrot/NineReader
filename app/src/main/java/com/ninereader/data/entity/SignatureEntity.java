package com.ninereader.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** A saved handwritten signature or initials, stored as a transparent PNG in internal storage (SIG-03). */
@Entity(tableName = "signatures")
public class SignatureEntity {

    public static final String KIND_SIGNATURE = "signature";
    public static final String KIND_INITIALS = "initials";

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    @ColumnInfo(name = "image_path")
    public String imagePath = "";

    @NonNull
    public String kind = KIND_SIGNATURE;

    @ColumnInfo(name = "created_at")
    public long createdAt;
}
