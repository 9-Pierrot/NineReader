package com.ninereader.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.ninereader.data.entity.CollectionEntity;
import com.ninereader.data.entity.DocumentCollectionCrossRef;

import java.util.List;

@Dao
public interface CollectionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(CollectionEntity collection);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void link(DocumentCollectionCrossRef ref);

    @Query("DELETE FROM document_collections WHERE document_id = :documentId AND collection_id = :collectionId")
    void unlink(long documentId, long collectionId);

    @Query("SELECT * FROM collections ORDER BY name COLLATE NOCASE")
    LiveData<List<CollectionEntity>> observeAll();

    @Query("SELECT * FROM collections ORDER BY name COLLATE NOCASE")
    List<CollectionEntity> all();

    @Query("SELECT * FROM collections WHERE name = :name LIMIT 1")
    CollectionEntity byName(String name);

    @Query("SELECT collection_id FROM document_collections WHERE document_id = :documentId")
    List<Long> collectionIdsForDocument(long documentId);

    @Query("SELECT * FROM document_collections")
    List<DocumentCollectionCrossRef> allLinks();

    @Query("SELECT COUNT(*) FROM document_collections WHERE collection_id = :collectionId")
    int countDocuments(long collectionId);

    @Query("UPDATE collections SET name = :name WHERE id = :id")
    void rename(long id, String name);

    @Query("DELETE FROM collections WHERE id = :id")
    void delete(long id);
}
