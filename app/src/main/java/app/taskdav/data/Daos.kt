package app.taskdav.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY displayName")
    fun observeAll(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections ORDER BY displayName")
    suspend fun getAll(): List<CollectionEntity>

    @Query("SELECT * FROM collections WHERE enabled = 1 AND supportsVtodo = 1")
    suspend fun getEnabledTodoCollections(): List<CollectionEntity>

    @Query("SELECT * FROM collections WHERE enabled = 1 AND supportsVevent = 1")
    suspend fun getEnabledEventCollections(): List<CollectionEntity>

    @Query("SELECT * FROM collections WHERE enabled = 1 AND supportsVjournal = 1")
    suspend fun getEnabledJournalCollections(): List<CollectionEntity>

    @Query("SELECT * FROM collections WHERE id = :id")
    suspend fun getById(id: Long): CollectionEntity?

    @Query("SELECT * FROM collections WHERE href = :href LIMIT 1")
    suspend fun getByHref(href: String): CollectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CollectionEntity): Long

    @Update
    suspend fun update(entity: CollectionEntity)

    @Query("UPDATE collections SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM collections")
    suspend fun deleteAll()

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE deleted = 0 ORDER BY summary COLLATE NOCASE")
    fun observeActive(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE deleted = 0 ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE deleted = 0 AND collectionId = :collectionId ORDER BY summary COLLATE NOCASE")
    fun observeByCollection(collectionId: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeById(id: Long): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE uid = :uid LIMIT 1")
    suspend fun getByUid(uid: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE deleted = 0")
    suspend fun getActive(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE dirty = 1 OR deleted = 1")
    suspend fun getDirty(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE collectionId = :collectionId AND deleted = 0")
    suspend fun getByCollection(collectionId: Long): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TaskEntity): Long

    @Update
    suspend fun update(entity: TaskEntity)

    @Query(
        """
        UPDATE tasks SET href = :href, etag = :etag, icsRaw = :icsRaw, dirty = 0
        WHERE id = :id AND updatedAt <= :pushedUpdatedAt
        """,
    )
    suspend fun markPushed(
        id: Long,
        href: String,
        etag: String?,
        icsRaw: String,
        pushedUpdatedAt: Long,
    ): Int

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM tasks WHERE collectionId = :collectionId AND dirty = 0 AND deleted = 0 AND uid NOT IN (:keepUids)")
    suspend fun deleteMissingRemote(collectionId: Long, keepUids: List<String>)

    @Query("DELETE FROM tasks WHERE collectionId = :collectionId AND dirty = 0 AND deleted = 0")
    suspend fun deleteCleanForCollection(collectionId: Long)

    @Query("DELETE FROM tasks WHERE collectionId = :collectionId")
    suspend fun deleteAllForCollection(collectionId: Long)
}

@Dao
interface EventDao {
    @Query("SELECT * FROM events WHERE deleted = 0 ORDER BY dtStartMillis ASC, summary COLLATE NOCASE")
    fun observeActive(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE uid = :uid LIMIT 1")
    suspend fun getByUid(uid: String): EventEntity?

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getById(id: Long): EventEntity?

    @Query("SELECT * FROM events WHERE deleted = 0")
    suspend fun getActive(): List<EventEntity>

    @Query("SELECT * FROM events WHERE dirty = 1 OR deleted = 1")
    suspend fun getDirty(): List<EventEntity>

    @Query("SELECT * FROM events WHERE collectionId = :collectionId AND deleted = 0")
    suspend fun getByCollection(collectionId: Long): List<EventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: EventEntity): Long

    @Update
    suspend fun update(entity: EventEntity)

    @Query(
        """
        UPDATE events SET href = :href, etag = :etag, icsRaw = :icsRaw, dirty = 0
        WHERE id = :id AND updatedAt <= :pushedUpdatedAt
        """,
    )
    suspend fun markPushed(
        id: Long,
        href: String,
        etag: String?,
        icsRaw: String,
        pushedUpdatedAt: Long,
    ): Int

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM events WHERE collectionId = :collectionId AND dirty = 0 AND deleted = 0")
    suspend fun deleteCleanForCollection(collectionId: Long)

    @Query("DELETE FROM events WHERE collectionId = :collectionId")
    suspend fun deleteAllForCollection(collectionId: Long)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE deleted = 0 ORDER BY summary COLLATE NOCASE")
    fun observeActive(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deleted = 0 ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): NoteEntity?

    @Query("SELECT * FROM notes WHERE uid = :uid LIMIT 1")
    suspend fun getByUid(uid: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE dirty = 1 OR deleted = 1")
    suspend fun getDirty(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE collectionId = :collectionId AND deleted = 0")
    suspend fun getByCollection(collectionId: Long): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: NoteEntity): Long

    @Update
    suspend fun update(entity: NoteEntity)

    @Query(
        """
        UPDATE notes SET href = :href, etag = :etag, icsRaw = :icsRaw, dirty = 0
        WHERE id = :id AND updatedAt <= :pushedUpdatedAt
        """,
    )
    suspend fun markPushed(
        id: Long,
        href: String,
        etag: String?,
        icsRaw: String,
        pushedUpdatedAt: Long,
    ): Int

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM notes WHERE collectionId = :collectionId")
    suspend fun deleteAllForCollection(collectionId: Long)
}

@Dao
interface SyncMetaDao {
    @Query("SELECT value FROM sync_meta WHERE key = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: SyncMetaEntity)
}
