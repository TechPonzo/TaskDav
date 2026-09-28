package app.taskdav.calendar

import android.app.Service
import android.content.Intent
import android.os.IBinder

class TaskDavCalendarSyncService : Service() {
    override fun onCreate() {
        synchronized(lock) {
            if (adapter == null) {
                adapter = TaskDavCalendarSyncAdapter(applicationContext, true)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        synchronized(lock) {
            return adapter!!.syncAdapterBinder
        }
    }

    companion object {
        private val lock = Any()
        private var adapter: TaskDavCalendarSyncAdapter? = null
    }
}
