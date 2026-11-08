package com.ninereader.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.ninereader.data.entity.DocumentEntity;

import java.util.List;

@Dao
public interface DocumentDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(DocumentEntity doc);

    @Update
    void update(DocumentEntity doc);

    @Query("SELECT * FROM documents WHERE id = :id")
    DocumentEntity byId(long id);

    @Query("SELECT * FROM documents WHERE id = :id")
    LiveData<DocumentEntity> observeById(long id);

    @Query("SELECT * FROM documents WHERE uri = :uri LIMIT 1")
    DocumentEntity byUri(String uri);

    @Query("SELECT * FROM documents WHERE hash = :hash AND hash IS NOT NULL ORDER BY last_opened DESC LIMIT 1")
    DocumentEntity byHash(String hash);

    @Query("SELECT * FROM documents WHERE in_trash = 0 AND last_opened > 0 ORDER BY last_opened DESC LIMIT :limit")
    LiveData<List<DocumentEntity>> observeRecents(int limit);

    @Query("SELECT * FROM documents WHERE in_trash = 0 AND favorite = 1 ORDER BY name COLLATE NOCASE")
    LiveData<List<DocumentEntity>> observeFavorites();

    @Query("SELECT * FROM documents WHERE in_trash = 0 AND last_opened > 0 ORDER BY last_opened DESC LIMIT 1")
    LiveData<DocumentEntity> observeLastOpened();

    @Query("SELECT * FROM documents WHERE in_trash = 0 AND last_opened > 0 ORDER BY last_opened DESC LIMIT 1")
    DocumentEntity lastOpened();

    @Query("SELECT * FROM documents WHERE in_trash = 0 ORDER BY name COLLATE NOCASE")
    LiveData<List<DocumentEntity>> observeAll();

    @Query("SELECT * FROM documents")
    List<DocumentEntity> all();

    @Query("SELECT d.* FROM documents d INNER JOIN document_collections dc ON dc.document_id = d.id "
            + "WHERE dc.collection_id = :collectionId AND d.in_trash = 0 ORDER BY d.name COLLATE NOCASE")
    LiveData<List<DocumentEntity>> observeByCollection(long collectionId);

    @Query("UPDATE documents SET last_page = :page, last_zoom = :zoom, reading_mode = :mode WHERE id = :id")
    void updatePosition(long id, int page, float zoom, String mode);

    @Query("UPDATE documents SET favorite = :favorite WHERE id = :id")
    void setFavorite(long id, boolean favorite);

    @Query("UPDATE documents SET last_opened = 0 WHERE id = :id")
    void removeFromRecents(long id);

    @Query("UPDATE documents SET last_opened = 0")
    void clearRecents();

    @Query("UPDATE documents SET in_trash = :inTrash WHERE id = :id")
    void setInTrash(long id, boolean inTrash);

    @Query("DELETE FROM documents WHERE id = :id")
    void delete(long id);

    @Query("SELECT COUNT(*) FROM documents WHERE in_trash = 0")
    LiveData<Integer> observeCount();
}
