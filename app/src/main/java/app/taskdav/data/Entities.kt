package app.taskdav.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val href: String,
    val displayName: String,
    val colorArgb: Int?,
    val supportsVtodo: Boolean,
    val supportsVevent: Boolean,
    val supportsVjournal: Boolean,
    val ctag: String? = null,
    val syncToken: String? = null,
    val enabled: Boolean = true,
)

@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["uid"], unique = true),
        Index(value = ["collectionId"]),
        Index(value = ["parentUid"]),
    ]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val href: String?,
    val etag: String?,
    val collectionId: Long,
    val summary: String,
    val description: String?,
    val status: String?,
    val percentComplete: Int?,
    val priority: Int?,
    val dtStartMillis: Long?,
    val dueMillis: Long?,
    val completedMillis: Long?,
    val categories: String?,
    /** Parent task UID from RELATED-TO;RELTYPE=PARENT */
    val parentUid: String?,
    /** Linked VEVENT UID from RELATED-TO;RELTYPE=RELATED */
    val linkedEventUid: String?,
    /** Nestable folder VTODO (X-TASKDAV-KIND:CATEGORY) */
    val isCategory: Boolean = false,
    val icsRaw: String?,
    val dirty: Boolean = false,
    val deleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "events",
    indices = [
        Index(value = ["uid"], unique = true),
        Index(value = ["collectionId"]),
    ]
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val href: String?,
    val etag: String?,
    val collectionId: Long,
    val summary: String,
    val description: String?,
    val dtStartMillis: Long?,
    val dtEndMillis: Long?,
    val allDay: Boolean = false,
    val icsRaw: String?,
    val dirty: Boolean = false,
    val deleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "notes",
    indices = [
        Index(value = ["uid"], unique = true),
        Index(value = ["collectionId"]),
    ]
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val href: String?,
    val etag: String?,
    val collectionId: Long,
    val summary: String,
    val description: String?,
    val dtStartMillis: Long?,
    val categories: String?,
    val icsRaw: String?,
    val dirty: Boolean = false,
    val deleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)
