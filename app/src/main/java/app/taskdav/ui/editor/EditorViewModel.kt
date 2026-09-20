package app.taskdav.ui.editor

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.taskdav.caldav.IcalMapper
import app.taskdav.data.CollectionEntity
import app.taskdav.data.EventEntity
import app.taskdav.domain.TaskEditorState
import app.taskdav.domain.TaskRepository
import app.taskdav.sync.CalDavSyncWorker
import app.taskdav.ui.common.joinCategories
import app.taskdav.ui.common.parseCategories
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorUiState(
    val editor: TaskEditorState? = null,
    val ready: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val showEventPicker: Boolean = false,
    val showCreateEvent: Boolean = false,
    val eventStartMillis: Long = System.currentTimeMillis(),
    val eventEndMillis: Long = System.currentTimeMillis() + 3_600_000,
    val eventCollectionId: Long? = null,
    val saved: Boolean = false,
)

class EditorViewModel(
    private val app: Application,
    private val repository: TaskRepository,
    private val taskId: Long?,
    private val parentUid: String?,
    private val presetCollectionId: Long?,
    private val presetIsCategory: Boolean,
) : ViewModel() {
    private val _ui = MutableStateFlow(EditorUiState())
    val ui: StateFlow<EditorUiState> = _ui.asStateFlow()

    val collections: StateFlow<List<CollectionEntity>> = repository.observeCollections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val events: StateFlow<List<EventEntity>> = repository.observeEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val cols = repository.getCollections().filter { it.enabled && it.supportsVtodo }
        if (taskId != null) {
            val task = repository.getTask(taskId)
            if (task == null) {
                _ui.update { it.copy(ready = true, error = "Task not found") }
                return
            }
            val linked = task.linkedEventUid?.let { repository.getEventByUid(it) }
            _ui.update {
                it.copy(
                    editor = TaskEditorState(
                        id = task.id,
                        uid = task.uid,
                        collectionId = task.collectionId,
                        summary = task.summary,
                        description = task.description.orEmpty(),
                        status = task.status,
                        percentComplete = task.percentComplete,
                        priority = task.priority,
                        dueMillis = task.dueMillis,
                        categories = task.categories,
                        parentUid = task.parentUid,
                        linkedEventUid = task.linkedEventUid,
                        linkedEvent = linked,
                        isCategory = task.isCategory,
                    ),
                    eventCollectionId = repository.getCollections()
                        .firstOrNull { c -> c.enabled && c.supportsVevent }?.id,
                    ready = true,
                )
            }
        } else {
            val collectionId = presetCollectionId
                ?: cols.firstOrNull()?.id
                ?: repository.getCollections().firstOrNull()?.id
                ?: 0L
            val asCategory = presetIsCategory && parentUid.isNullOrBlank()
            _ui.update {
                it.copy(
                    editor = TaskEditorState(
                        uid = IcalMapper.newUid(),
                        collectionId = collectionId,
                        parentUid = parentUid,
                        isCategory = asCategory,
                    ),
                    eventCollectionId = repository.getCollections()
                        .firstOrNull { c -> c.enabled && c.supportsVevent }?.id,
                    ready = true,
                )
            }
        }
    }

    fun updateSummary(v: String) = updateEditor { it.copy(summary = v) }
    fun updateDescription(v: String) = updateEditor { it.copy(description = v) }
    fun updateCollection(id: Long) = updateEditor { it.copy(collectionId = id) }
    fun updateParent(uid: String?) = updateEditor { it.copy(parentUid = uid) }
    fun updatePriority(p: Int) = updateEditor { it.copy(priority = p) }
    fun updateDue(millis: Long?) = updateEditor { it.copy(dueMillis = millis) }
    fun updateCategories(categories: String?) = updateEditor { it.copy(categories = categories) }

    fun setIsCategory(isCategory: Boolean) {
        updateEditor { editor ->
            if (editor.id != null || !editor.parentUid.isNullOrBlank()) editor
            else editor.copy(
                isCategory = isCategory,
                dueMillis = if (isCategory) null else editor.dueMillis,
                priority = if (isCategory) 0 else editor.priority,
                linkedEventUid = if (isCategory) null else editor.linkedEventUid,
                linkedEvent = if (isCategory) null else editor.linkedEvent,
            )
        }
    }

    fun setShowEventPicker(show: Boolean) = _ui.update { it.copy(showEventPicker = show) }
    fun setShowCreateEvent(show: Boolean) = _ui.update { it.copy(showCreateEvent = show) }
    fun setEventStart(millis: Long) = _ui.update { state ->
        val end = state.eventEndMillis.coerceAtLeast(millis + 60_000)
        state.copy(eventStartMillis = millis, eventEndMillis = end)
    }
    fun setEventEnd(millis: Long) = _ui.update { state ->
        state.copy(eventEndMillis = millis.coerceAtLeast(state.eventStartMillis + 60_000))
    }
    fun setEventTimes(start: Long, end: Long) =
        _ui.update { it.copy(eventStartMillis = start, eventEndMillis = end.coerceAtLeast(start + 60_000)) }
    fun setEventCollection(id: Long) = _ui.update { it.copy(eventCollectionId = id) }

    fun clearLinkedEvent() {
        updateEditor { it.copy(linkedEventUid = null, linkedEvent = null) }
    }

    fun pickEvent(event: EventEntity) {
        updateEditor { it.copy(linkedEventUid = event.uid, linkedEvent = event) }
        _ui.update { it.copy(showEventPicker = false) }
    }

    fun createAndLinkEvent() {
        viewModelScope.launch {
            val state = _ui.value
            if (!state.ready || state.saving) return@launch
            val editor = state.editor ?: return@launch
            val collectionId = state.eventCollectionId ?: return@launch
            if (state.eventEndMillis < state.eventStartMillis) {
                _ui.update { it.copy(error = "Event end must be after start") }
                return@launch
            }
            try {
                val id = repository.createOrUpdateTask(editor)
                val uid = repository.createLinkedEvent(
                    taskId = id,
                    collectionId = collectionId,
                    summary = editor.summary,
                    startMillis = state.eventStartMillis,
                    endMillis = state.eventEndMillis,
                )
                val event = repository.getEventByUid(uid)
                CalDavSyncWorker.enqueueNow(app)
                _ui.update {
                    it.copy(
                        editor = editor.copy(id = id, linkedEventUid = uid, linkedEvent = event),
                        showCreateEvent = false,
                        error = null,
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(error = e.message) }
            }
        }
    }

    /** @param pendingTag draft text from the tag field that was not yet added via Add */
    fun save(pendingTag: String = "") {
        viewModelScope.launch {
            val state = _ui.value
            if (!state.ready || state.saving) return@launch
            val editor = state.editor ?: return@launch
            if (taskId != null && editor.id == null) return@launch
            val withTag = mergePendingTag(editor, pendingTag)
            _ui.update { it.copy(editor = withTag, saving = true, error = null) }
            try {
                val id = repository.createOrUpdateTask(
                    withTag.copy(id = withTag.id ?: taskId),
                )
                // Push to Radicale now (don't only queue WorkManager — that can lag or fail silently)
                val syncMsg = repository.syncNow()
                val stillDirty = repository.getTask(id)?.dirty == true
                if (stillDirty) {
                    _ui.update {
                        it.copy(
                            saving = false,
                            error = syncMsg.ifBlank { "Saved on device, but upload to server failed" },
                        )
                    }
                } else {
                    _ui.update { it.copy(saving = false, saved = true) }
                }
            } catch (e: Exception) {
                _ui.update { it.copy(saving = false, error = e.message) }
            }
        }
    }

    private fun mergePendingTag(editor: TaskEditorState, pendingTag: String): TaskEditorState {
        val next = pendingTag.trim()
        if (next.isEmpty()) return editor
        val tags = parseCategories(editor.categories)
        return editor.copy(categories = joinCategories(tags + next))
    }

    private fun updateEditor(block: (TaskEditorState) -> TaskEditorState) {
        _ui.update { state ->
            val editor = state.editor ?: return@update state
            state.copy(editor = block(editor))
        }
    }

    class Factory(
        private val app: Application,
        private val repository: TaskRepository,
        private val taskId: Long?,
        private val parentUid: String?,
        private val presetCollectionId: Long?,
        private val presetIsCategory: Boolean = false,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return EditorViewModel(
                app,
                repository,
                taskId,
                parentUid,
                presetCollectionId,
                presetIsCategory,
            ) as T
        }
    }
}
