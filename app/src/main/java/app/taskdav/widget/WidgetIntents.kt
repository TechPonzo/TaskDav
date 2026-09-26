package app.taskdav.widget

import android.content.Context
import android.content.Intent
import app.taskdav.MainActivity

object WidgetIntents {
    const val EXTRA_NAV_TAB = "app.taskdav.EXTRA_NAV_TAB"
    const val EXTRA_TASK_ID = "app.taskdav.EXTRA_TASK_ID"
    const val EXTRA_NOTE_ID = "app.taskdav.EXTRA_NOTE_ID"

    fun openTab(context: Context, tab: String): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_NAV_TAB, tab)
        }

    fun openTask(context: Context, taskId: Long): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_NAV_TAB, "tasks")
            putExtra(EXTRA_TASK_ID, taskId)
        }

    fun openNote(context: Context, noteId: Long): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_NAV_TAB, "notes")
            putExtra(EXTRA_NOTE_ID, noteId)
        }
}
