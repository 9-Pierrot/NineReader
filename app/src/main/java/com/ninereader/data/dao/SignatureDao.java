package com.ninereader.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.ninereader.data.entity.SignatureEntity;

import java.util.List;

@Dao
public interface SignatureDao {

    @Insert
    long insert(SignatureEntity signature);

    @Query("SELECT * FROM signatures ORDER BY created_at DESC")
    LiveData<List<SignatureEntity>> observeAll();

    @Query("SELECT * FROM signatures ORDER BY created_at DESC")
    List<SignatureEntity> all();

    @Query("SELECT * FROM signatures WHERE id = :id")
    SignatureEntity byId(long id);

    @Query("DELETE FROM signatures WHERE id = :id")
    void delete(long id);
}
