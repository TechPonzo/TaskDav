package app.taskdav

import android.app.Application
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

    override fun onCreate() {
        super.onCreate()
        database = TaskDavDatabase.get(this)
        accountStore = AccountStore(this)
        appearanceStore = AppearanceStore(this)
        val syncEngine = SyncEngine(database, accountStore)
        repository = TaskRepository(database, accountStore, syncEngine)
        if (accountStore.isConfigured()) {
            CalDavSyncWorker.enqueuePeriodic(this)
        }
    }
}
