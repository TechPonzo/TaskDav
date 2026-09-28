package app.taskdav.ui.setup

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.taskdav.R
import app.taskdav.TaskDavApp
import app.taskdav.caldav.CalDavException
import app.taskdav.data.AccountCredentials
import app.taskdav.data.CollectionEntity
import app.taskdav.data.SyncBackend
import app.taskdav.domain.TaskRepository
import app.taskdav.sync.CalDavSyncWorker
import java.io.IOException
import java.net.UnknownHostException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SetupUiState(
    val syncBackend: SyncBackend = SyncBackend.LOCAL,
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
    private val _state = MutableStateFlow(
        SetupUiState(syncBackend = repository.syncBackend()),
    )
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
            if (cols.isNotEmpty() && repository.isCalDavMode()) {
                _state.value = _state.value.copy(discovered = true)
            }
        }
    }

    fun selectSyncBackend(backend: SyncBackend) {
        repository.setSyncBackend(backend)
        (app as? TaskDavApp)?.notifySyncBackendChanged()
        _state.value = _state.value.copy(
            syncBackend = backend,
            error = null,
            message = null,
        )
    }

    fun updateUrl(v: String) { _state.value = _state.value.copy(baseUrl = v, error = null) }
    fun updateUser(v: String) { _state.value = _state.value.copy(username = v, error = null) }
    fun updatePassword(v: String) { _state.value = _state.value.copy(password = v, error = null) }

    fun useLocalStorage(onReady: () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null, message = null)
            try {
                repository.setSyncBackend(SyncBackend.LOCAL)
                repository.ensureLocalWorkspace()
                CalDavSyncWorker.cancelAll(app)
                (app as? TaskDavApp)?.notifySyncBackendChanged()
                _state.value = _state.value.copy(
                    busy = false,
                    syncBackend = SyncBackend.LOCAL,
                    message = "Saving on this device only.",
                )
                onReady()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    busy = false,
                    error = e.message ?: e.javaClass.simpleName,
                )
            }
        }
    }

    fun saveAndDiscover() {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null, message = null)
            try {
                repository.setSyncBackend(SyncBackend.CALDAV)
                val creds = AccountCredentials(
                    baseUrl = _state.value.baseUrl.trim(),
                    username = _state.value.username.trim(),
                    password = _state.value.password,
                )
                repository.saveAccount(creds)
                val msg = repository.discoverAndSave()
                CalDavSyncWorker.enqueuePeriodic(app)
                CalDavSyncWorker.enqueueNow(app)
                (app as? TaskDavApp)?.notifySyncBackendChanged()
                _state.value = _state.value.copy(
                    busy = false,
                    syncBackend = SyncBackend.CALDAV,
                    discovered = true,
                    message = msg,
                    error = null,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    busy = false,
                    discovered = false,
                    message = null,
                    error = friendlyCalDavError(e),
                )
            }
        }
    }

    private fun friendlyCalDavError(e: Exception): String {
        val caldav = e as? CalDavException
            ?: (e.cause as? CalDavException)
        return when {
            caldav?.isAuthFailure == true ->
                app.getString(R.string.caldav_error_auth)
            caldav?.message?.contains("Invalid URL", ignoreCase = true) == true ->
                app.getString(R.string.caldav_error_invalid_url)
            e is UnknownHostException || e.cause is UnknownHostException ->
                app.getString(R.string.caldav_error_network)
            e is IOException || e.cause is IOException ->
                app.getString(R.string.caldav_error_network)
            !caldav?.message.isNullOrBlank() ->
                caldav!!.message!!
            !e.message.isNullOrBlank() ->
                e.message!!
            else ->
                app.getString(R.string.caldav_error_generic)
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
