package app.taskdav.widget

import android.content.Context
import app.taskdav.TaskDavApp
import app.taskdav.domain.EventRecurrence
import app.taskdav.domain.TaskTreeBuilder
import app.taskdav.ui.calendar.CalendarViewModel
import app.taskdav.ui.common.DateFormats
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class AgendaRange { WEEK, MONTH }

data class WidgetEventRow(
    val id: Long,
    val title: String,
    val timeLabel: String,
)

data class WidgetTaskRow(
    val id: Long,
    val title: String,
    val dueLabel: String?,
    val overdue: Boolean,
)

data class WidgetNoteRow(
    val id: Long,
    val title: String,
    val subtitle: String?,
)

data class CalendarWidgetModel(
    val headerDate: String,
    val events: List<WidgetEventRow>,
)

data class AgendaWidgetModel(
    val range: AgendaRange,
    val headerTitle: String,
    val subtitle: String,
    val events: List<WidgetEventRow>,
)

data class TasksWidgetModel(
    val openCount: Int,
    val tasks: List<WidgetTaskRow>,
)

data class NotesWidgetModel(
    val notes: List<WidgetNoteRow>,
)

object WidgetDataLoader {
    suspend fun loadCalendar(context: Context): CalendarWidgetModel {
        val app = context.applicationContext as TaskDavApp
        val now = System.currentTimeMillis()
        val todayStart = CalendarViewModel.startOfDay(now)
        val tomorrowStart = todayStart + TimeUnit.DAYS.toMillis(1)
        val events = app.database.events().getActive()
        val today = EventRecurrence.expandAll(events, todayStart, tomorrowStart)
            .sortedBy { it.dtStartMillis ?: Long.MAX_VALUE }
        val header = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(todayStart))
        return CalendarWidgetModel(
            headerDate = header,
            events = today.map { event ->
                WidgetEventRow(
                    id = event.id xor (event.dtStartMillis ?: 0L),
                    title = event.summary.ifBlank { "Event" },
                    timeLabel = when {
                        event.allDay -> "All day"
                        event.dtStartMillis != null -> DateFormats.time(context, event.dtStartMillis)
                        else -> "—"
                    },
                )
            },
        )
    }

    suspend fun loadAgenda(context: Context, range: AgendaRange): AgendaWidgetModel {
        val app = context.applicationContext as TaskDavApp
        val now = System.currentTimeMillis()
        val (rangeStart, rangeEnd, header, subtitle) = when (range) {
            AgendaRange.WEEK -> {
                val start = CalendarViewModel.startOfWeek(now)
                val end = start + TimeUnit.DAYS.toMillis(7)
                val endLabel = SimpleDateFormat("MMM d", Locale.getDefault())
                    .format(Date(end - TimeUnit.DAYS.toMillis(1)))
                val startLabel = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(start))
                AgendaBounds(start, end, "This week", "$startLabel – $endLabel")
            }
            AgendaRange.MONTH -> {
                val start = CalendarViewModel.startOfMonth(now)
                val cal = Calendar.getInstance().apply {
                    timeInMillis = start
                    add(Calendar.MONTH, 1)
                }
                val end = cal.timeInMillis
                val monthLabel = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(start))
                AgendaBounds(start, end, monthLabel, "This month")
            }
        }
        val events = app.database.events().getActive()
        val expanded = EventRecurrence.expandAll(events, rangeStart, rangeEnd)
            .sortedBy { it.dtStartMillis ?: Long.MAX_VALUE }
        val dayFmt = SimpleDateFormat("EEE d", Locale.getDefault())
        return AgendaWidgetModel(
            range = range,
            headerTitle = header,
            subtitle = subtitle,
            events = expanded.map { event ->
                val startMillis = event.dtStartMillis
                val day = startMillis?.let { dayFmt.format(Date(it)) } ?: ""
                val time = when {
                    event.allDay -> "All day"
                    startMillis != null -> DateFormats.time(context, startMillis)
                    else -> "—"
                }
                WidgetEventRow(
                    id = event.id xor (startMillis ?: 0L),
                    title = event.summary.ifBlank { "Event" },
                    timeLabel = if (day.isBlank()) time else "$day · $time",
                )
            },
        )
    }

    suspend fun loadTasks(context: Context): TasksWidgetModel {
        val app = context.applicationContext as TaskDavApp
        val now = System.currentTimeMillis()
        val todayStart = CalendarViewModel.startOfDay(now)
        val open = app.database.tasks().getActive()
            .filter { !it.isCategory && !it.deleted && !TaskTreeBuilder.isCompleted(it) }

        fun dueDay(millis: Long?) = millis?.let(CalendarViewModel::startOfDay)

        val overdue = open.filter { task ->
            val d = dueDay(task.dueMillis) ?: return@filter false
            d < todayStart
        }.sortedBy { it.dueMillis }
        val dueToday = open.filter { dueDay(it.dueMillis) == todayStart }
            .sortedBy { it.dueMillis ?: Long.MAX_VALUE }
        val rest = open.filter { task ->
            val d = dueDay(task.dueMillis)
            d == null || d > todayStart
        }.sortedBy { it.dueMillis ?: Long.MAX_VALUE }

        val ordered = (overdue + dueToday + rest).distinctBy { it.id }
        return TasksWidgetModel(
            openCount = open.size,
            tasks = ordered.map { task ->
                val d = dueDay(task.dueMillis)
                val overdueFlag = d != null && d < todayStart
                WidgetTaskRow(
                    id = task.id,
                    title = task.summary.ifBlank { "Task" },
                    dueLabel = task.dueMillis?.let { DateFormats.date(context, it) },
                    overdue = overdueFlag,
                )
            },
        )
    }

    suspend fun loadNotes(context: Context): NotesWidgetModel {
        val app = context.applicationContext as TaskDavApp
        val notes = app.repository.observeRecentNotes(40).first()
        return NotesWidgetModel(
            notes = notes.map { note ->
                WidgetNoteRow(
                    id = note.id,
                    title = note.summary.ifBlank { "Note" },
                    subtitle = note.description?.takeIf { it.isNotBlank() }
                        ?.lineSequence()?.firstOrNull()?.take(80),
                )
            },
        )
    }

    private data class AgendaBounds(
        val start: Long,
        val end: Long,
        val header: String,
        val subtitle: String,
    )
}
