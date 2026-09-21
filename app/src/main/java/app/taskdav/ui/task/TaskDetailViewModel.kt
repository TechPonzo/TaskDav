package app.taskdav.ui.task

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.taskdav.caldav.IcalMapper
import app.taskdav.data.CollectionEntity
import app.taskdav.data.EventEntity
import app.taskdav.data.TaskEntity
import app.taskdav.domain.TaskRepository
import app.taskdav.domain.TaskTreeBuilder
import app.taskdav.sync.CalDavSyncWorker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TaskDetailUiState(
    val task: TaskEntity? = null,
    val collection: CollectionEntity? = null,
    val linkedEvent: EventEntity? = null,
    val subtasks: List<TaskEntity> = emptyList(),
    val loading: Boolean = true,
    val deleted: Boolean = false,
    val showEditEvent: Boolean = false,
    val eventSummary: String = "",
    val eventStartMillis: Long = System.currentTimeMillis(),
    val eventEndMillis: Long = System.currentTimeMillis() + 3_600_000L,
    val eventLocation: String = "",
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TaskDetailViewModel(
    private val app: Application,
    private val repository: TaskRepository,
    private val taskId: Long,
) : ViewModel() {
    private val deleted = MutableStateFlow(false)
    private val edit = MutableStateFlow(EditEventUi())

    private data class EditEventUi(
        val show: Boolean = false,
        val summary: String = "",
        val startMillis: Long = System.currentTimeMillis(),
        val endMillis: Long = System.currentTimeMillis() + DEFAULT_EVENT_DURATION_MS,
        val location: String = "",
        val error: String? = null,
    )

    private data class CoreState(
        val task: TaskEntity?,
        val collection: CollectionEntity?,
        val linkedEvent: EventEntity?,
        val subtasks: List<TaskEntity>,
    )

    init {
        viewModelScope.launch {
            repository.ensureLinkedEvent(taskId)
        }
    }

    private val taskFlow = repository.observeTask(taskId)

    private val core: StateFlow<CoreState> = taskFlow
        .flatMapLatest { task ->
            val parentUid = task?.uid
            val childrenFlow = if (parentUid.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                repository.observeChildTasks(parentUid)
            }
            combine(
                repository.observeCollections(),
                repository.observeEvents(),
                childrenFlow,
            ) { collections, events, children ->
                val linkUid = IcalMapper.normalizeUid(task?.linkedEventUid)
                val linked = when {
                    linkUid.isNullOrBlank() -> null
                    else -> {
                        events.find { it.uid.equals(linkUid, ignoreCase = true) }
                            ?: task?.let { t ->
                                events.find {
                                    it.collectionId == t.collectionId &&
                                        it.summary.equals(t.summary, ignoreCase = true)
                                }
                            }
                    }
                }
                CoreState(
                    task = task,
                    collection = task?.let { t -> collections.find { it.id == t.collectionId } },
                    linkedEvent = linked,
                    subtasks = children.sortedWith(
                        compareBy(
                            { TaskTreeBuilder.isCompleted(it) },
                            { it.sortOrder },
                            { it.summary.lowercase() },
                        ),
                    ),
                )
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            CoreState(null, null, null, emptyList()),
        )

    val ui: StateFlow<TaskDetailUiState> = combine(core, deleted, edit) { snap, wasDeleted, editUi ->
        TaskDetailUiState(
            task = snap.task,
            collection = snap.collection,
            linkedEvent = snap.linkedEvent,
            subtasks = snap.subtasks,
            loading = false,
            deleted = wasDeleted,
            showEditEvent = editUi.show,
            eventSummary = editUi.summary,
            eventStartMillis = editUi.startMillis,
            eventEndMillis = editUi.endMillis,
            eventLocation = editUi.location,
            error = editUi.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskDetailUiState())

    fun startTask() {
        viewModelScope.launch {
            repository.startTask(taskId)
            repository.tryPushLocalChanges()
            CalDavSyncWorker.enqueueNow(app)
        }
    }

    fun endTask() {
        viewModelScope.launch {
            repository.endTask(taskId)
            repository.tryPushLocalChanges()
            CalDavSyncWorker.enqueueNow(app)
        }
    }

    fun convertKind() {
        viewModelScope.launch {
            val task = ui.value.task ?: return@launch
            if (!task.parentUid.isNullOrBlank()) return@launch
            repository.setIsCategory(taskId, !task.isCategory)
            repository.tryPushLocalChanges()
            CalDavSyncWorker.enqueueNow(app)
        }
    }

    fun deleteTask() {
        viewModelScope.launch {
            repository.deleteTask(taskId)
            repository.tryPushLocalChanges()
            CalDavSyncWorker.enqueueNow(app)
            deleted.value = true
        }
    }

    fun setShowEditEvent(show: Boolean) {
        if (!show) {
            edit.update { it.copy(show = false, error = null) }
            return
        }
        val event = ui.value.linkedEvent ?: return
        edit.update {
            it.copy(
                show = true,
                summary = event.summary,
                startMillis = event.dtStartMillis ?: System.currentTimeMillis(),
                endMillis = event.dtEndMillis
                    ?: ((event.dtStartMillis ?: System.currentTimeMillis()) + DEFAULT_EVENT_DURATION_MS),
                location = event.location.orEmpty(),
                error = null,
            )
        }
    }

    fun setEventSummary(v: String) = edit.update { it.copy(summary = v) }
    fun setEventLocation(v: String) = edit.update { it.copy(location = v) }
    fun setEventStart(millis: Long) = edit.update {
        it.copy(startMillis = millis, endMillis = millis + DEFAULT_EVENT_DURATION_MS)
    }
    fun setEventEnd(millis: Long) = edit.update {
        it.copy(endMillis = millis.coerceAtLeast(it.startMillis + MIN_EVENT_DURATION_MS))
    }

    fun saveLinkedEvent() {
        viewModelScope.launch {
            val event = ui.value.linkedEvent ?: return@launch
            val e = edit.value
            if (e.endMillis < e.startMillis) {
                edit.update { it.copy(error = "Event end must be after start") }
                return@launch
            }
            try {
                repository.updateEvent(
                    uid = event.uid,
                    summary = e.summary,
                    startMillis = e.startMillis,
                    endMillis = e.endMillis,
                    location = e.location,
                    description = event.description,
                )
                repository.tryPushLocalChanges()
                CalDavSyncWorker.enqueueNow(app)
                edit.update { it.copy(show = false, error = null) }
            } catch (ex: Exception) {
                edit.update { it.copy(error = ex.message) }
            }
        }
    }

    fun isCompleted(task: TaskEntity): Boolean = TaskTreeBuilder.isCompleted(task)
    fun isStarted(task: TaskEntity): Boolean = TaskTreeBuilder.isStarted(task)

    class Factory(
        private val app: Application,
        private val repository: TaskRepository,
        private val taskId: Long,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TaskDetailViewModel(app, repository, taskId) as T
        }
    }

    companion object {
        private const val DEFAULT_EVENT_DURATION_MS = 60 * 60 * 1000L
        private const val MIN_EVENT_DURATION_MS = 60 * 1000L
    }
}
