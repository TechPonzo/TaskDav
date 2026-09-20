package app.taskdav.ui.calendar

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.taskdav.data.CollectionEntity
import app.taskdav.data.EventEntity
import app.taskdav.data.TaskEntity
import app.taskdav.domain.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

data class CalendarUiState(
    /** First day of the month currently shown in the grid. */
    val visibleMonthStartMillis: Long = CalendarViewModel.startOfMonthMillis(),
    /** Selected day, or null after changing months until the user picks a day. */
    val selectedDayStartMillis: Long? = CalendarViewModel.startOfDayMillis(),
    val collectionFilter: Long? = null,
    val syncMessage: String? = null,
    val syncing: Boolean = false,
    val error: String? = null,
    val showEditor: Boolean = false,
    val editingEventId: Long? = null,
    val editorSummary: String = "",
    val editorStartMillis: Long = System.currentTimeMillis(),
    val editorEndMillis: Long = System.currentTimeMillis() + CalendarViewModel.DEFAULT_DURATION_MS,
    val editorLocation: String = "",
    val editorCollectionId: Long? = null,
)

data class CalendarDayItem(
    val event: EventEntity,
    val collection: CollectionEntity?,
    val linkedTask: TaskEntity?,
)

data class MonthDayCell(
    val dayStartMillis: Long,
    val dayOfMonth: Int,
    val inCurrentMonth: Boolean,
    val eventCount: Int,
)

class CalendarViewModel(
    private val app: Application,
    private val repository: TaskRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(
        CalendarUiState(syncMessage = repository.lastSyncMessage()),
    )
    val ui: StateFlow<CalendarUiState> = _ui.asStateFlow()

    val collections: StateFlow<List<CollectionEntity>> = repository.observeCollections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val filteredEvents = combine(
        repository.observeEvents(),
        _ui,
    ) { events, state ->
        events.filter { state.collectionFilter == null || it.collectionId == state.collectionFilter }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val monthCells: StateFlow<List<MonthDayCell>> = combine(filteredEvents, _ui) { events, state ->
        buildMonthCells(state.visibleMonthStartMillis, events)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dayItems: StateFlow<List<CalendarDayItem>> = combine(
        filteredEvents,
        repository.observeCollections(),
        repository.observeTasks(),
        _ui,
    ) { events, collections, tasks, state ->
        val dayStart = state.selectedDayStartMillis ?: return@combine emptyList()
        val dayEnd = dayStart + DAY_MS
        val byCollection = collections.associateBy { it.id }
        val taskByEventUid = tasks
            .filter { !it.linkedEventUid.isNullOrBlank() }
            .associateBy { it.linkedEventUid!!.lowercase() }
        events
            .asSequence()
            .filter { overlapsDay(it, dayStart, dayEnd) }
            .sortedWith(
                compareBy(
                    { it.allDay },
                    { it.dtStartMillis ?: Long.MAX_VALUE },
                    { it.summary.lowercase() },
                ),
            )
            .map { event ->
                CalendarDayItem(
                    event = event,
                    collection = byCollection[event.collectionId],
                    linkedTask = taskByEventUid[event.uid.lowercase()],
                )
            }
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectDay(dayStartMillis: Long) {
        val day = startOfDay(dayStartMillis)
        _ui.update {
            it.copy(
                selectedDayStartMillis = day,
                visibleMonthStartMillis = startOfMonth(day),
                error = null,
            )
        }
    }

    fun shiftMonth(deltaMonths: Int) {
        _ui.update { state ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = state.visibleMonthStartMillis
                set(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MONTH, deltaMonths)
            }
            state.copy(
                visibleMonthStartMillis = startOfDay(cal.timeInMillis),
                selectedDayStartMillis = null,
                error = null,
            )
        }
    }

    fun goToday() {
        val today = startOfDay(System.currentTimeMillis())
        _ui.update {
            it.copy(
                selectedDayStartMillis = today,
                visibleMonthStartMillis = startOfMonth(today),
                error = null,
            )
        }
    }

    fun setCollectionFilter(id: Long?) {
        _ui.update { it.copy(collectionFilter = id) }
    }

    fun syncNow() {
        viewModelScope.launch {
            _ui.update { it.copy(syncing = true, error = null) }
            try {
                val msg = repository.syncNow()
                _ui.update { it.copy(syncing = false, syncMessage = msg) }
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        syncing = false,
                        error = e.message,
                        syncMessage = repository.lastSyncMessage(),
                    )
                }
            }
        }
    }

    fun openCreate() {
        val day = _ui.value.selectedDayStartMillis
            ?: startOfDay(System.currentTimeMillis())
        val start = day + 10 * 60 * 60 * 1000L // 10:00 local-ish from day start
        val cols = collections.value.filter { it.supportsVevent || it.supportsVtodo }
        _ui.update {
            it.copy(
                showEditor = true,
                editingEventId = null,
                editorSummary = "",
                editorStartMillis = start,
                editorEndMillis = start + DEFAULT_DURATION_MS,
                editorLocation = "",
                editorCollectionId = it.collectionFilter
                    ?: cols.firstOrNull()?.id,
                error = null,
            )
        }
    }

    fun openEdit(event: EventEntity) {
        val start = event.dtStartMillis ?: System.currentTimeMillis()
        val end = event.dtEndMillis ?: (start + DEFAULT_DURATION_MS)
        _ui.update {
            it.copy(
                showEditor = true,
                editingEventId = event.id,
                editorSummary = event.summary,
                editorStartMillis = start,
                editorEndMillis = end,
                editorLocation = event.location.orEmpty(),
                editorCollectionId = event.collectionId,
                error = null,
            )
        }
    }

    fun dismissEditor() {
        _ui.update { it.copy(showEditor = false, editingEventId = null, error = null) }
    }

    fun setEditorSummary(v: String) = _ui.update { it.copy(editorSummary = v) }
    fun setEditorLocation(v: String) = _ui.update { it.copy(editorLocation = v) }
    fun setEditorCollection(id: Long) = _ui.update { it.copy(editorCollectionId = id) }
    fun setEditorStart(millis: Long) = _ui.update {
        it.copy(
            editorStartMillis = millis,
            editorEndMillis = millis + DEFAULT_DURATION_MS,
        )
    }
    fun setEditorEnd(millis: Long) = _ui.update {
        it.copy(editorEndMillis = millis.coerceAtLeast(it.editorStartMillis + MIN_DURATION_MS))
    }

    fun saveEditor() {
        viewModelScope.launch {
            val state = _ui.value
            val collectionId = state.editorCollectionId
            if (collectionId == null || collectionId <= 0L) {
                _ui.update { it.copy(error = "Pick a calendar") }
                return@launch
            }
            if (state.editorEndMillis < state.editorStartMillis) {
                _ui.update { it.copy(error = "End must be after start") }
                return@launch
            }
            try {
                val editId = state.editingEventId
                if (editId == null) {
                    repository.createEvent(
                        collectionId = collectionId,
                        summary = state.editorSummary,
                        startMillis = state.editorStartMillis,
                        endMillis = state.editorEndMillis,
                        location = state.editorLocation,
                    )
                } else {
                    val existing = repository.getEventByUid(
                        dayItems.value.find { it.event.id == editId }?.event?.uid
                            ?: filteredEvents.value.find { it.id == editId }?.uid
                            ?: return@launch,
                    ) ?: return@launch
                    repository.updateEvent(
                        uid = existing.uid,
                        summary = state.editorSummary,
                        startMillis = state.editorStartMillis,
                        endMillis = state.editorEndMillis,
                        location = state.editorLocation,
                    )
                }
                repository.pushLocalChanges()
                _ui.update { it.copy(showEditor = false, editingEventId = null, error = null) }
            } catch (e: Exception) {
                _ui.update { it.copy(error = e.message) }
            }
        }
    }

    fun deleteEditing() {
        viewModelScope.launch {
            val id = _ui.value.editingEventId ?: return@launch
            try {
                repository.deleteEvent(id)
                repository.pushLocalChanges()
                _ui.update { it.copy(showEditor = false, editingEventId = null, error = null) }
            } catch (e: Exception) {
                _ui.update { it.copy(error = e.message) }
            }
        }
    }

    class Factory(
        private val app: Application,
        private val repository: TaskRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CalendarViewModel(app, repository) as T
        }
    }

    companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
        const val DEFAULT_DURATION_MS = 60 * 60 * 1000L
        private const val MIN_DURATION_MS = 60 * 1000L

        fun startOfDayMillis(millis: Long = System.currentTimeMillis()): Long = startOfDay(millis)

        fun startOfMonthMillis(millis: Long = System.currentTimeMillis()): Long = startOfMonth(millis)

        fun startOfDay(millis: Long): Long {
            val cal = Calendar.getInstance()
            cal.timeInMillis = millis
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        fun startOfMonth(millis: Long): Long {
            val cal = Calendar.getInstance()
            cal.timeInMillis = millis
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        fun overlapsDay(event: EventEntity, dayStart: Long, dayEnd: Long): Boolean {
            val start = event.dtStartMillis ?: return false
            val end = when {
                event.dtEndMillis != null && event.dtEndMillis > start -> event.dtEndMillis
                event.allDay -> start + DAY_MS
                else -> start + DEFAULT_DURATION_MS
            }
            return start < dayEnd && end > dayStart
        }

        fun buildMonthCells(selectedDayMillis: Long, events: List<EventEntity>): List<MonthDayCell> {
            val cal = Calendar.getInstance().apply {
                timeInMillis = selectedDayMillis
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val month = cal.get(Calendar.MONTH)
            val year = cal.get(Calendar.YEAR)
            // Align grid to week start (Monday = Calendar.MONDAY)
            val firstDow = cal.get(Calendar.DAY_OF_WEEK)
            val offset = (firstDow - Calendar.MONDAY + 7) % 7
            cal.add(Calendar.DAY_OF_MONTH, -offset)

            val counts = HashMap<Long, Int>()
            for (event in events) {
                val start = event.dtStartMillis ?: continue
                val endExclusive = when {
                    event.dtEndMillis != null && event.dtEndMillis > start -> event.dtEndMillis
                    event.allDay -> start + DAY_MS
                    else -> start + DEFAULT_DURATION_MS
                }
                var day = startOfDay(start)
                val last = startOfDay(endExclusive - 1)
                while (day <= last) {
                    counts[day] = (counts[day] ?: 0) + 1
                    day += DAY_MS
                }
            }

            return List(42) {
                val dayStart = cal.timeInMillis
                val inMonth =
                    cal.get(Calendar.MONTH) == month && cal.get(Calendar.YEAR) == year
                val cell = MonthDayCell(
                    dayStartMillis = dayStart,
                    dayOfMonth = cal.get(Calendar.DAY_OF_MONTH),
                    inCurrentMonth = inMonth,
                    eventCount = counts[dayStart] ?: 0,
                )
                cal.add(Calendar.DAY_OF_MONTH, 1)
                cell
            }
        }
    }
}
