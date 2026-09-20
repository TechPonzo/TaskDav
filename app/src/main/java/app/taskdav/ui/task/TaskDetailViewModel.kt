package app.taskdav.ui.task

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.taskdav.data.CollectionEntity
import app.taskdav.data.TaskEntity
import app.taskdav.domain.TaskRepository
import app.taskdav.domain.TaskTreeBuilder
import app.taskdav.sync.CalDavSyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TaskDetailUiState(
    val task: TaskEntity? = null,
    val collection: CollectionEntity? = null,
    val linkedEventSummary: String? = null,
    val loading: Boolean = true,
    val deleted: Boolean = false,
)

class TaskDetailViewModel(
    private val app: Application,
    private val repository: TaskRepository,
    private val taskId: Long,
) : ViewModel() {
    private val deleted = MutableStateFlow(false)

    val ui: StateFlow<TaskDetailUiState> = combine(
        repository.observeTask(taskId),
        repository.observeCollections(),
        repository.observeEvents(),
        deleted,
    ) { task, collections, events, wasDeleted ->
        val linked = task?.linkedEventUid?.let { uid -> events.find { it.uid == uid } }
        TaskDetailUiState(
            task = task,
            collection = task?.let { t -> collections.find { it.id == t.collectionId } },
            linkedEventSummary = linked?.summary
                ?: task?.linkedEventUid?.takeIf { it.isNotBlank() }?.let { "Linked calendar event" },
            loading = false,
            deleted = wasDeleted,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskDetailUiState())

    fun startTask() {
        viewModelScope.launch {
            repository.startTask(taskId)
            CalDavSyncWorker.enqueueNow(app)
        }
    }

    fun endTask() {
        viewModelScope.launch {
            repository.endTask(taskId)
            CalDavSyncWorker.enqueueNow(app)
        }
    }

    fun deleteTask() {
        viewModelScope.launch {
            repository.deleteTask(taskId)
            CalDavSyncWorker.enqueueNow(app)
            deleted.value = true
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
}
