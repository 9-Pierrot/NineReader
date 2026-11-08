package com.ninereader.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.ninereader.data.entity.ReadingSessionEntity;

import java.util.List;

@Dao
public interface ReadingSessionDao {

    @Insert
    long insert(ReadingSessionEntity session);

    @Query("SELECT * FROM reading_sessions WHERE document_id = :documentId ORDER BY start")
    List<ReadingSessionEntity> forDocument(long documentId);

    @Query("SELECT * FROM reading_sessions ORDER BY start")
    List<ReadingSessionEntity> all();

    @Query("SELECT COALESCE(SUM(`end` - start), 0) FROM reading_sessions WHERE start >= :since")
    LiveData<Long> observeTotalTimeSince(long since);

    @Query("SELECT COALESCE(SUM(pages_read), 0) FROM reading_sessions WHERE start >= :since")
    LiveData<Integer> observePagesSince(long since);

    @Query("SELECT COALESCE(SUM(`end` - start), 0) FROM reading_sessions WHERE document_id = :documentId")
    long totalTimeForDocument(long documentId);

    @Query("SELECT COALESCE(SUM(pages_read), 0) FROM reading_sessions WHERE document_id = :documentId")
    int totalPagesForDocument(long documentId);
}
