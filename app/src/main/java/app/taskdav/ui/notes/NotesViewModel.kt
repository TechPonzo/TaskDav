package app.taskdav.ui.notes

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.taskdav.caldav.IcalMapper
import app.taskdav.data.CollectionEntity
import app.taskdav.data.NoteEntity
import app.taskdav.domain.TaskRepository
import app.taskdav.sync.CalDavSyncWorker
import app.taskdav.ui.common.joinCategories
import app.taskdav.ui.common.parseCategories
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotesUiState(
    val collectionFilter: Long? = null,
    val tagFilter: String? = null,
    val availableTags: List<String> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val syncing: Boolean = false,
    val error: String? = null,
    val syncMessage: String? = null,
)

data class NoteEditorState(
    val id: Long? = null,
    val uid: String = IcalMapper.newUid(),
    val collectionId: Long = 0,
    val summary: String = "",
    val description: String = "",
    val categories: String? = null,
    val ready: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
)

private data class NotesFilters(
    val collectionFilter: Long? = null,
    val tagFilter: String? = null,
    val syncing: Boolean = false,
    val error: String? = null,
    val syncMessage: String? = null,
)

class NotesViewModel(
    private val app: Application,
    private val repository: TaskRepository,
) : ViewModel() {
    private val filters = MutableStateFlow(
        NotesFilters(syncMessage = repository.lastSyncMessage()),
    )

    val ui: StateFlow<NotesUiState> = combine(
        filters,
        repository.observeNotes(),
    ) { f, allNotes ->
        val scoped = allNotes.filter {
            f.collectionFilter == null || it.collectionId == f.collectionFilter
        }
        val tags = scoped.flatMap { parseCategories(it.categories) }
            .distinct()
            .sortedBy { it.lowercase() }
        val tagFilter = f.tagFilter?.takeIf { it in tags }
        val notes = allNotes.filter { note ->
            (f.collectionFilter == null || note.collectionId == f.collectionFilter) &&
                (tagFilter == null || tagFilter in parseCategories(note.categories))
        }.sortedByDescending { it.updatedAt }

        NotesUiState(
            collectionFilter = f.collectionFilter,
            tagFilter = tagFilter,
            availableTags = tags,
            notes = notes,
            syncing = f.syncing,
            error = f.error,
            syncMessage = f.syncMessage,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        NotesUiState(syncMessage = repository.lastSyncMessage()),
    )

    val collections: StateFlow<List<CollectionEntity>> = repository.observeCollections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setCollectionFilter(id: Long?) {
        filters.update { it.copy(collectionFilter = id) }
    }

    fun setTagFilter(tag: String?) {
        filters.update { it.copy(tagFilter = tag) }
    }

    fun syncNow() {
        viewModelScope.launch {
            filters.update { it.copy(syncing = true, error = null) }
            try {
                val msg = repository.syncNow()
                filters.update { it.copy(syncing = false, syncMessage = msg) }
            } catch (e: Exception) {
                filters.update { it.copy(syncing = false, error = e.message) }
            }
        }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch {
            repository.deleteNote(id)
            CalDavSyncWorker.enqueueNow(app)
        }
    }

    fun enqueueSync() = CalDavSyncWorker.enqueueNow(app)

    class Factory(
        private val app: Application,
        private val repository: TaskRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NotesViewModel(app, repository) as T
        }
    }
}

class NoteEditorViewModel(
    private val app: Application,
    private val repository: TaskRepository,
    private val noteId: Long?,
) : ViewModel() {
    private val _state = MutableStateFlow(NoteEditorState())
    val state: StateFlow<NoteEditorState> = _state.asStateFlow()

    val collections: StateFlow<List<CollectionEntity>> = repository.observeCollections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val knownTags: StateFlow<List<String>> = combine(
        repository.observeTasks(),
        repository.observeNotes(),
    ) { tasks, notes ->
        (tasks.flatMap { parseCategories(it.categories) } +
            notes.flatMap { parseCategories(it.categories) })
            .distinct()
            .sortedBy { it.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            if (noteId != null) {
                val note = repository.getNote(noteId)
                if (note == null) {
                    _state.update {
                        it.copy(ready = true, error = "Note not found")
                    }
                    return@launch
                }
                _state.update {
                    it.copy(
                        id = note.id,
                        uid = note.uid,
                        collectionId = note.collectionId,
                        summary = note.summary,
                        description = note.description.orEmpty(),
                        categories = note.categories,
                        ready = true,
                    )
                }
            } else {
                val col = repository.getCollections()
                    .firstOrNull { it.enabled && it.supportsVjournal }
                    ?: repository.getCollections().firstOrNull()
                _state.update {
                    it.copy(
                        collectionId = col?.id ?: 0L,
                        uid = IcalMapper.newUid(),
                        ready = true,
                    )
                }
            }
        }
    }

    fun updateSummary(v: String) = _state.update { it.copy(summary = v) }
    fun updateDescription(v: String) = _state.update { it.copy(description = v) }
    fun updateCollection(id: Long) = _state.update { it.copy(collectionId = id) }
    fun updateCategories(c: String?) = _state.update { it.copy(categories = c) }

    /** @param pendingTag draft text from the tag field that was not yet added via Add */
    fun save(pendingTag: String = "") {
        viewModelScope.launch {
            val s = _state.value
            if (!s.ready || s.saving) return@launch
            if (noteId != null && s.id == null) return@launch
            val next = pendingTag.trim()
            val categories = if (next.isEmpty()) {
                s.categories
            } else {
                joinCategories(parseCategories(s.categories) + next)
            }
            _state.update { it.copy(categories = categories, saving = true, error = null) }
            try {
                repository.createOrUpdateNote(
                    id = s.id ?: noteId,
                    uid = s.uid,
                    collectionId = s.collectionId,
                    summary = s.summary,
                    description = s.description,
                    categories = categories,
                )
                CalDavSyncWorker.enqueueNow(app)
                _state.update { it.copy(saving = false, saved = true, error = null) }
                repository.tryPushLocalChanges()
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, error = e.message) }
            }
        }
    }

    class Factory(
        private val app: Application,
        private val repository: TaskRepository,
        private val noteId: Long?,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NoteEditorViewModel(app, repository, noteId) as T
        }
    }
}
