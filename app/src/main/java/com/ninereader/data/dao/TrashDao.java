package com.ninereader.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.ninereader.data.entity.TrashItemEntity;

import java.util.List;

@Dao
public interface TrashDao {

    @Insert
    long insert(TrashItemEntity item);

    @Query("SELECT * FROM trash ORDER BY deleted_at DESC")
    LiveData<List<TrashItemEntity>> observeAll();

    @Query("SELECT * FROM trash ORDER BY deleted_at DESC")
    List<TrashItemEntity> all();

    @Query("SELECT * FROM trash WHERE id = :id")
    TrashItemEntity byId(long id);

    @Query("SELECT * FROM trash WHERE deleted_at < :threshold")
    List<TrashItemEntity> olderThan(long threshold);

    @Query("DELETE FROM trash WHERE id = :id")
    void delete(long id);
}
