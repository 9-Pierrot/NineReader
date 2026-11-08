package com.ninereader.ui.files;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ninereader.data.entity.CollectionEntity;
import com.ninereader.data.entity.DocumentEntity;
import com.ninereader.data.entity.TrashItemEntity;

/** A row of the file explorer: folder, PDF found in a folder, library document, collection or trashed file. */
public final class FileRow {

    /** Row kinds. */
    public enum Kind { FOLDER_ROOT, FOLDER, PDF, DOCUMENT, COLLECTION, TRASH }

    @NonNull
    public final Kind kind;
    @NonNull
    public final String title;
    /** Content URI (document) or tree URI (folder root). */
    @NonNull
    public final String uri;
    public long size = -1;
    public long date;
    /** SAF: tree URI and document id of the folder (folders) or of the parent folder (PDF). */
    @Nullable
    public String treeUri;
    @Nullable
    public String documentId;
    @Nullable
    public String parentUri;
    @Nullable
    public DocumentEntity doc;
    @Nullable
    public CollectionEntity collection;
    @Nullable
    public TrashItemEntity trash;

    public FileRow(@NonNull Kind kind, @NonNull String title, @NonNull String uri) {
        this.kind = kind;
        this.title = title;
        this.uri = uri;
    }

    public boolean isFolderLike() {
        return kind == Kind.FOLDER_ROOT || kind == Kind.FOLDER || kind == Kind.COLLECTION;
    }
}
