package app.taskdav.data

import android.content.Context

/**
 * Local-only UI state: which category folders are collapsed in the Tasks list.
 * Not synced to CalDAV.
 */
class CategoryCollapseStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun collapsedUids(): Set<String> =
        prefs.getStringSet(KEY_COLLAPSED, emptySet())?.toSet().orEmpty()

    fun setCollapsedUids(uids: Set<String>) {
        prefs.edit().putStringSet(KEY_COLLAPSED, uids.toSet()).apply()
    }

    fun toggle(uid: String): Set<String> {
        val next = collapsedUids().toMutableSet()
        if (!next.add(uid)) next.remove(uid)
        setCollapsedUids(next)
        return next
    }

    companion object {
        private const val PREFS = "taskdav_task_ui"
        private const val KEY_COLLAPSED = "collapsed_category_uids"
    }
}
