package com.ninereader.reader;

import androidx.annotation.NonNull;

/** One entry of the document outline (internal bookmarks), flattened with its depth. */
public final class OutlineItem {

    @NonNull
    public final String title;
    public final int page;
    public final int level;
    public final boolean hasChildren;

    public OutlineItem(@NonNull String title, int page, int level, boolean hasChildren) {
        this.title = title;
        this.page = page;
        this.level = level;
        this.hasChildren = hasChildren;
    }
}
