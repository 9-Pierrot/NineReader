package com.ninereader.ui.reader;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ninereader.reader.TextSearch;

import java.util.Collections;
import java.util.List;

/** Immutable snapshot of a full-text search (LEC-07). */
public final class SearchState {

    @NonNull
    public final String query;
    @NonNull
    public final List<TextSearch.Match> results;
    /** Index of the current result, -1 when none. */
    public final int current;
    public final boolean finished;
    public final int pagesSearched;

    public SearchState(@NonNull String query, @NonNull List<TextSearch.Match> results, int current, boolean finished,
                       int pagesSearched) {
        this.query = query;
        this.results = Collections.unmodifiableList(results);
        this.current = current;
        this.finished = finished;
        this.pagesSearched = pagesSearched;
    }

    @NonNull
    public static SearchState empty() {
        return new SearchState("", Collections.emptyList(), -1, true, 0);
    }

    @Nullable
    public TextSearch.Match currentMatch() {
        return current >= 0 && current < results.size() ? results.get(current) : null;
    }

    @NonNull
    public SearchState withCurrent(int index) {
        return new SearchState(query, results, index, finished, pagesSearched);
    }
}
