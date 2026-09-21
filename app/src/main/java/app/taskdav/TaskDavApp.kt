package app.taskdav

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import app.taskdav.caldav.SyncEngine
import app.taskdav.data.AccountStore
import app.taskdav.data.AppearanceStore
import app.taskdav.data.TaskDavDatabase
import app.taskdav.domain.TaskRepository
import app.taskdav.sync.CalDavSyncWorker

class TaskDavApp : Application() {
    lateinit var database: TaskDavDatabase
        private set
    lateinit var accountStore: AccountStore
        private set
    lateinit var appearanceStore: AppearanceStore
        private set
    lateinit var repository: TaskRepository
        private set

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        database = TaskDavDatabase.get(this)
        accountStore = AccountStore(this)
        appearanceStore = AppearanceStore(this)
        val syncEngine = SyncEngine(database, accountStore)
        repository = TaskRepository(database, accountStore, syncEngine)
        if (accountStore.isConfigured()) {
            CalDavSyncWorker.enqueuePeriodic(this)
            CalDavSyncWorker.enqueueNow(this)
        }
        registerReconnectSync()
    }

    /** When connectivity returns, push any dirty local edits and pull updates. */
    private fun registerReconnectSync() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (accountStore.isConfigured()) {
                    CalDavSyncWorker.enqueueNow(this@TaskDavApp)
                }
            }
        }
        networkCallback = callback
        runCatching { cm.registerNetworkCallback(request, callback) }
    }
}
