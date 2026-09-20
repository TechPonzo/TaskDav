package app.taskdav.domain

import app.taskdav.data.EventEntity
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Recur
import net.fortuna.ical4j.model.parameter.Value
import java.util.concurrent.TimeUnit

/**
 * Build / parse weekly-oriented RRULEs for the event editor, and expand
 * occurrences for calendar display.
 */
object EventRecurrence {
    enum class Mode {
        NONE,
        WEEKLY,
        EVERY_2_WEEKS,
        EVERY_N_WEEKS,
        /** Keep an RRULE we don't fully model in the simple UI. */
        OTHER,
    }

    data class EditState(
        val mode: Mode = Mode.NONE,
        val intervalWeeks: Int = 3,
        val count: Int? = null,
        val otherRrule: String? = null,
    ) {
        fun toRrule(): String? = when (mode) {
            Mode.NONE -> null
            Mode.WEEKLY -> buildWeekly(1, count)
            Mode.EVERY_2_WEEKS -> buildWeekly(2, count)
            Mode.EVERY_N_WEEKS -> buildWeekly(intervalWeeks.coerceAtLeast(1), count)
            Mode.OTHER -> otherRrule?.takeIf { it.isNotBlank() }
        }

        fun summaryLabel(): String = when (mode) {
            Mode.NONE -> "Does not repeat"
            Mode.WEEKLY -> countLabel("Every week")
            Mode.EVERY_2_WEEKS -> countLabel("Every 2 weeks")
            Mode.EVERY_N_WEEKS -> countLabel("Every $intervalWeeks weeks")
            Mode.OTHER -> otherRrule ?: "Custom repeat"
        }

        private fun countLabel(base: String): String =
            if (count != null && count > 0) "$base · $count times" else base
    }

    fun fromRrule(rrule: String?): EditState {
        if (rrule.isNullOrBlank()) return EditState()
        val parts = rrule.split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .associate { part ->
                val idx = part.indexOf('=')
                if (idx <= 0) part.uppercase() to ""
                else part.substring(0, idx).uppercase() to part.substring(idx + 1)
            }
        val freq = parts["FREQ"]?.uppercase()
        val interval = parts["INTERVAL"]?.toIntOrNull() ?: 1
        val count = parts["COUNT"]?.toIntOrNull()
        val hasUntil = parts.containsKey("UNTIL")
        val hasBy = parts.keys.any { it.startsWith("BY") }
        if (freq == "WEEKLY" && !hasUntil && !hasBy) {
            return when (interval) {
                1 -> EditState(Mode.WEEKLY, intervalWeeks = 1, count = count)
                2 -> EditState(Mode.EVERY_2_WEEKS, intervalWeeks = 2, count = count)
                else -> EditState(Mode.EVERY_N_WEEKS, intervalWeeks = interval.coerceAtLeast(1), count = count)
            }
        }
        return EditState(Mode.OTHER, otherRrule = rrule)
    }

    fun buildWeekly(intervalWeeks: Int, count: Int?): String {
        val interval = intervalWeeks.coerceAtLeast(1)
        return buildString {
            append("FREQ=WEEKLY")
            if (interval != 1) append(";INTERVAL=").append(interval)
            if (count != null && count > 0) append(";COUNT=").append(count)
        }
    }

    /**
     * Returns event copies with occurrence start/end in [rangeStart, rangeEnd).
     * Non-recurring events are returned as-is when they overlap the range.
     */
    fun expandInRange(
        event: EventEntity,
        rangeStart: Long,
        rangeEnd: Long,
        defaultDurationMs: Long = TimeUnit.HOURS.toMillis(1),
    ): List<EventEntity> {
        val start = event.dtStartMillis ?: return emptyList()
        val end = event.dtEndMillis?.takeIf { it > start }
            ?: if (event.allDay) start + TimeUnit.DAYS.toMillis(1) else start + defaultDurationMs
        val duration = end - start
        val rrule = event.rrule?.takeIf { it.isNotBlank() }
        if (rrule == null) {
            return if (start < rangeEnd && end > rangeStart) listOf(event) else emptyList()
        }
        return try {
            val recur = Recur(rrule)
            val seed = toIcalDate(start, event.allDay)
            val periodStart = toIcalDate(rangeStart, event.allDay)
            val periodEnd = toIcalDate(rangeEnd, event.allDay)
            val value = if (event.allDay) Value.DATE else Value.DATE_TIME
            @Suppress("DEPRECATION")
            val dates = recur.getDates(seed, periodStart, periodEnd, value)
            dates.mapNotNull { date ->
                val occStart = date.time
                val occEnd = occStart + duration
                if (occStart < rangeEnd && occEnd > rangeStart) {
                    event.copy(dtStartMillis = occStart, dtEndMillis = occEnd)
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            if (start < rangeEnd && end > rangeStart) listOf(event) else emptyList()
        }
    }

    fun expandAll(
        events: List<EventEntity>,
        rangeStart: Long,
        rangeEnd: Long,
        defaultDurationMs: Long = TimeUnit.HOURS.toMillis(1),
    ): List<EventEntity> =
        events.flatMap { expandInRange(it, rangeStart, rangeEnd, defaultDurationMs) }
            .sortedBy { it.dtStartMillis ?: Long.MAX_VALUE }

    private fun toIcalDate(millis: Long, allDay: Boolean): Date {
        return if (allDay) {
            Date(millis)
        } else {
            DateTime(millis).also { it.isUtc = true }
        }
    }
}
