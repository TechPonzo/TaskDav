package app.taskdav.ui.tasks

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.taskdav.caldav.IcalMapper
import app.taskdav.data.CollectionEntity
import app.taskdav.data.EventEntity
import app.taskdav.domain.TaskNode
import app.taskdav.domain.TaskRepository
import app.taskdav.sync.CalDavSyncWorker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TasksUiState(
    val collectionFilter: Long? = null,
    val showCompleted: Boolean = true,
    val syncMessage: String? = null,
    val syncing: Boolean = false,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModel(
    private val app: Application,
    private val repository: TaskRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(
        TasksUiState(syncMessage = repository.lastSyncMessage()),
    )
    val ui: StateFlow<TasksUiState> = _ui.asStateFlow()

    val collections: StateFlow<List<CollectionEntity>> = repository.observeCollections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val events: StateFlow<List<EventEntity>> = repository.observeEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val taskForest: StateFlow<List<TaskNode>> = _ui
        .flatMapLatest { state ->
            repository.observeTaskForest(state.collectionFilter, state.showCompleted)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setCollectionFilter(id: Long?) {
        _ui.update { it.copy(collectionFilter = id) }
    }

    fun setShowCompleted(show: Boolean) {
        _ui.update { it.copy(showCompleted = show) }
    }

    fun syncNow() {
        viewModelScope.launch {
            _ui.update { it.copy(syncing = true, error = null) }
            try {
                val msg = repository.syncNow()
                _ui.update { it.copy(syncing = false, syncMessage = msg) }
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        syncing = false,
                        error = e.message,
                        syncMessage = repository.lastSyncMessage(),
                    )
                }
            }
        }
    }

    fun enqueueBackgroundSync() {
        CalDavSyncWorker.enqueueNow(app)
    }

    fun toggleComplete(taskId: Long) {
        viewModelScope.launch {
            repository.toggleComplete(taskId)
            CalDavSyncWorker.enqueueNow(app)
        }
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch {
            repository.deleteTask(taskId)
            CalDavSyncWorker.enqueueNow(app)
        }
    }

    fun persistOrder(flat: List<TaskNode>) {
        viewModelScope.launch {
            repository.persistFlatOrder(flat)
            CalDavSyncWorker.enqueueNow(app)
        }
    }

    fun newTaskUid(): String = IcalMapper.newUid()

    class Factory(
        private val app: Application,
        private val repository: TaskRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TasksViewModel(app, repository) as T
        }
    }
}
