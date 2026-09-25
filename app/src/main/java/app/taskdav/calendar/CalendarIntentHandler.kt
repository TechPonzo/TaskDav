package app.taskdav.calendar

import android.content.ContentResolver
import android.content.Intent
import android.provider.CalendarContract
import app.taskdav.caldav.IcalMapper

/**
 * Prefill data for creating an event from an external INSERT/VIEW/EDIT intent.
 */
data class PendingEventCompose(
    val title: String = "",
    val description: String = "",
    val location: String = "",
    val beginMillis: Long? = null,
    val endMillis: Long? = null,
)

object CalendarIntentHandler {
    fun fromIntent(resolver: ContentResolver, intent: Intent?): PendingEventCompose? {
        if (intent == null) return null
        val action = intent.action ?: return null
        if (action != Intent.ACTION_INSERT &&
            action != Intent.ACTION_EDIT &&
            action != Intent.ACTION_VIEW
        ) {
            return null
        }

        val ics = readIcsFromUri(resolver, intent)
        if (!ics.isNullOrBlank()) {
            val parsed = runCatching { IcalMapper.parseEvents(ics) }.getOrNull()?.firstOrNull()
            if (parsed != null) {
                return PendingEventCompose(
                    title = parsed.summary,
                    description = parsed.description.orEmpty(),
                    location = parsed.location.orEmpty(),
                    beginMillis = parsed.dtStartMillis,
                    endMillis = parsed.dtEndMillis,
                )
            }
        }

        val begin = intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1L)
            .takeIf { it > 0 }
            ?: intent.getLongExtra("beginTime", -1L).takeIf { it > 0 }
        val end = intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, -1L)
            .takeIf { it > 0 }
            ?: intent.getLongExtra("endTime", -1L).takeIf { it > 0 }

        val title = intent.getStringExtra(Intent.EXTRA_TITLE)
            ?: intent.getStringExtra("title")
            ?: intent.getStringExtra(CalendarContract.Events.TITLE)
            ?: ""
        val description = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent.getStringExtra("description")
            ?: intent.getStringExtra(CalendarContract.Events.DESCRIPTION)
            ?: ""
        val location = intent.getStringExtra("eventLocation")
            ?: intent.getStringExtra(CalendarContract.Events.EVENT_LOCATION)
            ?: ""

        val isInsert = action == Intent.ACTION_INSERT
        val looksLikeEvent = isInsert ||
            begin != null ||
            intent.type == "vnd.android.cursor.dir/event" ||
            intent.type == "vnd.android.cursor.item/event" ||
            intent.type?.contains("calendar") == true

        if (!looksLikeEvent) return null

        return PendingEventCompose(
            title = title,
            description = description,
            location = location,
            beginMillis = begin,
            endMillis = end,
        )
    }

    private fun readIcsFromUri(resolver: ContentResolver, intent: Intent): String? {
        val streamExtra = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
        val uri = streamExtra ?: intent.data ?: return null
        return runCatching {
            resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
    }
}
