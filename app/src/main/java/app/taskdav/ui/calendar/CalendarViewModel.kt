package app.taskdav.ui.calendar

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.taskdav.TaskDavApp
import app.taskdav.data.CalendarViewMode
import app.taskdav.data.CollectionEntity
import app.taskdav.data.EventEntity
import app.taskdav.data.TaskEntity
import app.taskdav.domain.EventRecurrence
import app.taskdav.domain.TaskRepository
import app.taskdav.sync.CalDavSyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

data class CalendarUiState(
    val viewMode: CalendarViewMode = CalendarViewMode.MONTHLY_AND_DAILY,
    val showViewPicker: Boolean = false,
    /** First day of the month currently shown in the grid. */
    val visibleMonthStartMillis: Long = CalendarViewModel.startOfMonthMillis(),
    /** Monday of the week currently shown in weekly view. */
    val visibleWeekStartMillis: Long = CalendarViewModel.startOfWeekMillis(),
    /** Year shown in yearly view. */
    val visibleYear: Int = CalendarViewModel.currentYear(),
    /** Selected day, or null after changing months/weeks until the user picks a day. */
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
    val editorDescription: String = "",
    val editorCollectionId: Long? = null,
    val editorRecurrence: EventRecurrence.EditState = EventRecurrence.EditState(),
    /** Full-day hourly timeline (opened by tapping the already-selected date). */
    val showHourlyDay: Boolean = false,
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
    /** Calendar colors for events on this day (ARGB), capped for the grid dots. */
    val eventColorsArgb: List<Int>,
)

class CalendarViewModel(
    private val app: Application,
    private val repository: TaskRepository,
) : ViewModel() {
    private val appearanceStore = (app as TaskDavApp).appearanceStore

    private val _ui = MutableStateFlow(
        CalendarUiState(syncMessage = repository.lastSyncMessage()),
    )
    val ui: StateFlow<CalendarUiState> = combine(
        _ui,
        appearanceStore.calendarViewMode,
    ) { state, modeId ->
        state.copy(viewMode = CalendarViewMode.fromId(modeId))
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CalendarUiState(syncMessage = repository.lastSyncMessage()),
    )

    val collections: StateFlow<List<CollectionEntity>> = repository.observeCollections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val filteredEvents = combine(
        repository.observeEvents(),
        _ui,
    ) { events, state ->
        events.filter { state.collectionFilter == null || it.collectionId == state.collectionFilter }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val monthCells: StateFlow<List<MonthDayCell>> = combine(
        filteredEvents,
        collections,
        _ui,
    ) { events, collections, state ->
        val expanded = expandForMonthGrid(events, state.visibleMonthStartMillis)
        buildMonthCells(state.visibleMonthStartMillis, expanded, collections)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 12 mini-month grids for the visible year. */
    val yearMonths: StateFlow<List<List<MonthDayCell>>> = combine(
        filteredEvents,
        collections,
        _ui,
    ) { events, collections, state ->
        (Calendar.JANUARY..Calendar.DECEMBER).map { month ->
            val monthStart = Calendar.getInstance().apply {
                set(Calendar.YEAR, state.visibleYear)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val expanded = expandForMonthGrid(events, monthStart)
            buildMonthCells(monthStart, expanded, collections)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dayItems: StateFlow<List<CalendarDayItem>> = combine(
        filteredEvents,
        repository.observeCollections(),
        repository.observeTasks(),
        _ui,
    ) { events, collections, tasks, state ->
        val dayStart = state.selectedDayStartMillis ?: return@combine emptyList()
        val expanded = EventRecurrence.expandAll(events, dayStart, dayStart + DAY_MS, DEFAULT_DURATION_MS)
        mapDayItems(expanded, collections, tasks, dayStart, dayStart + DAY_MS)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Events for the week currently shown. */
    val weekItems: StateFlow<List<CalendarDayItem>> = combine(
        filteredEvents,
        repository.observeCollections(),
        repository.observeTasks(),
        _ui,
    ) { events, collections, tasks, state ->
        val weekStart = state.visibleWeekStartMillis
        val weekEnd = weekStart + 7 * DAY_MS
        val expanded = EventRecurrence.expandAll(events, weekStart, weekEnd, DEFAULT_DURATION_MS)
        mapDayItems(expanded, collections, tasks, weekStart, weekEnd)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Upcoming / all events for the simple list view. */
    val upcomingItems: StateFlow<List<CalendarDayItem>> = combine(
        filteredEvents,
        repository.observeCollections(),
        repository.observeTasks(),
    ) { events, collections, tasks ->
        val now = startOfDay(System.currentTimeMillis())
        val rangeEnd = now + 90 * DAY_MS
        val expanded = EventRecurrence.expandAll(events, now, rangeEnd, DEFAULT_DURATION_MS)
        val byCollection = collections.associateBy { it.id }
        val taskByEventUid = tasks
            .filter { !it.linkedEventUid.isNullOrBlank() }
            .associateBy { it.linkedEventUid!!.lowercase() }
        expanded
            .sortedWith(
                compareBy(
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        val appRef = app as TaskDavApp
        appRef.takeStickyPendingEvent()?.let { pending ->
            openCreatePrefilled(
                title = pending.title,
                description = pending.description,
                location = pending.location,
                beginMillis = pending.beginMillis,
                endMillis = pending.endMillis,
            )
        }
        viewModelScope.launch {
            appRef.pendingEventCompose.collect { pending ->
                openCreatePrefilled(
                    title = pending.title,
                    description = pending.description,
                    location = pending.location,
                    beginMillis = pending.beginMillis,
                    endMillis = pending.endMillis,
                )
            }
        }
    }

    fun openViewPicker() = _ui.update { it.copy(showViewPicker = true) }
    fun dismissViewPicker() = _ui.update { it.copy(showViewPicker = false) }

    fun setViewMode(mode: CalendarViewMode) {
        viewModelScope.launch {
            appearanceStore.setCalendarViewMode(mode.id)
            _ui.update { state ->
                val needsDay = state.selectedDayStartMillis == null &&
                    mode != CalendarViewMode.YEARLY &&
                    mode != CalendarViewMode.EVENT_LIST &&
                    mode != CalendarViewMode.MONTHLY &&
                    mode != CalendarViewMode.WEEKLY
                val today = startOfDay(System.currentTimeMillis())
                state.copy(
                    showViewPicker = false,
                    selectedDayStartMillis = if (needsDay) today else state.selectedDayStartMillis,
                    visibleMonthStartMillis = if (needsDay) {
                        startOfMonth(today)
                    } else {
                        state.visibleMonthStartMillis
                    },
                    visibleWeekStartMillis = if (mode == CalendarViewMode.WEEKLY &&
                        state.selectedDayStartMillis == null
                    ) {
                        startOfWeek(today)
                    } else if (needsDay) {
                        startOfWeek(today)
                    } else {
                        state.visibleWeekStartMillis
                    },
                    visibleYear = if (needsDay) yearOf(today) else state.visibleYear,
                )
            }
        }
    }

    fun selectDay(dayStartMillis: Long, switchToMonthDaily: Boolean = false) {
        val day = startOfDay(dayStartMillis)
        viewModelScope.launch {
            if (switchToMonthDaily) {
                appearanceStore.setCalendarViewMode(CalendarViewMode.MONTHLY_AND_DAILY.id)
            }
            val alreadySelected = _ui.value.selectedDayStartMillis == day
            _ui.update {
                it.copy(
                    selectedDayStartMillis = day,
                    visibleMonthStartMillis = startOfMonth(day),
                    visibleWeekStartMillis = startOfWeek(day),
                    visibleYear = yearOf(day),
                    showHourlyDay = alreadySelected,
                    error = null,
                )
            }
        }
    }

    fun dismissHourlyDay() {
        _ui.update { it.copy(showHourlyDay = false) }
    }

    fun openHourlyDay(dayStartMillis: Long? = null) {
        val day = startOfDay(dayStartMillis ?: _ui.value.selectedDayStartMillis
            ?: System.currentTimeMillis())
        _ui.update {
            it.copy(
                selectedDayStartMillis = day,
                visibleMonthStartMillis = startOfMonth(day),
                visibleWeekStartMillis = startOfWeek(day),
                visibleYear = yearOf(day),
                showHourlyDay = true,
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
                visibleYear = cal.get(Calendar.YEAR),
                selectedDayStartMillis = null,
                showHourlyDay = false,
                error = null,
            )
        }
    }

    fun shiftYear(deltaYears: Int) {
        _ui.update { state ->
            state.copy(
                visibleYear = state.visibleYear + deltaYears,
                selectedDayStartMillis = null,
                showHourlyDay = false,
                error = null,
            )
        }
    }

    fun shiftDay(deltaDays: Int) {
        _ui.update { state ->
            val base = state.selectedDayStartMillis ?: startOfDay(System.currentTimeMillis())
            val day = base + deltaDays * DAY_MS
            state.copy(
                selectedDayStartMillis = day,
                visibleMonthStartMillis = startOfMonth(day),
                visibleWeekStartMillis = startOfWeek(day),
                visibleYear = yearOf(day),
                // Keep hourly schedule open while flipping days
            )
        }
    }

    fun shiftWeek(deltaWeeks: Int) {
        _ui.update { state ->
            val weekStart = state.visibleWeekStartMillis + deltaWeeks * 7 * DAY_MS
            state.copy(
                visibleWeekStartMillis = weekStart,
                visibleMonthStartMillis = startOfMonth(weekStart),
                visibleYear = yearOf(weekStart),
                selectedDayStartMillis = null,
                showHourlyDay = false,
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
                visibleWeekStartMillis = startOfWeek(today),
                visibleYear = yearOf(today),
                error = null,
            )
        }
    }

    fun openMonth(year: Int, monthIndex: Int) {
        val monthStart = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthIndex)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        viewModelScope.launch {
            appearanceStore.setCalendarViewMode(CalendarViewMode.MONTHLY_AND_DAILY.id)
            _ui.update {
                it.copy(
                    visibleMonthStartMillis = monthStart,
                    visibleYear = year,
                    selectedDayStartMillis = null,
                    error = null,
                )
            }
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

    fun openCreate(atMillis: Long? = null) {
        val day = _ui.value.selectedDayStartMillis
            ?: startOfDay(System.currentTimeMillis())
        val start = atMillis ?: (day + 10 * 60 * 60 * 1000L) // default 10:00
        openCreatePrefilled(
            title = "",
            description = "",
            location = "",
            beginMillis = start,
            endMillis = start + DEFAULT_DURATION_MS,
        )
    }

    fun openCreatePrefilled(
        title: String = "",
        description: String = "",
        location: String = "",
        beginMillis: Long? = null,
        endMillis: Long? = null,
    ) {
        val day = _ui.value.selectedDayStartMillis
            ?: startOfDay(System.currentTimeMillis())
        val start = beginMillis ?: (day + 10 * 60 * 60 * 1000L)
        val end = endMillis?.coerceAtLeast(start + MIN_DURATION_MS) ?: (start + DEFAULT_DURATION_MS)
        val cols = collections.value.filter { it.supportsVevent || it.supportsVtodo }
        _ui.update {
            it.copy(
                showEditor = true,
                editingEventId = null,
                editorSummary = title,
                editorStartMillis = start,
                editorEndMillis = end,
                editorLocation = location,
                editorDescription = description,
                editorCollectionId = it.collectionFilter
                    ?: cols.firstOrNull()?.id,
                editorRecurrence = EventRecurrence.EditState(),
                error = if (cols.isEmpty()) "No calendar available — set up Syncing first." else null,
                selectedDayStartMillis = startOfDay(start),
                showHourlyDay = false,
            )
        }
    }

    fun openEdit(event: EventEntity) {
        viewModelScope.launch {
            val master = repository.getEventByUid(event.uid) ?: event
            val start = master.dtStartMillis ?: System.currentTimeMillis()
            val end = master.dtEndMillis ?: (start + DEFAULT_DURATION_MS)
            _ui.update {
                it.copy(
                    showEditor = true,
                    editingEventId = master.id,
                    editorSummary = master.summary,
                    editorStartMillis = start,
                    editorEndMillis = end,
                    editorLocation = master.location.orEmpty(),
                    editorDescription = master.description.orEmpty(),
                    editorCollectionId = master.collectionId,
                    editorRecurrence = EventRecurrence.fromRrule(master.rrule),
                    error = null,
                )
            }
        }
    }

    fun dismissEditor() {
        _ui.update { it.copy(showEditor = false, editingEventId = null, error = null) }
    }

    fun setEditorSummary(v: String) = _ui.update { it.copy(editorSummary = v) }
    fun setEditorLocation(v: String) = _ui.update { it.copy(editorLocation = v) }
    fun setEditorDescription(v: String) = _ui.update { it.copy(editorDescription = v) }
    fun setEditorCollection(id: Long) = _ui.update { it.copy(editorCollectionId = id) }
    fun setEditorRecurrence(state: EventRecurrence.EditState) =
        _ui.update { it.copy(editorRecurrence = state) }
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
            val rrule = state.editorRecurrence.toRrule()
            try {
                val editId = state.editingEventId
                if (editId == null) {
                    repository.createEvent(
                        collectionId = collectionId,
                        summary = state.editorSummary,
                        startMillis = state.editorStartMillis,
                        endMillis = state.editorEndMillis,
                        location = state.editorLocation,
                        description = state.editorDescription,
                        rrule = rrule,
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
                        description = state.editorDescription,
                        rrule = rrule,
                        updateRrule = true,
                    )
                }
                CalDavSyncWorker.enqueueNow(app)
                _ui.update { it.copy(showEditor = false, editingEventId = null, error = null) }
                repository.tryPushLocalChanges()
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
                CalDavSyncWorker.enqueueNow(app)
                _ui.update { it.copy(showEditor = false, editingEventId = null, error = null) }
                repository.tryPushLocalChanges()
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

        fun startOfWeekMillis(millis: Long = System.currentTimeMillis()): Long = startOfWeek(millis)

        fun currentYear(): Int = Calendar.getInstance().get(Calendar.YEAR)

        fun yearOf(millis: Long): Int {
            return Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.YEAR)
        }

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

        fun startOfWeek(millis: Long): Long {
            val cal = Calendar.getInstance()
            cal.timeInMillis = startOfDay(millis)
            val offset = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
            cal.add(Calendar.DAY_OF_MONTH, -offset)
            return cal.timeInMillis
        }

        private fun expandForMonthGrid(
            events: List<EventEntity>,
            monthStartMillis: Long,
        ): List<EventEntity> {
            val cal = Calendar.getInstance().apply {
                timeInMillis = monthStartMillis
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val firstDow = cal.get(Calendar.DAY_OF_WEEK)
            val offset = (firstDow - Calendar.MONDAY + 7) % 7
            cal.add(Calendar.DAY_OF_MONTH, -offset)
            val rangeStart = cal.timeInMillis
            val rangeEnd = rangeStart + 42 * DAY_MS
            return EventRecurrence.expandAll(events, rangeStart, rangeEnd, DEFAULT_DURATION_MS)
        }

        private fun mapDayItems(
            events: List<EventEntity>,
            collections: List<CollectionEntity>,
            tasks: List<TaskEntity>,
            rangeStart: Long,
            rangeEnd: Long,
        ): List<CalendarDayItem> {
            val byCollection = collections.associateBy { it.id }
            val taskByEventUid = tasks
                .filter { !it.linkedEventUid.isNullOrBlank() }
                .associateBy { it.linkedEventUid!!.lowercase() }
            return events
                .asSequence()
                .filter { overlapsDay(it, rangeStart, rangeEnd) }
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

        fun buildMonthCells(
            selectedDayMillis: Long,
            events: List<EventEntity>,
            collections: List<CollectionEntity>,
        ): List<MonthDayCell> {
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

            val colorByCollection = collections.associate { it.id to (it.colorArgb ?: DEFAULT_CALENDAR_COLOR) }
            val colorsByDay = HashMap<Long, MutableList<Int>>()
            for (event in events) {
                val start = event.dtStartMillis ?: continue
                val endExclusive = when {
                    event.dtEndMillis != null && event.dtEndMillis > start -> event.dtEndMillis
                    event.allDay -> start + DAY_MS
                    else -> start + DEFAULT_DURATION_MS
                }
                val color = colorByCollection[event.collectionId] ?: DEFAULT_CALENDAR_COLOR
                var day = startOfDay(start)
                val last = startOfDay(endExclusive - 1)
                while (day <= last) {
                    val list = colorsByDay.getOrPut(day) { mutableListOf() }
                    if (list.size < MAX_EVENT_DOTS) {
                        list += color
                    }
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
                    eventColorsArgb = colorsByDay[dayStart].orEmpty(),
                )
                cal.add(Calendar.DAY_OF_MONTH, 1)
                cell
            }
        }

        private const val MAX_EVENT_DOTS = 3
        private const val DEFAULT_CALENDAR_COLOR = 0xFF2B6A4F.toInt()
    }
}
