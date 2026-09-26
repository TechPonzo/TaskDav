package app.taskdav.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object WidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun updateAll(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            runCatching { updateAllNow(appContext) }
                .onFailure { Log.w("WidgetUpdater", "Failed to refresh widgets", it) }
        }
    }

    suspend fun updateAllNow(context: Context) {
        val appContext = context.applicationContext
        withContext(Dispatchers.Default) {
            CalendarWidget().updateAll(appContext)
            AgendaCalendarWidget().updateAll(appContext)
            TasksWidget().updateAll(appContext)
            NotesWidget().updateAll(appContext)
        }
    }
}
