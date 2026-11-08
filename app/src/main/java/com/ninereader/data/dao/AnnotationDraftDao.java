package com.ninereader.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.ninereader.data.entity.AnnotationDraftEntity;

@Dao
public interface AnnotationDraftDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(AnnotationDraftEntity draft);

    @Query("SELECT * FROM annotation_drafts WHERE uri = :uri")
    AnnotationDraftEntity get(String uri);

    @Query("DELETE FROM annotation_drafts WHERE uri = :uri")
    void delete(String uri);
}
