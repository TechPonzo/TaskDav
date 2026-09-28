package app.taskdav.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.util.concurrent.TimeUnit

/**
 * Reads events from the device [CalendarContract] (Google Calendar, DAVx⁵-synced
 * calendars, etc.) so TaskDav can show them without CalDAV — useful for local-only mode.
 *
 * Skips TaskDav's own mirrored calendar to avoid import loops.
 */
class SystemCalendarImport(
    private val context: Context,
) {
    data class SystemEvent(
        val systemEventId: Long,
        val calendarId: Long,
        val uid: String,
        val summary: String,
        val description: String?,
        val location: String?,
        val dtStartMillis: Long?,
        val dtEndMillis: Long?,
        val allDay: Boolean,
        val rrule: String?,
        val updatedAt: Long,
    )

    fun hasReadPermission(): Boolean {
        val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
        return read == PackageManager.PERMISSION_GRANTED
    }

    fun hasWritePermission(): Boolean {
        val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
        return write == PackageManager.PERMISSION_GRANTED
    }

    /** Visible calendars excluding TaskDav's local mirror calendar. */
    fun sourceCalendarIds(): Set<Long> {
        if (!hasReadPermission()) return emptySet()
        val ids = mutableSetOf<Long>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
        )
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            "${CalendarContract.Calendars.VISIBLE}=1",
            null,
            null,
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
            val nameIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
            val typeIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_TYPE)
            while (cursor.moveToNext()) {
                val accountName = cursor.getString(nameIdx).orEmpty()
                val accountType = cursor.getString(typeIdx).orEmpty()
                // Skip TaskDav's published calendar (account-backed or legacy local).
                if (accountName == TaskDavCalendarAccount.ACCOUNT_NAME &&
                    (accountType == TaskDavCalendarAccount.ACCOUNT_TYPE ||
                        accountType == CalendarContract.ACCOUNT_TYPE_LOCAL)
                ) {
                    continue
                }
                ids += cursor.getLong(idIdx)
            }
        }
        return ids
    }

    /**
     * Events from source calendars in a rolling window (plus recurring series that started earlier).
     */
    fun queryEvents(
        windowPastMs: Long = TimeUnit.DAYS.toMillis(365),
        windowFutureMs: Long = TimeUnit.DAYS.toMillis(730),
    ): List<SystemEvent> {
        if (!hasReadPermission()) return emptyList()
        val calendarIds = sourceCalendarIds()
        if (calendarIds.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()
        val windowStart = now - windowPastMs
        val windowEnd = now + windowFutureMs

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.UID_2445,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.DURATION,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.RRULE,
        )

        val idList = calendarIds.joinToString(",")
        val selection = buildString {
            append(CalendarContract.Events.DELETED).append("=0 AND ")
            append(CalendarContract.Events.CALENDAR_ID).append(" IN (").append(idList).append(") AND (")
            append("(")
            append(CalendarContract.Events.DTSTART).append(">=? AND ")
            append(CalendarContract.Events.DTSTART).append("<=?")
            append(") OR (")
            append(CalendarContract.Events.RRULE).append(" IS NOT NULL AND ")
            append(CalendarContract.Events.DTSTART).append("<=?")
            append("))")
        }
        val args = arrayOf(
            windowStart.toString(),
            windowEnd.toString(),
            windowEnd.toString(),
        )

        val out = mutableListOf<SystemEvent>()
        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            args,
            null,
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)
            val calIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_ID)
            val uidIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events.UID_2445)
            val titleIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
            val descIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)
            val locIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)
            val startIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
            val endIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
            val allDayIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)
            val rruleIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events.RRULE)

            while (cursor.moveToNext()) {
                val systemId = cursor.getLong(idIdx)
                val calendarId = cursor.getLong(calIdx)
                val uid2445 = cursor.getString(uidIdx)?.trim()?.takeIf { it.isNotEmpty() }
                val uid = uid2445 ?: "syscal-$calendarId-$systemId"
                val start = if (cursor.isNull(startIdx)) null else cursor.getLong(startIdx)
                val end = when {
                    cursor.isNull(endIdx) -> start?.let { it + TimeUnit.HOURS.toMillis(1) }
                    else -> cursor.getLong(endIdx)
                }
                out += SystemEvent(
                    systemEventId = systemId,
                    calendarId = calendarId,
                    uid = uid,
                    summary = cursor.getString(titleIdx)?.trim().orEmpty().ifBlank { "Untitled" },
                    description = cursor.getString(descIdx)?.trim()?.ifBlank { null },
                    location = cursor.getString(locIdx)?.trim()?.ifBlank { null },
                    dtStartMillis = start,
                    dtEndMillis = end,
                    allDay = cursor.getInt(allDayIdx) == 1,
                    rrule = cursor.getString(rruleIdx)?.trim()?.ifBlank { null },
                    updatedAt = System.currentTimeMillis(),
                )
            }
        }
        return out
    }

    fun updateSystemEvent(
        systemEventId: Long,
        summary: String,
        description: String?,
        location: String?,
        dtStartMillis: Long,
        dtEndMillis: Long,
        allDay: Boolean,
        rrule: String?,
    ): Boolean {
        if (!hasWritePermission() || systemEventId <= 0L) return false
        val values = ContentValues().apply {
            put(CalendarContract.Events.TITLE, summary)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.EVENT_LOCATION, location)
            put(CalendarContract.Events.DTSTART, dtStartMillis)
            put(CalendarContract.Events.DTEND, dtEndMillis)
            put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
            val rule = rrule?.trim()?.takeIf { it.isNotEmpty() }
            if (rule != null) put(CalendarContract.Events.RRULE, rule)
            else putNull(CalendarContract.Events.RRULE)
        }
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, systemEventId)
        return runCatching {
            context.contentResolver.update(uri, values, null, null) > 0
        }.getOrDefault(false)
    }

    fun deleteSystemEvent(systemEventId: Long?): Boolean {
        if (systemEventId == null || systemEventId <= 0L) return false
        if (!hasWritePermission()) return false
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, systemEventId)
        return runCatching {
            context.contentResolver.delete(uri, null, null) > 0
        }.getOrDefault(false)
    }
}
