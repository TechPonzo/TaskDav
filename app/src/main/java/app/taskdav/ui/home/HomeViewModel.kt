package app.taskdav.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.taskdav.data.EventEntity
import app.taskdav.data.NoteEntity
import app.taskdav.data.TaskEntity
import app.taskdav.domain.TaskRepository
import app.taskdav.domain.TaskTreeBuilder
import app.taskdav.ui.calendar.CalendarViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import java.util.concurrent.TimeUnit

data class HomeStats(
    val openTasks: Int = 0,
    val overdueTasks: Int = 0,
    val dueTodayTasks: Int = 0,
    val todayEvents: Int = 0,
    val notes: Int = 0,
)

data class HomeAgendaItem(
    val id: Long,
    val kind: HomeItemKind,
    val title: String,
    /** Epoch millis for sorting / secondary line (due or event start). */
    val atMillis: Long?,
    val allDay: Boolean = false,
    val subtitle: String? = null,
)

enum class HomeItemKind { EVENT, TASK, NOTE }

data class HomeUiState(
    val greeting: String = "Home",
    val syncMessage: String? = null,
    val lastSyncAt: Long = 0L,
    val stats: HomeStats = HomeStats(),
    val todayEvents: List<HomeAgendaItem> = emptyList(),
    val dueToday: List<HomeAgendaItem> = emptyList(),
    val overdue: List<HomeAgendaItem> = emptyList(),
    val upcomingEvents: List<HomeAgendaItem> = emptyList(),
    val upcomingTasks: List<HomeAgendaItem> = emptyList(),
    val recentNotes: List<HomeAgendaItem> = emptyList(),
)

class HomeViewModel(
    private val repository: TaskRepository,
) : ViewModel() {

    val ui: StateFlow<HomeUiState> = combine(
        repository.observeTasks(),
        repository.observeEvents(),
        repository.observeNotes(),
    ) { tasks, events, notes ->
        buildState(tasks, events, notes)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun buildState(
        tasks: List<TaskEntity>,
        events: List<EventEntity>,
        notes: List<NoteEntity>,
    ): HomeUiState {
        val now = System.currentTimeMillis()
        val todayStart = CalendarViewModel.startOfDay(now)
        val tomorrowStart = todayStart + TimeUnit.DAYS.toMillis(1)
        val weekEnd = todayStart + TimeUnit.DAYS.toMillis(7)

        val actionable = tasks.filter { !it.isCategory && !it.deleted }
        val open = actionable.filter { !TaskTreeBuilder.isCompleted(it) }

        fun dueDay(task: TaskEntity): Long? =
            task.dueMillis?.let(CalendarViewModel::startOfDay)

        val overdueTasks = open.filter { due ->
            val d = dueDay(due) ?: return@filter false
            d < todayStart
        }.sortedBy { it.dueMillis }

        val dueTodayTasks = open.filter { dueDay(it) == todayStart }
            .sortedBy { it.dueMillis ?: Long.MAX_VALUE }

        val upcomingDue = open.filter { task ->
            val d = dueDay(task) ?: return@filter false
            d in tomorrowStart until weekEnd
        }.sortedBy { it.dueMillis }.take(6)

        val todayEv = events
            .filter { eventOnDay(it, todayStart, tomorrowStart) }
            .sortedBy { it.dtStartMillis ?: Long.MAX_VALUE }

        val upcomingEv = events
            .filter { event ->
                val start = event.dtStartMillis ?: return@filter false
                start >= tomorrowStart && start < weekEnd
            }
            .sortedBy { it.dtStartMillis }
            .take(8)

        return HomeUiState(
            greeting = greetingForHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)),
            syncMessage = repository.lastSyncMessage(),
            lastSyncAt = repository.lastSyncAt(),
            stats = HomeStats(
                openTasks = open.size,
                overdueTasks = overdueTasks.size,
                dueTodayTasks = dueTodayTasks.size,
                todayEvents = todayEv.size,
                notes = notes.size,
            ),
            todayEvents = todayEv.map { it.toAgenda() },
            dueToday = dueTodayTasks.map { it.toAgenda() },
            overdue = overdueTasks.take(8).map { it.toAgenda() },
            upcomingEvents = upcomingEv.map { it.toAgenda() },
            upcomingTasks = upcomingDue.map { it.toAgenda() },
            recentNotes = notes
                .sortedByDescending { it.updatedAt }
                .take(5)
                .map {
                    HomeAgendaItem(
                        id = it.id,
                        kind = HomeItemKind.NOTE,
                        title = it.summary,
                        atMillis = it.updatedAt,
                        subtitle = it.description?.takeIf { d -> d.isNotBlank() }
                            ?.lineSequence()?.firstOrNull()?.take(80),
                    )
                },
        )
    }

    private fun EventEntity.toAgenda() = HomeAgendaItem(
        id = id,
        kind = HomeItemKind.EVENT,
        title = summary,
        atMillis = dtStartMillis,
        allDay = allDay,
        subtitle = location?.takeIf { it.isNotBlank() },
    )

    private fun TaskEntity.toAgenda() = HomeAgendaItem(
        id = id,
        kind = HomeItemKind.TASK,
        title = summary,
        atMillis = dueMillis,
        subtitle = null,
    )

    private fun eventOnDay(event: EventEntity, dayStart: Long, dayEnd: Long): Boolean {
        val start = event.dtStartMillis ?: return false
        val end = event.dtEndMillis ?: (start + if (event.allDay) TimeUnit.DAYS.toMillis(1) else 0)
        // Inclusive of events that span the day
        return start < dayEnd && end > dayStart
    }

    private fun greetingForHour(hour: Int): String = when (hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        in 18..21 -> "Good evening"
        else -> "Hello"
    }

    class Factory(
        private val repository: TaskRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(repository) as T
        }
    }
}
