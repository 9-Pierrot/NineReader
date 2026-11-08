package com.ninereader.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.ninereader.data.dao.AnnotationDraftDao;
import com.ninereader.data.dao.BookmarkDao;
import com.ninereader.data.dao.CollectionDao;
import com.ninereader.data.dao.DocumentDao;
import com.ninereader.data.dao.FolderDao;
import com.ninereader.data.dao.ReadingSessionDao;
import com.ninereader.data.dao.SignatureDao;
import com.ninereader.data.dao.TrashDao;
import com.ninereader.data.entity.AnnotationDraftEntity;
import com.ninereader.data.entity.BookmarkEntity;
import com.ninereader.data.entity.CollectionEntity;
import com.ninereader.data.entity.DocumentCollectionCrossRef;
import com.ninereader.data.entity.DocumentEntity;
import com.ninereader.data.entity.FolderEntity;
import com.ninereader.data.entity.ReadingSessionEntity;
import com.ninereader.data.entity.SignatureEntity;
import com.ninereader.data.entity.TrashItemEntity;

/** Local Room database (§6.3). */
@Database(entities = {
        DocumentEntity.class,
        BookmarkEntity.class,
        CollectionEntity.class,
        DocumentCollectionCrossRef.class,
        SignatureEntity.class,
        ReadingSessionEntity.class,
        TrashItemEntity.class,
        AnnotationDraftEntity.class,
        FolderEntity.class
}, version = 1, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase instance;

    public abstract DocumentDao documents();

    public abstract BookmarkDao bookmarks();

    public abstract CollectionDao collections();

    public abstract SignatureDao signatures();

    public abstract ReadingSessionDao sessions();

    public abstract TrashDao trash();

    public abstract AnnotationDraftDao drafts();

    public abstract FolderDao folders();

    @NonNull
    public static AppDatabase get(@NonNull Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "ninereader.db")
                            .build();
                }
            }
        }
        return instance;
    }

    @VisibleForTesting
    public static void setInstanceForTests(AppDatabase db) {
        instance = db;
    }
}
