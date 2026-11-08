package com.ninereader.data;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import com.ninereader.data.entity.FolderEntity;
import com.ninereader.util.AppExecutors;

import java.util.List;

/** Folders (SAF trees) authorised by the user (BIB-02). */
public class FolderRepository {

    private final AppDatabase db;

    public FolderRepository(@NonNull AppDatabase db) {
        this.db = db;
    }

    public LiveData<List<FolderEntity>> all() {
        return db.folders().observeAll();
    }

    public void add(@NonNull String uri, @NonNull String name) {
        AppExecutors.get().diskIo().execute(() -> {
            FolderEntity f = new FolderEntity();
            f.uri = uri;
            f.name = name;
            f.addedAt = System.currentTimeMillis();
            db.folders().insert(f);
        });
    }

    public void remove(@NonNull String uri) {
        AppExecutors.get().diskIo().execute(() -> db.folders().delete(uri));
    }
}
