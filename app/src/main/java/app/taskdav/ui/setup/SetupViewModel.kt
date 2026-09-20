package app.taskdav.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.taskdav.domain.TaskRepository
import app.taskdav.data.AccountCredentials
import app.taskdav.data.CollectionEntity
import app.taskdav.sync.CalDavSyncWorker
import android.app.Application
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SetupUiState(
    val baseUrl: String = "",
    val username: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val discovered: Boolean = false,
)

class SetupViewModel(
    private val app: Application,
    private val repository: TaskRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    val collections: StateFlow<List<CollectionEntity>> = repository.observeCollections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        repository.loadAccount()?.let { creds ->
            _state.value = _state.value.copy(
                baseUrl = creds.baseUrl,
                username = creds.username,
                password = creds.password,
            )
        }
        viewModelScope.launch {
            val cols = repository.getCollections()
            if (cols.isNotEmpty()) {
                _state.value = _state.value.copy(discovered = true)
            }
        }
    }

    fun updateUrl(v: String) { _state.value = _state.value.copy(baseUrl = v, error = null) }
    fun updateUser(v: String) { _state.value = _state.value.copy(username = v, error = null) }
    fun updatePassword(v: String) { _state.value = _state.value.copy(password = v, error = null) }

    fun saveAndDiscover() {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null, message = null)
            try {
                val creds = AccountCredentials(
                    baseUrl = _state.value.baseUrl.trim(),
                    username = _state.value.username.trim(),
                    password = _state.value.password,
                )
                repository.saveAccount(creds)
                val msg = repository.discoverAndSave()
                CalDavSyncWorker.enqueuePeriodic(app)
                CalDavSyncWorker.enqueueNow(app)
                _state.value = _state.value.copy(
                    busy = false,
                    discovered = true,
                    message = msg,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    busy = false,
                    error = e.message ?: e.javaClass.simpleName,
                )
            }
        }
    }

    fun toggleCollection(id: Long, enabled: Boolean) {
        viewModelScope.launch { repository.setCollectionEnabled(id, enabled) }
    }

    class Factory(
        private val app: Application,
        private val repository: TaskRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SetupViewModel(app, repository) as T
        }
    }
}
