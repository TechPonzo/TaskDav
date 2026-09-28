package app.taskdav.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import app.taskdav.data.EventEntity
import java.util.TimeZone

/**
 * Publishes TaskDav events into a phone [CalendarContract] calendar under a real
 * Android account ([TaskDavCalendarAccount]), so Google Calendar / AOSP Calendar list
 * TaskDav like DAVx⁵ — including for local-only users.
 */
class SystemCalendarMirror(
    private val context: Context,
) {
    fun hasPermission(): Boolean {
        val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
        val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
        return read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED
    }

    /** Ensures the TaskDav account + calendar exist; returns calendar row id. */
    fun ensureCalendarId(): Long? {
        if (!hasPermission()) return null
        TaskDavCalendarAccount.ensure(context)
        findCalendarId()?.let { return it }
        removeLegacyLocalCalendar()
        return createCalendar()
    }

    fun upsertEvent(event: EventEntity): Long? {
        if (!hasPermission()) return null
        val calId = ensureCalendarId() ?: return null
        val start = event.dtStartMillis ?: return null
        val end = event.dtEndMillis ?: (start + 60 * 60 * 1000L)
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calId)
            put(CalendarContract.Events.TITLE, event.summary)
            put(CalendarContract.Events.DESCRIPTION, event.description)
            put(CalendarContract.Events.EVENT_LOCATION, event.location)
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DTEND, end)
            put(CalendarContract.Events.ALL_DAY, if (event.allDay) 1 else 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.UID_2445, event.uid)
            val rule = event.rrule?.trim()?.takeIf { it.isNotEmpty() }
            if (rule != null) {
                put(CalendarContract.Events.RRULE, rule)
            } else {
                putNull(CalendarContract.Events.RRULE)
            }
        }

        val existingId = event.systemEventId?.takeIf { it > 0 }
            ?: findEventIdByUid(event.uid)
        if (existingId != null && existingId > 0) {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existingId)
            val updated = context.contentResolver.update(uri, values, null, null)
            if (updated > 0) return existingId
            // Stale id (e.g. after migrating off ACCOUNT_TYPE_LOCAL) — insert fresh.
        }
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: return null
        return ContentUris.parseId(uri)
    }

    fun deleteEvent(systemEventId: Long?) {
        if (systemEventId == null || systemEventId <= 0L) return
        if (!hasPermission()) return
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, systemEventId)
        runCatching { context.contentResolver.delete(uri, null, null) }
    }

    fun deleteByUid(uid: String) {
        if (!hasPermission()) return
        val id = findEventIdByUid(uid) ?: return
        deleteEvent(id)
    }

    private fun findCalendarId(): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection =
            "${CalendarContract.Calendars.ACCOUNT_NAME}=? AND ${CalendarContract.Calendars.ACCOUNT_TYPE}=?"
        val args = arrayOf(
            TaskDavCalendarAccount.ACCOUNT_NAME,
            TaskDavCalendarAccount.ACCOUNT_TYPE,
        )
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            args,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getLong(0)
        }
        return null
    }

    /**
     * Older builds used ACCOUNT_TYPE_LOCAL, which Google Calendar hides.
     * Remove that calendar so we only expose the account-backed one.
     */
    private fun removeLegacyLocalCalendar() {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection =
            "${CalendarContract.Calendars.ACCOUNT_NAME}=? AND ${CalendarContract.Calendars.ACCOUNT_TYPE}=?"
        val args = arrayOf(
            TaskDavCalendarAccount.ACCOUNT_NAME,
            CalendarContract.ACCOUNT_TYPE_LOCAL,
        )
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            args,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val uri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, id)
                    .buildUpon()
                    .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                    .appendQueryParameter(
                        CalendarContract.Calendars.ACCOUNT_NAME,
                        TaskDavCalendarAccount.ACCOUNT_NAME,
                    )
                    .appendQueryParameter(
                        CalendarContract.Calendars.ACCOUNT_TYPE,
                        CalendarContract.ACCOUNT_TYPE_LOCAL,
                    )
                    .build()
                runCatching { context.contentResolver.delete(uri, null, null) }
            }
        }
    }

    private fun createCalendar(): Long? {
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, TaskDavCalendarAccount.ACCOUNT_NAME)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, TaskDavCalendarAccount.ACCOUNT_TYPE)
            put(CalendarContract.Calendars.NAME, TaskDavCalendarAccount.ACCOUNT_NAME)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "TaskDav")
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF2E7D32.toInt())
            put(
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.CAL_ACCESS_OWNER,
            )
            put(CalendarContract.Calendars.OWNER_ACCOUNT, TaskDavCalendarAccount.ACCOUNT_NAME)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.CAN_ORGANIZER_RESPOND, 1)
            put(CalendarContract.Calendars.CAN_MODIFY_TIME_ZONE, 1)
            put(CalendarContract.Calendars.MAX_REMINDERS, 5)
            put(CalendarContract.Calendars.ALLOWED_REMINDERS, "0,1,2")
            put(CalendarContract.Calendars.ALLOWED_AVAILABILITY, "0,1,2")
            put(CalendarContract.Calendars.ALLOWED_ATTENDEE_TYPES, "0,1,2")
            put(
                CalendarContract.Calendars.CALENDAR_TIME_ZONE,
                TimeZone.getDefault().id,
            )
        }
        val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(
                CalendarContract.Calendars.ACCOUNT_NAME,
                TaskDavCalendarAccount.ACCOUNT_NAME,
            )
            .appendQueryParameter(
                CalendarContract.Calendars.ACCOUNT_TYPE,
                TaskDavCalendarAccount.ACCOUNT_TYPE,
            )
            .build()
        val result = context.contentResolver.insert(uri, values)
        if (result == null) {
            android.util.Log.e("TaskDavMirror", "Calendar insert returned null")
            return null
        }
        return ContentUris.parseId(result)
    }

    private fun findEventIdByUid(uid: String): Long? {
        val projection = arrayOf(CalendarContract.Events._ID)
        val selection = "${CalendarContract.Events.UID_2445}=?"
        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            arrayOf(uid),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getLong(0)
        }
        return null
    }

    companion object {
        const val ACCOUNT_NAME = TaskDavCalendarAccount.ACCOUNT_NAME
    }
}
