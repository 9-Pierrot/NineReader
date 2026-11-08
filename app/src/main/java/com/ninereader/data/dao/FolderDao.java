package com.ninereader.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.ninereader.data.entity.FolderEntity;

import java.util.List;

@Dao
public interface FolderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(FolderEntity folder);

    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE")
    LiveData<List<FolderEntity>> observeAll();

    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE")
    List<FolderEntity> all();

    @Query("DELETE FROM folders WHERE uri = :uri")
    void delete(String uri);
}
