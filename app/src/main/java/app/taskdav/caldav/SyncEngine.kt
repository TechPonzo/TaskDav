package app.taskdav.caldav

import app.taskdav.data.AccountStore
import app.taskdav.data.CollectionEntity
import app.taskdav.data.EventEntity
import app.taskdav.data.NoteEntity
import app.taskdav.data.TaskDavDatabase
import app.taskdav.data.TaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

enum class SyncMode {
    /** Upload dirty items only — used after local create/edit so we don't re-pull the whole server. */
    PUSH_ONLY,

    /** Push dirty items, then pull collections from Radicale (manual refresh / background sync). */
    FULL,
}

data class SyncResult(
    val tasksPulled: Int = 0,
    val eventsPulled: Int = 0,
    val notesPulled: Int = 0,
    val message: String,
)

class SyncEngine(
    private val db: TaskDavDatabase,
    private val accountStore: AccountStore,
    private val httpFactory: CalDavHttpFactory = CalDavHttpFactory(),
    private val discoverer: CollectionDiscoverer = CollectionDiscoverer(httpFactory),
) {
    suspend fun refreshCollections(): DiscoveryResult = withContext(Dispatchers.IO) {
        val creds = accountStore.load() ?: throw CalDavException("Account not configured")
        val result = discoverer.testAndDiscover(creds)
        accountStore.save(creds.copy(calendarHome = result.calendarHome))

        val existing = db.collections().getAll()
        val existingByNorm = existing.associateBy { normalizeHref(it.href) }
        val remoteNorms = result.collections.map { normalizeHref(it.href) }.toSet()

        for (remote in result.collections) {
            val norm = normalizeHref(remote.href)
            val prev = existingByNorm[norm]
            val entity = CollectionEntity(
                id = prev?.id ?: 0,
                href = MultistatusParser.ensureTrailingSlash(remote.href),
                displayName = remote.displayName,
                colorArgb = remote.colorArgb,
                supportsVtodo = remote.supportsVtodo,
                supportsVevent = remote.supportsVevent,
                supportsVjournal = remote.supportsVjournal,
                ctag = remote.ctag,
                syncToken = remote.syncToken,
                enabled = prev?.enabled ?: true,
            )
            if (prev == null) {
                db.collections().upsert(entity)
            } else {
                db.collections().update(entity.copy(id = prev.id))
            }
        }
        // Drop local calendars that no longer exist on the server — but never destroy
        // unpublished (dirty) rows; those must be pushed or kept until the user deletes them.
        for (local in existing) {
            if (normalizeHref(local.href) in remoteNorms) continue
            val dirtyTasks = db.tasks().getByCollection(local.id).any { it.dirty || it.deleted }
            val dirtyEvents = db.events().getByCollection(local.id).any { it.dirty || it.deleted }
            val dirtyNotes = db.notes().getByCollection(local.id).any { it.dirty || it.deleted }
            if (dirtyTasks || dirtyEvents || dirtyNotes) {
                // Keep the collection so pending uploads still have a target
                continue
            }
            db.tasks().deleteAllForCollection(local.id)
            db.events().deleteAllForCollection(local.id)
            db.notes().deleteAllForCollection(local.id)
            db.collections().deleteById(local.id)
        }
        result
    }

    suspend fun syncAll(mode: SyncMode = SyncMode.FULL): SyncResult = withContext(Dispatchers.IO) {
        val creds = accountStore.load() ?: throw CalDavException("Account not configured")
        val client = httpFactory.create(creds)

        val pushedTaskUids = mutableSetOf<String>()
        val pushedEventUids = mutableSetOf<String>()
        val pushedNoteUids = mutableSetOf<String>()

        // Push first so unpublished local edits cannot be wiped by collection rediscovery
        val pushErrors = mutableListOf<String>()
        pushErrors += pushDirtyTasks(client, pushedTaskUids)
        pushErrors += pushDirtyEvents(client, pushedEventUids)
        pushErrors += pushDirtyNotes(client, pushedNoteUids)

        var tasksPulled = 0
        var eventsPulled = 0
        var notesPulled = 0

        if (mode == SyncMode.FULL) {
            try {
                refreshCollections()
            } catch (e: Exception) {
                pushErrors += "Collection refresh: ${e.message ?: e.javaClass.simpleName}"
            }

            for (collection in db.collections().getEnabledTodoCollections()) {
                try {
                    tasksPulled += pullTodos(client, collection, pushedTaskUids)
                } catch (e: Exception) {
                    pushErrors += "Pull tasks ${collection.displayName}: ${e.message ?: e.javaClass.simpleName}"
                }
            }
            for (collection in db.collections().getEnabled()) {
                // Task lists often host linked VEVENTs too (Radicale); don't require supportsVevent
                if (!collection.supportsVevent && !collection.supportsVtodo) continue
                try {
                    eventsPulled += pullEvents(client, collection, pushedEventUids)
                } catch (e: Exception) {
                    pushErrors += "Pull events ${collection.displayName}: ${e.message ?: e.javaClass.simpleName}"
                }
            }
            for (collection in db.collections().getEnabledJournalCollections()) {
                try {
                    notesPulled += pullNotes(client, collection, pushedNoteUids)
                } catch (e: Exception) {
                    pushErrors += "Pull notes ${collection.displayName}: ${e.message ?: e.javaClass.simpleName}"
                }
            }

            try {
                eventsPulled += refreshLinkedEvents(client)
            } catch (e: Exception) {
                pushErrors += "Refresh linked events: ${e.message ?: e.javaClass.simpleName}"
            }
        }

        val stillDirtyTasks = db.tasks().getDirty().count { !it.deleted }
        val stillDirtyNotes = db.notes().getDirty().count { !it.deleted }
        val stillDirtyEvents = db.events().getDirty().count { !it.deleted }
        val pending = stillDirtyTasks + stillDirtyNotes + stillDirtyEvents

        val parts = mutableListOf(
            "Pushed ${pushedTaskUids.size} tasks / ${pushedEventUids.size} events / ${pushedNoteUids.size} notes",
        )
        if (mode == SyncMode.FULL) {
            parts += "pulled $tasksPulled / $eventsPulled / $notesPulled"
        }
        if (pending > 0) parts += "$pending still waiting to upload"
        var msg = parts.joinToString("; ")
        if (pushErrors.isNotEmpty()) {
            msg += ". Issues: ${pushErrors.joinToString("; ")}"
        }
        accountStore.setLastSync(System.currentTimeMillis(), msg)
        SyncResult(
            tasksPulled = tasksPulled,
            eventsPulled = eventsPulled,
            notesPulled = notesPulled,
            message = msg,
        )
    }

    private fun normalizeHref(href: String): String {
        val trimmed = href.trim()
        return try {
            URI(trimmed).normalize().toString().trimEnd('/').lowercase()
        } catch (_: Exception) {
            trimmed.trimEnd('/').lowercase()
        }
    }

    private suspend fun pullTodos(
        client: okhttp3.OkHttpClient,
        collection: CollectionEntity,
        skipUids: Set<String>,
    ): Int {
        client.report(collection.href, CalDavXml.calendarQuery("VTODO")).use { response ->
            val xml = response.requireSuccess("REPORT VTODO ${collection.displayName}")
            val objects = MultistatusParser.parseCalendarObjects(xml, collection.href)
            val keepUids = mutableListOf<String>()
            for (obj in objects) {
                val parsedList = try {
                    IcalMapper.parseTodos(obj.ics)
                } catch (e: Exception) {
                    IcalMapper.extractUid(obj.ics)?.let { keepUids += it }
                    continue
                }
                for (parsed in parsedList) {
                    keepUids += parsed.uid
                    val existing = db.tasks().getByUid(parsed.uid)
                    if (existing?.dirty == true) continue
                    // Keep local fields from a push in this same sync (avoid stale REPORT overwrite)
                    if (parsed.uid in skipUids) {
                        if (existing != null) {
                            db.tasks().update(
                                existing.copy(href = obj.href, etag = obj.etag),
                            )
                        }
                        continue
                    }
                    // Unchanged on server — skip local rewrite
                    if (existing != null &&
                        !obj.etag.isNullOrBlank() &&
                        existing.etag == obj.etag
                    ) {
                        continue
                    }
                    val entity = TaskEntity(
                        id = existing?.id ?: 0,
                        uid = parsed.uid,
                        href = obj.href,
                        etag = obj.etag,
                        collectionId = collection.id,
                        summary = parsed.summary,
                        description = parsed.description,
                        status = parsed.status,
                        percentComplete = parsed.percentComplete,
                        priority = parsed.priority,
                        dtStartMillis = parsed.dtStartMillis,
                        dueMillis = parsed.dueMillis,
                        completedMillis = parsed.completedMillis,
                        categories = parsed.categories,
                        parentUid = parsed.parentUid,
                        linkedEventUid = parsed.linkedEventUid,
                        isCategory = parsed.isCategory,
                        sortOrder = parsed.sortOrder,
                        icsRaw = obj.ics,
                        dirty = false,
                        deleted = false,
                        updatedAt = existing?.updatedAt ?: System.currentTimeMillis(),
                    )
                    if (existing == null) db.tasks().upsert(entity) else db.tasks().update(entity.copy(id = existing.id))
                }
                // Some clients store the linked VEVENT in the same resource as the VTODO
                upsertEmbeddedEvents(obj.ics, obj.href, obj.etag, collection.id)
            }
            val current = db.tasks().getByCollection(collection.id)
            for (task in current) {
                if (!task.dirty && !task.deleted && task.uid !in keepUids && task.uid !in skipUids) {
                    db.tasks().deleteById(task.id)
                }
            }
            return objects.size
        }
    }

    private suspend fun pullEvents(
        client: okhttp3.OkHttpClient,
        collection: CollectionEntity,
        skipUids: Set<String>,
    ): Int {
        client.report(collection.href, CalDavXml.calendarQuery("VEVENT")).use { response ->
            val xml = response.requireSuccess("REPORT VEVENT ${collection.displayName}")
            val objects = MultistatusParser.parseCalendarObjects(xml, collection.href)
            val keepUids = mutableListOf<String>()
            for (obj in objects) {
                val parsedList = try {
                    IcalMapper.parseEvents(obj.ics)
                } catch (_: Exception) {
                    IcalMapper.extractUid(obj.ics)?.let { keepUids += it }
                    continue
                }
                for (parsed in parsedList) {
                    keepUids += parsed.uid
                    val existing = db.events().getByUid(parsed.uid)
                    if (existing?.dirty == true) continue
                    if (parsed.uid in skipUids) {
                        if (existing != null) {
                            db.events().update(existing.copy(href = obj.href, etag = obj.etag))
                        }
                        continue
                    }
                    if (existing != null &&
                        !obj.etag.isNullOrBlank() &&
                        existing.etag == obj.etag
                    ) {
                        continue
                    }
                    val entity = EventEntity(
                        id = existing?.id ?: 0,
                        uid = parsed.uid,
                        href = obj.href,
                        etag = obj.etag,
                        collectionId = collection.id,
                        summary = parsed.summary,
                        description = parsed.description,
                        location = parsed.location,
                        dtStartMillis = parsed.dtStartMillis,
                        dtEndMillis = parsed.dtEndMillis,
                        allDay = parsed.allDay,
                        icsRaw = obj.ics,
                        dirty = false,
                        deleted = false,
                        updatedAt = System.currentTimeMillis(),
                    )
                    if (existing == null) db.events().upsert(entity) else db.events().update(entity.copy(id = existing.id))
                }
            }
            // Only prune when this collection advertises VEVENT and the query returned data.
            // Empty REPORT on a task list must not wipe linked events still referenced by tasks.
            if (collection.supportsVevent && objects.isNotEmpty()) {
                val linkedUids = db.tasks().getActive()
                    .mapNotNull { it.linkedEventUid?.takeIf { uid -> uid.isNotBlank() } }
                    .toSet()
                val current = db.events().getByCollection(collection.id)
                for (event in current) {
                    if (event.uid in linkedUids) continue
                    if (!event.dirty && !event.deleted && event.uid !in keepUids && event.uid !in skipUids) {
                        db.events().deleteById(event.id)
                    }
                }
            }
            return objects.size
        }
    }

    /**
     * Re-fetch every calendar event linked from a task so local DB matches Radicale
     * (updates from this device after push, or from other CalDAV clients).
     * Skips events with unsynced local edits (dirty).
     */
    private suspend fun refreshLinkedEvents(client: okhttp3.OkHttpClient): Int {
        val linkedUids = db.tasks().getActive()
            .mapNotNull { IcalMapper.normalizeUid(it.linkedEventUid) }
            .toSet()
        if (linkedUids.isEmpty()) return 0

        val tasksByLink = db.tasks().getActive()
            .mapNotNull { task ->
                IcalMapper.normalizeUid(task.linkedEventUid)?.let { it to task }
            }
            .groupBy({ it.first }, { it.second })

        var refreshed = 0
        for (uid in linkedUids) {
            val existing = db.events().getByUid(uid)
            if (existing?.dirty == true) continue
            val task = tasksByLink[uid]?.firstOrNull()
                ?: continue
            val before = existing?.let { snapshotEvent(it) }
            val updated = refreshEventFromServer(client, task, existing)
            if (updated != null && snapshotEvent(updated) != before) {
                refreshed++
            } else if (updated == null && existing == null) {
                // Still missing — try full recovery path
                if (fetchLinkedEventFromServer(client, task) != null) refreshed++
            }
        }
        return refreshed
    }

    private fun snapshotEvent(event: EventEntity): String =
        listOf(
            event.uid,
            event.etag.orEmpty(),
            event.summary,
            event.location.orEmpty(),
            event.dtStartMillis?.toString().orEmpty(),
            event.dtEndMillis?.toString().orEmpty(),
            event.allDay.toString(),
        ).joinToString("|")

    /** Public entry: load/refresh linked calendar when opening a task. */
    suspend fun ensureLinkedEventForTask(taskId: Long): EventEntity? = withContext(Dispatchers.IO) {
        val task = db.tasks().getById(taskId) ?: return@withContext null
        IcalMapper.normalizeUid(task.linkedEventUid) ?: return@withContext null
        val local = findLocalLinkedEvent(task)
        val creds = accountStore.load() ?: return@withContext local
        val client = httpFactory.create(creds)
        // Prefer server copy when there are no pending local edits
        if (local != null && !local.dirty) {
            refreshEventFromServer(client, task, local)?.let { return@withContext it }
            return@withContext local
        }
        if (local != null && local.dirty) return@withContext local
        fetchLinkedEventFromServer(client, task) ?: local
    }

    private suspend fun findLocalLinkedEvent(task: TaskEntity): EventEntity? {
        val uid = IcalMapper.normalizeUid(task.linkedEventUid)
        if (uid != null) {
            // Broken link: RELATED-TO points at another task, not a VEVENT
            if (db.tasks().getByUid(uid) != null && db.events().getByUid(uid) == null) {
                val byTitle = db.events().getActive().find {
                    it.collectionId == task.collectionId &&
                        it.summary.equals(task.summary, ignoreCase = true)
                }
                if (byTitle != null) {
                    db.tasks().update(
                        task.copy(
                            linkedEventUid = byTitle.uid,
                            dirty = true,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                    return byTitle
                }
            }
            db.events().getByUid(uid)?.let { event ->
                if (event.deleted) {
                    val restored = event.copy(deleted = false, dirty = false)
                    db.events().update(restored)
                    return restored
                }
                return event
            }
            // Embedded in the task ICS resource
            if (!task.icsRaw.isNullOrBlank()) {
                upsertEmbeddedEvents(task.icsRaw, task.href, task.etag, task.collectionId)
                db.events().getByUid(uid)?.let { return it }
                IcalMapper.parseEvents(task.icsRaw).firstOrNull()?.let { parsed ->
                    val stored = upsertParsedEvent(parsed, task.href, task.etag, task.collectionId)
                    if (stored.uid != uid) {
                        db.tasks().update(
                            task.copy(
                                linkedEventUid = stored.uid,
                                dirty = true,
                                updatedAt = System.currentTimeMillis(),
                            ),
                        )
                    }
                    return stored
                }
            }
        }
        // Last resort: same-title event on the same list
        return db.events().getActive().find {
            it.collectionId == task.collectionId &&
                it.summary.equals(task.summary, ignoreCase = true)
        }
    }

    private suspend fun refreshEventFromServer(
        client: okhttp3.OkHttpClient,
        task: TaskEntity,
        existing: EventEntity?,
    ): EventEntity? {
        val uid = IcalMapper.normalizeUid(task.linkedEventUid) ?: existing?.uid ?: return null
        val collections = db.collections().getEnabled()
        val preferred = collections.find { it.id == (existing?.collectionId ?: task.collectionId) }
        val candidates = buildList {
            if (preferred != null) add(preferred)
            collections.filterTo(this) { it.id != preferred?.id }
        }

        // Known href first (fast path for updates from other devices)
        val knownHref = existing?.href
        if (!knownHref.isNullOrBlank()) {
            fetchAndStoreEventFromHref(
                client,
                knownHref,
                existing?.collectionId ?: task.collectionId,
                uid,
                knownEtag = existing?.etag,
            )?.let { return it }
        }

        // Task resource may embed the VEVENT
        val taskHref = task.href
        if (!taskHref.isNullOrBlank()) {
            fetchAndStoreEventFromHref(client, taskHref, task.collectionId, uid)?.let { return it }
        }

        for (collection in candidates) {
            val href = MultistatusParser.joinUrl(collection.href, "$uid.ics")
            fetchAndStoreEventFromHref(client, href, collection.id, uid)?.let { return it }
        }

        for (collection in candidates) {
            try {
                client.report(collection.href, CalDavXml.calendarQueryEventByUid(uid)).use { response ->
                    if (!response.isSuccessful) return@use
                    val xml = response.body?.string().orEmpty()
                    val objects = MultistatusParser.parseCalendarObjects(xml, collection.href)
                    for (obj in objects) {
                        upsertEmbeddedEvents(obj.ics, obj.href, obj.etag, collection.id)
                    }
                }
                db.events().getByUid(uid)?.takeIf { !it.deleted }?.let { return it }
            } catch (_: Exception) {
                // try next collection
            }
        }
        return null
    }

    private suspend fun fetchLinkedEventFromServer(
        client: okhttp3.OkHttpClient,
        task: TaskEntity,
    ): EventEntity? {
        val uid = IcalMapper.normalizeUid(task.linkedEventUid) ?: return null
        return refreshEventFromServer(client, task, db.events().getByUid(uid))
    }

    private suspend fun existingForHref(href: String, wantedUid: String?): EventEntity? {
        if (!wantedUid.isNullOrBlank()) {
            db.events().getByUid(wantedUid)?.let { return it }
        }
        return db.events().getActive().find { it.href.equals(href, ignoreCase = true) }
    }

    private suspend fun fetchAndStoreEventFromHref(
        client: okhttp3.OkHttpClient,
        href: String,
        collectionId: Long,
        wantedUid: String?,
        knownEtag: String? = null,
    ): EventEntity? {
        return try {
            client.getResource(href, ifNoneMatch = knownEtag).use { response ->
                if (response.code == 304) {
                    return@use existingForHref(href, wantedUid)
                }
                if (!response.isSuccessful) return@use null
                val ics = response.body?.string().orEmpty()
                if (ics.isBlank()) return@use null
                val etag = response.header("ETag")?.trim()?.trim('"')
                upsertEmbeddedEvents(ics, href, etag, collectionId)
                val events = IcalMapper.parseEvents(ics)
                val match = when {
                    wantedUid != null -> events.find {
                        IcalMapper.normalizeUid(it.uid).equals(wantedUid, ignoreCase = true)
                    }
                    else -> events.firstOrNull()
                } ?: events.firstOrNull()
                match?.let { upsertParsedEvent(it, href, etag, collectionId) }
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun upsertEmbeddedEvents(
        ics: String,
        href: String?,
        etag: String?,
        collectionId: Long,
    ) {
        val parsed = try {
            IcalMapper.parseEvents(ics)
        } catch (_: Exception) {
            return
        }
        for (event in parsed) {
            upsertParsedEvent(event, href, etag, collectionId)
        }
    }

    private suspend fun upsertParsedEvent(
        parsed: ParsedEvent,
        href: String?,
        etag: String?,
        collectionId: Long,
    ): EventEntity {
        val existing = db.events().getByUid(parsed.uid)
        if (existing?.dirty == true) return existing
        val entity = EventEntity(
            id = existing?.id ?: 0,
            uid = parsed.uid,
            href = href ?: existing?.href,
            etag = etag ?: existing?.etag,
            collectionId = collectionId,
            summary = parsed.summary,
            description = parsed.description,
            location = parsed.location,
            dtStartMillis = parsed.dtStartMillis,
            dtEndMillis = parsed.dtEndMillis,
            allDay = parsed.allDay,
            icsRaw = parsed.icsRaw,
            dirty = false,
            deleted = false,
            updatedAt = System.currentTimeMillis(),
        )
        return if (existing == null) {
            val id = db.events().upsert(entity)
            entity.copy(id = id)
        } else {
            val updated = entity.copy(id = existing.id)
            db.events().update(updated)
            updated
        }
    }

    private suspend fun pullNotes(
        client: okhttp3.OkHttpClient,
        collection: CollectionEntity,
        skipUids: Set<String>,
    ): Int {
        client.report(collection.href, CalDavXml.calendarQuery("VJOURNAL")).use { response ->
            val xml = response.requireSuccess("REPORT VJOURNAL ${collection.displayName}")
            val objects = MultistatusParser.parseCalendarObjects(xml, collection.href)
            val keepUids = mutableListOf<String>()
            for (obj in objects) {
                val parsedList = try {
                    IcalMapper.parseNotes(obj.ics)
                } catch (_: Exception) {
                    IcalMapper.extractUid(obj.ics)?.let { keepUids += it }
                    continue
                }
                for (parsed in parsedList) {
                    keepUids += parsed.uid
                    val existing = db.notes().getByUid(parsed.uid)
                    if (existing?.dirty == true) continue
                    if (parsed.uid in skipUids) {
                        if (existing != null) {
                            db.notes().update(existing.copy(href = obj.href, etag = obj.etag))
                        }
                        continue
                    }
                    if (existing != null &&
                        !obj.etag.isNullOrBlank() &&
                        existing.etag == obj.etag
                    ) {
                        continue
                    }
                    val entity = NoteEntity(
                        id = existing?.id ?: 0,
                        uid = parsed.uid,
                        href = obj.href,
                        etag = obj.etag,
                        collectionId = collection.id,
                        summary = parsed.summary,
                        description = parsed.description,
                        dtStartMillis = parsed.dtStartMillis,
                        categories = parsed.categories,
                        icsRaw = obj.ics,
                        dirty = false,
                        deleted = false,
                        updatedAt = existing?.updatedAt ?: System.currentTimeMillis(),
                    )
                    if (existing == null) db.notes().upsert(entity) else db.notes().update(entity.copy(id = existing.id))
                }
            }
            val current = db.notes().getByCollection(collection.id)
            for (note in current) {
                if (!note.dirty && !note.deleted && note.uid !in keepUids && note.uid !in skipUids) {
                    db.notes().deleteById(note.id)
                }
            }
            return objects.size
        }
    }

    private suspend fun pushDirtyTasks(
        client: okhttp3.OkHttpClient,
        pushedUids: MutableSet<String>,
    ): List<String> {
        val errors = mutableListOf<String>()
        for (task in db.tasks().getDirty()) {
            try {
                if (task.deleted) {
                    pushDeleteTask(client, task, errors)
                    continue
                }
                val latest = db.tasks().getById(task.id) ?: continue
                if (latest.deleted) {
                    pushDeleteTask(client, latest, errors)
                    continue
                }
                val collection = db.collections().getById(latest.collectionId)
                if (collection == null) {
                    errors += "Task ${latest.uid}: collection missing"
                    continue
                }
                pushPutTask(client, latest, collection, errors, pushedUids)
            } catch (e: Exception) {
                errors += "Task ${task.uid}: ${e.message ?: e.javaClass.simpleName}"
            }
        }
        return errors
    }

    private suspend fun pushDeleteTask(
        client: okhttp3.OkHttpClient,
        task: TaskEntity,
        errors: MutableList<String>,
    ) {
        val href = task.href
        if (href.isNullOrBlank()) {
            db.tasks().deleteById(task.id)
            return
        }
        client.deleteResource(href, task.etag).use { response ->
            when {
                response.isSuccessful || response.code == 404 -> {
                    db.tasks().deleteById(task.id)
                }
                response.code == 412 || response.code == 409 -> {
                    response.body?.close()
                    client.deleteResourceUnconditional(href).use { retry ->
                        if (retry.isSuccessful || retry.code == 404) {
                            db.tasks().deleteById(task.id)
                        } else {
                            val body = retry.body?.string().orEmpty()
                            errors += "DELETE ${task.summary}: HTTP ${retry.code} $body".trim()
                        }
                    }
                }
                else -> {
                    val body = response.body?.string().orEmpty()
                    errors += "DELETE ${task.summary}: HTTP ${response.code} $body".trim()
                }
            }
        }
    }

    private suspend fun pushPutTask(
        client: okhttp3.OkHttpClient,
        latest: TaskEntity,
        collection: CollectionEntity,
        errors: MutableList<String>,
        pushedUids: MutableSet<String>,
    ) {
        val ics = IcalMapper.buildTodoIcs(
            uid = latest.uid,
            summary = latest.summary,
            description = latest.description,
            status = latest.status ?: "NEEDS-ACTION",
            percentComplete = latest.percentComplete,
            priority = latest.priority,
            dtStartMillis = latest.dtStartMillis,
            dueMillis = latest.dueMillis,
            completedMillis = latest.completedMillis,
            categories = latest.categories,
            parentUid = latest.parentUid,
            linkedEventUid = latest.linkedEventUid,
            existingRaw = null,
            isCategory = latest.isCategory,
            sortOrder = latest.sortOrder,
        )
        val targetHref = MultistatusParser.joinUrl(collection.href, "${latest.uid}.ics")
        val oldHref = latest.href?.takeIf { it.isNotBlank() && !sameResource(it, targetHref) }
        if (oldHref != null) {
            // Task list changed — remove the object from the previous collection
            client.deleteResourceUnconditional(oldHref).use { /* ignore status */ }
        }
        val href = if (latest.href.isNullOrBlank() || oldHref != null) targetHref else latest.href
        val etagForPut = if (oldHref != null || latest.href.isNullOrBlank()) null else latest.etag
        client.putIcs(href, ics, etagForPut).use { response ->
            when {
                response.isSuccessful || response.code == 201 || response.code == 204 -> {
                    markTaskPushed(latest, href, response.header("ETag"), ics)
                    pushedUids += latest.uid
                }
                else -> {
                    // Create/update often fails on conditional headers; retry without them
                    val code = response.code
                    val firstBody = response.body?.string().orEmpty()
                    client.putIcsUnconditional(href, ics).use { retry ->
                        if (retry.isSuccessful || retry.code == 201 || retry.code == 204) {
                            markTaskPushed(latest, href, retry.header("ETag"), ics)
                            pushedUids += latest.uid
                        } else {
                            val body = retry.body?.string().orEmpty().ifBlank { firstBody }
                            errors += "PUT ${latest.summary}: HTTP ${retry.code} (was $code) $body".trim()
                        }
                    }
                }
            }
        }
    }

    private fun sameResource(a: String, b: String): Boolean {
        val na = a.trimEnd('/')
        val nb = b.trimEnd('/')
        return na.equals(nb, ignoreCase = true)
    }

    private suspend fun markTaskPushed(task: TaskEntity, href: String, etagHeader: String?, ics: String) {
        val newEtag = etagHeader?.trim()?.trim('"')
        // Only clear dirty / store ics if the row was not edited again during the push
        db.tasks().markPushed(
            id = task.id,
            href = href,
            etag = newEtag ?: task.etag,
            icsRaw = ics,
            pushedUpdatedAt = task.updatedAt,
        )
    }

    private suspend fun pushDirtyEvents(
        client: okhttp3.OkHttpClient,
        pushedUids: MutableSet<String>,
    ): List<String> {
        val errors = mutableListOf<String>()
        for (event in db.events().getDirty()) {
            try {
                val collection = db.collections().getById(event.collectionId)
                if (collection == null) {
                    errors += "Event ${event.uid}: collection missing"
                    continue
                }
                if (event.deleted) {
                    val href = event.href
                    if (href.isNullOrBlank()) {
                        db.events().deleteById(event.id)
                        continue
                    }
                    client.deleteResource(href, event.etag).use { response ->
                        when {
                            response.isSuccessful || response.code == 404 ->
                                db.events().deleteById(event.id)
                            response.code == 412 || response.code == 409 -> {
                                response.body?.close()
                                client.deleteResourceUnconditional(href).use { retry ->
                                    if (retry.isSuccessful || retry.code == 404) {
                                        db.events().deleteById(event.id)
                                    } else {
                                        errors += "DELETE event ${event.summary}: HTTP ${retry.code}"
                                    }
                                }
                            }
                            else -> errors += "DELETE event ${event.summary}: HTTP ${response.code}"
                        }
                    }
                    continue
                }
                val latest = db.events().getById(event.id) ?: continue
                val start = latest.dtStartMillis ?: System.currentTimeMillis()
                val end = latest.dtEndMillis ?: (start + 3_600_000)
                val ics = IcalMapper.buildEventIcs(
                    uid = latest.uid,
                    summary = latest.summary,
                    description = latest.description,
                    location = latest.location,
                    dtStartMillis = start,
                    dtEndMillis = end,
                    allDay = latest.allDay,
                )
                val href = latest.href ?: MultistatusParser.joinUrl(collection.href, "${latest.uid}.ics")
                client.putIcs(href, ics, latest.etag).use { response ->
                    when {
                        response.isSuccessful || response.code == 201 || response.code == 204 -> {
                            val newEtag = response.header("ETag")?.trim()?.trim('"')
                            db.events().markPushed(
                                id = latest.id,
                                href = href,
                                etag = newEtag ?: latest.etag,
                                icsRaw = ics,
                                pushedUpdatedAt = latest.updatedAt,
                            )
                            pushedUids += latest.uid
                        }
                        response.code == 412 -> {
                            response.body?.close()
                            client.putIcsUnconditional(href, ics).use { retry ->
                                if (retry.isSuccessful || retry.code == 201 || retry.code == 204) {
                                    val newEtag = retry.header("ETag")?.trim()?.trim('"')
                                    db.events().markPushed(
                                        id = latest.id,
                                        href = href,
                                        etag = newEtag ?: latest.etag,
                                        icsRaw = ics,
                                        pushedUpdatedAt = latest.updatedAt,
                                    )
                                    pushedUids += latest.uid
                                } else {
                                    errors += "PUT event ${latest.summary}: HTTP ${retry.code}"
                                }
                            }
                        }
                        else -> errors += "PUT event ${latest.summary}: HTTP ${response.code}"
                    }
                }
            } catch (e: Exception) {
                errors += "Event ${event.uid}: ${e.message ?: e.javaClass.simpleName}"
            }
        }
        return errors
    }

    private suspend fun pushDirtyNotes(
        client: okhttp3.OkHttpClient,
        pushedUids: MutableSet<String>,
    ): List<String> {
        val errors = mutableListOf<String>()
        for (note in db.notes().getDirty()) {
            try {
                val collection = db.collections().getById(note.collectionId)
                if (collection == null) {
                    errors += "Note ${note.uid}: collection missing"
                    continue
                }
                if (note.deleted) {
                    val href = note.href
                    if (href.isNullOrBlank()) {
                        db.notes().deleteById(note.id)
                        continue
                    }
                    client.deleteResource(href, note.etag).use { response ->
                        when {
                            response.isSuccessful || response.code == 404 ->
                                db.notes().deleteById(note.id)
                            response.code == 412 || response.code == 409 -> {
                                response.body?.close()
                                client.deleteResourceUnconditional(href).use { retry ->
                                    if (retry.isSuccessful || retry.code == 404) {
                                        db.notes().deleteById(note.id)
                                    } else {
                                        errors += "DELETE note ${note.summary}: HTTP ${retry.code}"
                                    }
                                }
                            }
                            else -> errors += "DELETE note ${note.summary}: HTTP ${response.code}"
                        }
                    }
                    continue
                }
                val latest = db.notes().getById(note.id) ?: continue
                val ics = IcalMapper.buildNoteIcs(
                    uid = latest.uid,
                    summary = latest.summary,
                    description = latest.description,
                    dtStartMillis = latest.dtStartMillis,
                    categories = latest.categories,
                )
                val href = latest.href ?: MultistatusParser.joinUrl(collection.href, "${latest.uid}.ics")
                client.putIcs(href, ics, latest.etag).use { response ->
                    when {
                        response.isSuccessful || response.code == 201 || response.code == 204 -> {
                            val newEtag = response.header("ETag")?.trim()?.trim('"')
                            db.notes().markPushed(
                                id = latest.id,
                                href = href,
                                etag = newEtag ?: latest.etag,
                                icsRaw = ics,
                                pushedUpdatedAt = latest.updatedAt,
                            )
                            pushedUids += latest.uid
                        }
                        response.code == 412 -> {
                            response.body?.close()
                            client.putIcsUnconditional(href, ics).use { retry ->
                                if (retry.isSuccessful || retry.code == 201 || retry.code == 204) {
                                    val newEtag = retry.header("ETag")?.trim()?.trim('"')
                                    db.notes().markPushed(
                                        id = latest.id,
                                        href = href,
                                        etag = newEtag ?: latest.etag,
                                        icsRaw = ics,
                                        pushedUpdatedAt = latest.updatedAt,
                                    )
                                    pushedUids += latest.uid
                                } else {
                                    errors += "PUT note ${latest.summary}: HTTP ${retry.code}"
                                }
                            }
                        }
                        else -> errors += "PUT note ${latest.summary}: HTTP ${response.code}"
                    }
                }
            } catch (e: Exception) {
                errors += "Note ${note.uid}: ${e.message ?: e.javaClass.simpleName}"
            }
        }
        return errors
    }
}
