package app.taskdav

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import app.taskdav.calendar.CalendarIntentHandler
import app.taskdav.calendar.PendingEventCompose
import app.taskdav.calendar.SystemCalendarMirror
import app.taskdav.caldav.SyncEngine
import app.taskdav.data.AccountStore
import app.taskdav.data.AppearanceStore
import app.taskdav.data.SyncBackend
import app.taskdav.data.TaskDavDatabase
import app.taskdav.domain.TaskRepository
import app.taskdav.sync.CalDavSyncWorker
import app.taskdav.widget.WidgetIntents
import app.taskdav.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

data class WidgetNavigation(
    val tab: String,
    val taskId: Long? = null,
    val noteId: Long? = null,
)

class TaskDavApp : Application() {
    lateinit var database: TaskDavDatabase
        private set
    lateinit var accountStore: AccountStore
        private set
    lateinit var appearanceStore: AppearanceStore
        private set
    lateinit var repository: TaskRepository
        private set
    lateinit var systemCalendarMirror: SystemCalendarMirror
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val _syncBackend = MutableStateFlow(SyncBackend.LOCAL)
    val syncBackend: StateFlow<SyncBackend> = _syncBackend.asStateFlow()

    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _pendingEventCompose = MutableSharedFlow<PendingEventCompose>(
        replay = 0,
        extraBufferCapacity = 8,
    )
    val pendingEventCompose: SharedFlow<PendingEventCompose> = _pendingEventCompose.asSharedFlow()

    @Volatile
    private var stickyPendingEvent: PendingEventCompose? = null

    private val _navigateToCalendar = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val navigateToCalendar: SharedFlow<Unit> = _navigateToCalendar.asSharedFlow()

    private val _widgetNavigation = MutableSharedFlow<WidgetNavigation>(extraBufferCapacity = 8)
    val widgetNavigation: SharedFlow<WidgetNavigation> = _widgetNavigation.asSharedFlow()

    private val _phoneCalendarMirror = MutableStateFlow(false)
    val phoneCalendarMirror: StateFlow<Boolean> = _phoneCalendarMirror.asStateFlow()

    fun notifySyncBackendChanged() {
        _syncBackend.value = accountStore.syncBackend()
    }

    fun notifyPhoneCalendarMirrorChanged() {
        _phoneCalendarMirror.value = accountStore.phoneCalendarMirrorEnabled()
    }

    fun notifyWidgetsChanged() {
        WidgetUpdater.updateAll(this)
    }

    fun offerPendingEventCompose(pending: PendingEventCompose) {
        stickyPendingEvent = pending
        _pendingEventCompose.tryEmit(pending)
    }

    /** Consume a sticky pending compose once (for ViewModels that start after the intent). */
    fun takeStickyPendingEvent(): PendingEventCompose? {
        val value = stickyPendingEvent
        stickyPendingEvent = null
        return value
    }

    fun consumeCalendarIntent(intent: Intent?): Boolean {
        val pending = CalendarIntentHandler.fromIntent(contentResolver, intent) ?: return false
        offerPendingEventCompose(pending)
        _navigateToCalendar.tryEmit(Unit)
        return true
    }

    /** Parse widget extras without enqueueing (cold start). */
    fun parseWidgetIntent(intent: Intent?): WidgetNavigation? {
        val tab = intent?.getStringExtra(WidgetIntents.EXTRA_NAV_TAB) ?: return null
        val taskId = intent.getLongExtra(WidgetIntents.EXTRA_TASK_ID, -1L).takeIf { it >= 0 }
        val noteId = intent.getLongExtra(WidgetIntents.EXTRA_NOTE_ID, -1L).takeIf { it >= 0 }
        return WidgetNavigation(tab = tab, taskId = taskId, noteId = noteId)
    }

    /** Enqueue widget navigation for an already-running activity (warm start). */
    fun offerWidgetNavigation(nav: WidgetNavigation) {
        _widgetNavigation.tryEmit(nav)
    }

    override fun onCreate() {
        super.onCreate()
        database = TaskDavDatabase.get(this)
        accountStore = AccountStore(this)
        appearanceStore = AppearanceStore(this)
        systemCalendarMirror = SystemCalendarMirror(this)
        val syncEngine = SyncEngine(database, accountStore)
        repository = TaskRepository(
            database,
            accountStore,
            syncEngine,
            this,
            systemCalendarMirror,
        )
        _syncBackend.value = accountStore.syncBackend()
        _phoneCalendarMirror.value = accountStore.phoneCalendarMirrorEnabled()
        when (accountStore.syncBackend()) {
            SyncBackend.LOCAL -> {
                CalDavSyncWorker.cancelAll(this)
                appScope.launch { repository.ensureLocalWorkspace() }
            }
            SyncBackend.CALDAV -> {
                if (accountStore.isConfigured()) {
                    CalDavSyncWorker.enqueuePeriodic(this)
                    CalDavSyncWorker.enqueueNow(this)
                }
            }
        }
        registerReconnectSync()
        observeSeedForWidgets()
    }

    private fun observeSeedForWidgets() {
        appScope.launch {
            appearanceStore.seedColorArgb
                .distinctUntilChanged()
                .collect {
                    WidgetUpdater.updateAllNow(this@TaskDavApp)
                }
        }
    }

    /** When connectivity returns, push any dirty local edits and pull updates. */
    private fun registerReconnectSync() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        fun refreshOnline(caps: NetworkCapabilities? = cm.getNetworkCapabilities(cm.activeNetwork)) {
            _isOnline.value = caps != null &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
        refreshOnline()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                refreshOnline(cm.getNetworkCapabilities(network))
                if (accountStore.isCalDavMode() && accountStore.isConfigured()) {
                    CalDavSyncWorker.enqueueNow(this@TaskDavApp)
                }
            }

            override fun onLost(network: Network) {
                // Default network may have switched; re-check active.
                refreshOnline()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                refreshOnline(networkCapabilities)
            }

            override fun onUnavailable() {
                _isOnline.value = false
            }
        }
        networkCallback = callback
        // Default-network callback reliably reports the network apps actually use.
        runCatching { cm.registerDefaultNetworkCallback(callback) }
            .onFailure {
                runCatching {
                    cm.registerNetworkCallback(
                        NetworkRequest.Builder()
                            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                            .build(),
                        callback,
                    )
                }
            }
    }
}
