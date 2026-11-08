package com.ninereader.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.ninereader.data.entity.BookmarkEntity;

import java.util.List;

@Dao
public interface BookmarkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(BookmarkEntity bookmark);

    @Query("SELECT * FROM bookmarks WHERE document_id = :documentId ORDER BY page")
    LiveData<List<BookmarkEntity>> observeForDocument(long documentId);

    @Query("SELECT * FROM bookmarks WHERE document_id = :documentId ORDER BY page")
    List<BookmarkEntity> forDocument(long documentId);

    @Query("SELECT * FROM bookmarks")
    List<BookmarkEntity> all();

    @Query("SELECT COUNT(*) FROM bookmarks WHERE document_id = :documentId AND page = :page")
    int count(long documentId, int page);

    @Query("DELETE FROM bookmarks WHERE document_id = :documentId AND page = :page")
    void delete(long documentId, int page);

    @Query("DELETE FROM bookmarks WHERE id = :id")
    void deleteById(long id);

    @Query("UPDATE bookmarks SET label = :label WHERE id = :id")
    void rename(long id, String label);
}
