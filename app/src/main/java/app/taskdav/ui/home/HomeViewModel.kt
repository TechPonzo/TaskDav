package app.taskdav.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.taskdav.data.CollectionEntity
import app.taskdav.data.NoteEntity
import app.taskdav.data.TaskEntity
import app.taskdav.domain.TaskRepository
import app.taskdav.ui.common.parseCategories
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class HomeFilters(
    val collectionId: Long? = null,
    val tag: String? = null,
)

data class HomeUiState(
    val filters: HomeFilters = HomeFilters(),
    val recentTasks: List<TaskEntity> = emptyList(),
    val recentNotes: List<NoteEntity> = emptyList(),
    val collections: List<CollectionEntity> = emptyList(),
    val availableTags: List<String> = emptyList(),
)

class HomeViewModel(
    repository: TaskRepository,
) : ViewModel() {
    private val filters = MutableStateFlow(HomeFilters())

    private val catalog = combine(
        repository.observeTasks(),
        repository.observeNotes(),
        repository.observeCollections(),
    ) { tasks, notes, collections ->
        Triple(tasks, notes, collections)
    }

    val ui: StateFlow<HomeUiState> = combine(filters, catalog) { f, catalog ->
        val (allTasks, allNotes, collections) = catalog
        val tags = (allTasks.flatMap { parseCategories(it.categories) } +
            allNotes.flatMap { parseCategories(it.categories) })
            .distinct()
            .sortedBy { it.lowercase() }

        fun TaskEntity.matches(): Boolean {
            if (f.collectionId != null && collectionId != f.collectionId) return false
            if (f.tag != null && f.tag !in parseCategories(categories)) return false
            return true
        }

        fun NoteEntity.matches(): Boolean {
            if (f.collectionId != null && collectionId != f.collectionId) return false
            if (f.tag != null && f.tag !in parseCategories(categories)) return false
            return true
        }

        HomeUiState(
            filters = f,
            recentTasks = allTasks
                .filter { it.matches() }
                .sortedByDescending { it.updatedAt }
                .take(8),
            recentNotes = allNotes
                .filter { it.matches() }
                .sortedByDescending { it.updatedAt }
                .take(8),
            collections = collections.filter { it.enabled },
            availableTags = tags,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setCollectionFilter(id: Long?) {
        filters.update { it.copy(collectionId = id) }
    }

    fun setTagFilter(tag: String?) {
        filters.update { it.copy(tag = tag) }
    }

    class Factory(
        private val repository: TaskRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(repository) as T
        }
    }
}
