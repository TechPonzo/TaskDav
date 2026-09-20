package app.taskdav.domain

import app.taskdav.caldav.IcalMapper
import app.taskdav.caldav.SyncEngine
import app.taskdav.caldav.SyncMode
import app.taskdav.data.AccountCredentials
import app.taskdav.data.AccountStore
import app.taskdav.data.CollectionEntity
import app.taskdav.data.EventEntity
import app.taskdav.data.TaskDavDatabase
import app.taskdav.data.TaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class TaskRepository(
    private val db: TaskDavDatabase,
    private val accountStore: AccountStore,
    private val syncEngine: SyncEngine,
) {
    fun observeCollections(): Flow<List<CollectionEntity>> = db.collections().observeAll()

    fun observeTasks(): Flow<List<TaskEntity>> = db.tasks().observeActive()

    fun observeChildTasks(parentUid: String): Flow<List<TaskEntity>> =
        db.tasks().observeChildren(parentUid)

    fun observeEvents(): Flow<List<EventEntity>> = db.events().observeActive()

    fun observeNotes(): Flow<List<app.taskdav.data.NoteEntity>> = db.notes().observeActive()

    fun observeRecentTasks(limit: Int = 8): Flow<List<TaskEntity>> =
        db.tasks().observeRecent(limit)

    fun observeRecentNotes(limit: Int = 8): Flow<List<app.taskdav.data.NoteEntity>> =
        db.notes().observeRecent(limit)

    fun observeTaskForest(
        collectionFilter: Long?,
        showCompleted: Boolean,
    ): Flow<List<TaskNode>> {
        return combine(
            db.tasks().observeActive(),
            db.collections().observeAll(),
        ) { tasks, collections ->
            TaskTreeBuilder.buildForest(
                tasks = tasks,
                collections = collections.associateBy { it.id },
                collectionFilter = collectionFilter,
                showCompleted = showCompleted,
            )
        }
    }

    suspend fun getTask(id: Long): TaskEntity? = db.tasks().getById(id)

    suspend fun getEventByUid(uid: String): EventEntity? = db.events().getByUid(uid)

    /** Load calendar details for a task that has a link but missing local event row. */
    suspend fun ensureLinkedEvent(taskId: Long): EventEntity? =
        syncEngine.ensureLinkedEventForTask(taskId)

    suspend fun getCollections(): List<CollectionEntity> = db.collections().getAll()

    suspend fun setCollectionEnabled(id: Long, enabled: Boolean) {
        db.collections().setEnabled(id, enabled)
    }

    suspend fun saveAccount(credentials: AccountCredentials) {
        accountStore.save(credentials)
    }

    fun accountConfigured(): Boolean = accountStore.isConfigured()

    fun loadAccount(): AccountCredentials? = accountStore.load()

    fun lastSyncMessage(): String? = accountStore.lastSyncMessage()

    fun lastSyncAt(): Long = accountStore.lastSyncAt()

    suspend fun discoverAndSave(): String {
        val result = syncEngine.refreshCollections()
        return "Found ${result.collections.size} collections"
    }

    suspend fun syncNow(mode: SyncMode = SyncMode.FULL): String = syncEngine.syncAll(mode).message

    suspend fun pushLocalChanges(): String = syncEngine.syncAll(SyncMode.PUSH_ONLY).message

    suspend fun createOrUpdateTask(state: TaskEditorState): Long {
        require(state.collectionId > 0) { "Pick a task list before saving" }
        val collection = db.collections().getById(state.collectionId)
            ?: throw IllegalStateException("Task list not found")
        require(collection.supportsVtodo) { "Collection “${collection.displayName}” does not support tasks" }

        val existing = state.id?.let { db.tasks().getById(it) }
            ?: db.tasks().getByUid(state.uid)
        val now = System.currentTimeMillis()
        val status = state.status?.takeIf { it.isNotBlank() } ?: "NEEDS-ACTION"
        val percent = state.percentComplete ?: 0
        val priority = state.priority ?: 0
        val completed = status.equals("COMPLETED", ignoreCase = true) || percent >= 100
        val collectionChanged = existing != null && existing.collectionId != state.collectionId
        val entity = TaskEntity(
            id = existing?.id ?: 0,
            uid = existing?.uid ?: state.uid,
            // Moving task lists must not keep the old collection's href/etag
            href = if (collectionChanged) null else existing?.href,
            etag = if (collectionChanged) null else existing?.etag,
            collectionId = state.collectionId,
            summary = state.summary.trim().ifBlank { "Untitled" },
            description = state.description.trim().ifBlank { null },
            status = status,
            percentComplete = percent,
            priority = priority,
            dtStartMillis = existing?.dtStartMillis,
            dueMillis = state.dueMillis,
            completedMillis = when {
                completed && existing?.completedMillis != null -> existing.completedMillis
                completed -> now
                else -> null
            },
            categories = state.categories?.trim()?.ifBlank { null },
            parentUid = state.parentUid?.takeIf { it.isNotBlank() },
            linkedEventUid = state.linkedEventUid?.takeIf { it.isNotBlank() },
            isCategory = state.isCategory,
            sortOrder = existing?.sortOrder
                ?: nextSortOrder(state.parentUid?.takeIf { it.isNotBlank() }, state.collectionId),
            // Always rebuild ICS from structured fields on next push
            icsRaw = null,
            dirty = true,
            deleted = false,
            updatedAt = now,
        )
        return if (existing == null) {
            db.tasks().upsert(entity)
        } else {
            db.tasks().upsert(entity.copy(id = existing.id))
            existing.id
        }
    }

    /**
     * Persist sibling order from the flat task list (appearance order among same parent).
     */
    suspend fun persistFlatOrder(flat: List<TaskNode>) {
        val now = System.currentTimeMillis()
        val counters = mutableMapOf<String?, Int>()
        for (node in flat) {
            val parent = node.task.parentUid
            val order = counters.getOrDefault(parent, 0)
            counters[parent] = order + 1
            val task = node.task
            if (task.sortOrder != order) {
                db.tasks().update(
                    task.copy(
                        sortOrder = order,
                        icsRaw = null,
                        dirty = true,
                        updatedAt = now,
                    ),
                )
            }
        }
    }

    private suspend fun nextSortOrder(parentUid: String?, collectionId: Long): Int {
        val siblings = db.tasks().getActive().filter {
            it.collectionId == collectionId && it.parentUid == parentUid
        }
        return (siblings.maxOfOrNull { it.sortOrder } ?: -1) + 1
    }

    suspend fun toggleComplete(taskId: Long) {
        val task = db.tasks().getById(taskId) ?: return
        val completed = TaskTreeBuilder.isCompleted(task)
        val now = System.currentTimeMillis()
        val updated = if (completed) {
            task.copy(
                status = "NEEDS-ACTION",
                percentComplete = 0,
                completedMillis = null,
                dirty = true,
                updatedAt = now,
            )
        } else {
            task.copy(
                status = "COMPLETED",
                percentComplete = 100,
                completedMillis = now,
                dirty = true,
                updatedAt = now,
            )
        }
        db.tasks().update(updated)
    }

    suspend fun startTask(taskId: Long) {
        val task = db.tasks().getById(taskId) ?: return
        if (task.isCategory) return
        val now = System.currentTimeMillis()
        db.tasks().update(
            task.copy(
                status = "IN-PROCESS",
                percentComplete = if ((task.percentComplete ?: 0) >= 100) 0 else task.percentComplete,
                dtStartMillis = now,
                completedMillis = null,
                dirty = true,
                updatedAt = now,
            ),
        )
    }

    /**
     * Convert a top-level task into a category or the reverse.
     * Subtasks cannot become categories (they have a parent).
     */
    suspend fun setIsCategory(taskId: Long, isCategory: Boolean) {
        val task = db.tasks().getById(taskId) ?: return
        if (!task.parentUid.isNullOrBlank()) return
        if (task.isCategory == isCategory) return
        val now = System.currentTimeMillis()
        db.tasks().update(
            task.copy(
                isCategory = isCategory,
                dueMillis = if (isCategory) null else task.dueMillis,
                priority = if (isCategory) 0 else task.priority,
                linkedEventUid = if (isCategory) null else task.linkedEventUid,
                icsRaw = null,
                dirty = true,
                updatedAt = now,
            ),
        )
    }

    suspend fun endTask(taskId: Long) {
        val task = db.tasks().getById(taskId) ?: return
        if (task.isCategory) return
        val now = System.currentTimeMillis()
        db.tasks().update(
            task.copy(
                status = "COMPLETED",
                percentComplete = 100,
                completedMillis = now,
                dtStartMillis = task.dtStartMillis ?: now,
                dirty = true,
                updatedAt = now,
            ),
        )
    }

    fun observeTask(id: Long): Flow<TaskEntity?> = db.tasks().observeById(id)

    suspend fun deleteTask(taskId: Long) {
        val task = db.tasks().getById(taskId) ?: return
        // Soft-delete children recursively
        val all = db.tasks().getActive()
        val toDelete = mutableListOf(task)
        var i = 0
        while (i < toDelete.size) {
            val uid = toDelete[i].uid
            toDelete += all.filter { it.parentUid == uid && it !in toDelete }
            i++
        }
        val now = System.currentTimeMillis()
        for (t in toDelete) {
            if (t.href.isNullOrBlank()) {
                db.tasks().deleteById(t.id)
            } else {
                db.tasks().update(t.copy(deleted = true, dirty = true, updatedAt = now))
            }
        }
    }

    suspend fun linkEvent(taskId: Long, eventUid: String?) {
        val task = db.tasks().getById(taskId) ?: return
        db.tasks().update(
            task.copy(
                linkedEventUid = eventUid,
                dirty = true,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun createLinkedEvent(
        taskId: Long,
        collectionId: Long,
        summary: String,
        startMillis: Long,
        endMillis: Long,
        location: String? = null,
    ): String {
        val task = db.tasks().getById(taskId) ?: throw IllegalStateException("Task missing")
        val uid = IcalMapper.newUid()
        val event = EventEntity(
            uid = uid,
            href = null,
            etag = null,
            collectionId = collectionId,
            summary = summary.ifBlank { task.summary },
            description = "Linked from task ${task.uid}",
            location = location?.trim()?.ifBlank { null },
            dtStartMillis = startMillis,
            dtEndMillis = endMillis,
            allDay = false,
            icsRaw = null,
            dirty = true,
            deleted = false,
        )
        db.events().upsert(event)
        db.tasks().update(
            task.copy(
                linkedEventUid = uid,
                dirty = true,
                updatedAt = System.currentTimeMillis(),
            )
        )
        return uid
    }

    suspend fun updateEvent(
        uid: String,
        summary: String,
        startMillis: Long,
        endMillis: Long,
        location: String?,
        description: String?,
    ) {
        val existing = db.events().getByUid(uid) ?: throw IllegalStateException("Event missing")
        db.events().update(
            existing.copy(
                summary = summary.trim().ifBlank { existing.summary },
                location = location?.trim()?.ifBlank { null },
                description = description?.trim()?.ifBlank { null },
                dtStartMillis = startMillis,
                dtEndMillis = endMillis,
                icsRaw = null,
                dirty = true,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun createEvent(
        collectionId: Long,
        summary: String,
        startMillis: Long,
        endMillis: Long,
        location: String? = null,
        description: String? = null,
    ): String {
        val uid = IcalMapper.newUid()
        db.events().upsert(
            EventEntity(
                uid = uid,
                href = null,
                etag = null,
                collectionId = collectionId,
                summary = summary.trim().ifBlank { "Untitled" },
                description = description?.trim()?.ifBlank { null },
                location = location?.trim()?.ifBlank { null },
                dtStartMillis = startMillis,
                dtEndMillis = endMillis,
                allDay = false,
                icsRaw = null,
                dirty = true,
                deleted = false,
            ),
        )
        return uid
    }

    suspend fun deleteEvent(eventId: Long) {
        val event = db.events().getById(eventId) ?: return
        // Unlink tasks that pointed at this event
        val linked = db.tasks().getActive().filter {
            it.linkedEventUid.equals(event.uid, ignoreCase = true)
        }
        val now = System.currentTimeMillis()
        for (task in linked) {
            db.tasks().update(
                task.copy(linkedEventUid = null, dirty = true, updatedAt = now),
            )
        }
        if (event.href.isNullOrBlank()) {
            db.events().deleteById(event.id)
        } else {
            db.events().update(
                event.copy(deleted = true, dirty = true, updatedAt = now),
            )
        }
    }

    suspend fun getNote(id: Long) = db.notes().getById(id)

    suspend fun createOrUpdateNote(
        id: Long?,
        uid: String,
        collectionId: Long,
        summary: String,
        description: String,
        categories: String?,
    ): Long {
        val existing = id?.let { db.notes().getById(it) }
            ?: db.notes().getByUid(uid)
        val now = System.currentTimeMillis()
        val collectionChanged = existing != null && existing.collectionId != collectionId
        val entity = app.taskdav.data.NoteEntity(
            id = existing?.id ?: 0,
            uid = existing?.uid ?: uid,
            href = if (collectionChanged) null else existing?.href,
            etag = if (collectionChanged) null else existing?.etag,
            collectionId = collectionId,
            summary = summary.trim().ifBlank { "Untitled" },
            description = description.trim().ifBlank { null },
            dtStartMillis = existing?.dtStartMillis ?: now,
            categories = categories?.trim()?.ifBlank { null },
            // Drop stale ICS so sync always rebuilds from current fields (incl. details)
            icsRaw = null,
            dirty = true,
            deleted = false,
            updatedAt = now,
        )
        return if (existing == null) {
            db.notes().upsert(entity)
        } else {
            db.notes().update(entity.copy(id = existing.id))
            existing.id
        }
    }

    suspend fun deleteNote(noteId: Long) {
        val note = db.notes().getById(noteId) ?: return
        if (note.href.isNullOrBlank()) {
            db.notes().deleteById(note.id)
        } else {
            db.notes().update(
                note.copy(deleted = true, dirty = true, updatedAt = System.currentTimeMillis()),
            )
        }
    }
}
