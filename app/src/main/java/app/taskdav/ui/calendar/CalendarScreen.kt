package app.taskdav.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.taskdav.data.CalendarViewMode
import app.taskdav.data.CollectionEntity
import app.taskdav.data.EventEntity
import app.taskdav.domain.EventRecurrence
import app.taskdav.ui.common.CompactHeader
import app.taskdav.ui.common.DateFormats
import app.taskdav.ui.common.DockScrollPadding
import app.taskdav.ui.common.ExpressiveExtendedFab
import app.taskdav.ui.common.ExpressiveFilterChip
import app.taskdav.ui.common.InlineDateTimePicker
import app.taskdav.ui.common.ItemShare
import app.taskdav.ui.common.ConfirmDeleteDialog
import app.taskdav.ui.common.SwipeRevealAction
import app.taskdav.ui.common.SwipeRevealRow
import app.taskdav.ui.common.SyncLoadingBanner
import app.taskdav.ui.common.openEventLink
import app.taskdav.ui.common.resolveEventLink
import app.taskdav.ui.theme.TaskDavRadii
import app.taskdav.ui.theme.collectionColorOrDefault
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onOpenTask: (Long) -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val dayItems by viewModel.dayItems.collectAsStateWithLifecycle()
    val weekItems by viewModel.weekItems.collectAsStateWithLifecycle()
    val upcomingItems by viewModel.upcomingItems.collectAsStateWithLifecycle()
    val monthCells by viewModel.monthCells.collectAsStateWithLifecycle()
    val yearMonths by viewModel.yearMonths.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val eventCollections = remember(collections) {
        collections.filter { it.enabled && (it.supportsVevent || it.supportsVtodo) }
    }
    val todayStart = remember { CalendarViewModel.startOfDay(System.currentTimeMillis()) }
    val selectedOrToday = ui.selectedDayStartMillis ?: todayStart
    val weekStart = ui.visibleWeekStartMillis
    val weekEventDays = remember(weekItems) {
        weekItems.mapNotNull { it.event.dtStartMillis?.let(CalendarViewModel::startOfDay) }.toSet()
    }

    LaunchedEffect(ui.viewMode, ui.selectedDayStartMillis) {
        if (ui.selectedDayStartMillis == null && ui.viewMode == CalendarViewMode.DAILY) {
            viewModel.goToday()
        }
    }

    if (ui.showEditor) {
        EventEditorScreen(
            isNew = ui.editingEventId == null,
            summary = ui.editorSummary,
            description = ui.editorDescription,
            location = ui.editorLocation,
            startMillis = ui.editorStartMillis,
            endMillis = ui.editorEndMillis,
            collectionId = ui.editorCollectionId,
            collections = eventCollections,
            recurrence = ui.editorRecurrence,
            error = ui.error,
            onSummary = viewModel::setEditorSummary,
            onDescription = viewModel::setEditorDescription,
            onLocation = viewModel::setEditorLocation,
            onCollection = viewModel::setEditorCollection,
            onRecurrence = viewModel::setEditorRecurrence,
            onStart = viewModel::setEditorStart,
            onEnd = viewModel::setEditorEnd,
            onDismiss = viewModel::dismissEditor,
            onSave = viewModel::saveEditor,
            onDelete = if (ui.editingEventId != null) viewModel::deleteEditing else null,
            onShare = {
                val matched = listOf(dayItems, weekItems, upcomingItems)
                    .flatten()
                    .map { it.event }
                    .find { it.id == ui.editingEventId }
                val event = matched ?: EventEntity(
                    id = ui.editingEventId ?: 0,
                    uid = "share-draft",
                    href = null,
                    etag = null,
                    collectionId = ui.editorCollectionId ?: 0,
                    summary = ui.editorSummary.ifBlank { "Event" },
                    description = ui.editorDescription.ifBlank { null },
                    location = ui.editorLocation.ifBlank { null },
                    dtStartMillis = ui.editorStartMillis,
                    dtEndMillis = ui.editorEndMillis,
                    allDay = false,
                    rrule = ui.editorRecurrence.toRrule(),
                    icsRaw = null,
                )
                ItemShare.shareEvent(context, event)
            }.takeIf { ui.editingEventId != null },
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            Box(modifier = Modifier.navigationBarsPadding().padding(bottom = 72.dp)) {
                ExpressiveExtendedFab(
                    text = "New event",
                    icon = Icons.Default.Add,
                    onClick = viewModel::openCreate,
                )
            }
        },
    ) { _ ->
        PullToRefreshBox(
            isRefreshing = ui.syncing,
            onRefresh = viewModel::syncNow,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompactHeader(
                        title = "Calendar",
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = viewModel::openViewPicker) {
                        Icon(Icons.Default.Apps, contentDescription = "Calendar view")
                    }
                    IconButton(onClick = viewModel::syncNow, enabled = !ui.syncing) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync")
                    }
                }
                if (ui.syncing) {
                    SyncLoadingBanner(
                        message = ui.syncMessage?.takeIf { it.isNotBlank() }
                            ?: "Syncing calendar…",
                    )
                }
                CollectionFilterRow(
                    collections = eventCollections,
                    collectionFilter = ui.collectionFilter,
                    onFilter = viewModel::setCollectionFilter,
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = DockScrollPadding,
                ) {
                    when (ui.viewMode) {
                        CalendarViewMode.YEARLY -> {
                            item {
                                YearHeader(
                                    year = ui.visibleYear,
                                    onPrev = { viewModel.shiftYear(-1) },
                                    onNext = { viewModel.shiftYear(1) },
                                    onToday = viewModel::goToday,
                                )
                            }
                            item {
                                YearGrid(
                                    year = ui.visibleYear,
                                    months = yearMonths,
                                    todayStart = todayStart,
                                    selectedDayStart = ui.selectedDayStartMillis,
                                    onSelectDay = { viewModel.selectDay(it, switchToMonthDaily = true) },
                                    onSelectMonth = { month ->
                                        viewModel.openMonth(ui.visibleYear, month)
                                    },
                                )
                            }
                        }
                        CalendarViewMode.MONTHLY -> {
                            item {
                                MonthHeader(
                                    visibleMonthStart = ui.visibleMonthStartMillis,
                                    onPrevMonth = { viewModel.shiftMonth(-1) },
                                    onNextMonth = { viewModel.shiftMonth(1) },
                                    onToday = viewModel::goToday,
                                )
                            }
                            item {
                                MonthGrid(
                                    cells = monthCells,
                                    selectedDayStart = ui.selectedDayStartMillis,
                                    todayStart = todayStart,
                                    onSelect = viewModel::selectDay,
                                )
                            }
                            dayAgenda(
                                selectedDay = ui.selectedDayStartMillis,
                                dayItems = dayItems,
                                onOpenTask = onOpenTask,
                                onEdit = { viewModel.openEdit(it.event) },
                                context = context,
                            )
                        }
                        CalendarViewMode.MONTHLY_AND_DAILY -> {
                            item {
                                MonthHeader(
                                    visibleMonthStart = ui.visibleMonthStartMillis,
                                    onPrevMonth = { viewModel.shiftMonth(-1) },
                                    onNextMonth = { viewModel.shiftMonth(1) },
                                    onToday = viewModel::goToday,
                                )
                            }
                            item {
                                MonthGrid(
                                    cells = monthCells,
                                    selectedDayStart = ui.selectedDayStartMillis,
                                    todayStart = todayStart,
                                    onSelect = viewModel::selectDay,
                                )
                            }
                            dayAgenda(
                                selectedDay = ui.selectedDayStartMillis,
                                dayItems = dayItems,
                                onOpenTask = onOpenTask,
                                onEdit = { viewModel.openEdit(it.event) },
                                context = context,
                            )
                        }
                        CalendarViewMode.WEEKLY -> {
                            item {
                                WeekHeader(
                                    weekStart = weekStart,
                                    onPrev = { viewModel.shiftWeek(-1) },
                                    onNext = { viewModel.shiftWeek(1) },
                                    onToday = viewModel::goToday,
                                )
                            }
                            item {
                                WeekStrip(
                                    weekStart = weekStart,
                                    selectedDayStart = ui.selectedDayStartMillis,
                                    todayStart = todayStart,
                                    eventDays = weekEventDays,
                                    onSelect = viewModel::selectDay,
                                )
                            }
                            item {
                                Text(
                                    "This week",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                            eventRows(
                                items = weekItems,
                                emptyMessage = "No events this week.",
                                onOpenTask = onOpenTask,
                                onEdit = { viewModel.openEdit(it.event) },
                                context = context,
                            )
                        }
                        CalendarViewMode.DAILY -> {
                            item {
                                DayNavHeader(
                                    dayStart = selectedOrToday,
                                    onPrev = { viewModel.shiftDay(-1) },
                                    onNext = { viewModel.shiftDay(1) },
                                    onToday = viewModel::goToday,
                                )
                            }
                            dayAgenda(
                                selectedDay = selectedOrToday,
                                dayItems = dayItems,
                                onOpenTask = onOpenTask,
                                onEdit = { viewModel.openEdit(it.event) },
                                context = context,
                                requireSelection = false,
                            )
                        }
                        CalendarViewMode.EVENT_LIST -> {
                            item {
                                Text(
                                    "All events",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                            eventRows(
                                items = upcomingItems,
                                emptyMessage = "No events. Sync or tap + to add one.",
                                onOpenTask = onOpenTask,
                                onEdit = { viewModel.openEdit(it.event) },
                                context = context,
                            )
                        }
                    }
                }
            }
        }
    }

    if (ui.showHourlyDay) {
        val day = ui.selectedDayStartMillis
            ?: CalendarViewModel.startOfDay(System.currentTimeMillis())
        HourlyDaySchedule(
            dayStartMillis = day,
            items = dayItems,
            onBack = viewModel::dismissHourlyDay,
            onPrevDay = {
                viewModel.shiftDay(-1)
            },
            onNextDay = {
                viewModel.shiftDay(1)
            },
            onCreateAt = { viewModel.openCreate(atMillis = it) },
            onEdit = { viewModel.openEdit(it.event) },
        )
    }

    if (ui.showViewPicker) {
        CalendarViewPickerDialog(
            selected = ui.viewMode,
            onSelect = viewModel::setViewMode,
            onDismiss = viewModel::dismissViewPicker,
        )
    }
}

@Composable
private fun CollectionFilterRow(
    collections: List<CollectionEntity>,
    collectionFilter: Long?,
    onFilter: (Long?) -> Unit,
) {
    if (collections.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ExpressiveFilterChip(
            label = "All",
            selected = collectionFilter == null,
            onClick = { onFilter(null) },
        )
        collections.forEach { col ->
            ExpressiveFilterChip(
                label = col.displayName,
                selected = collectionFilter == col.id,
                onClick = {
                    onFilter(if (collectionFilter == col.id) null else col.id)
                },
            )
        }
    }
}

private fun LazyListScope.dayAgenda(
    selectedDay: Long?,
    dayItems: List<CalendarDayItem>,
    onOpenTask: (Long) -> Unit,
    onEdit: (CalendarDayItem) -> Unit,
    context: android.content.Context,
    requireSelection: Boolean = true,
) {
    if (requireSelection && selectedDay == null) {
        item {
            Text(
                "Tap a day to see its events.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
        return
    }
    val day = selectedDay ?: return
    item {
        Text(
            formatDayTitle(day),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
    eventRows(
        items = dayItems,
        emptyMessage = "No events this day. Sync or tap + to add one.",
        onOpenTask = onOpenTask,
        onEdit = onEdit,
        context = context,
    )
}

private fun LazyListScope.eventRows(
    items: List<CalendarDayItem>,
    emptyMessage: String,
    onOpenTask: (Long) -> Unit,
    onEdit: (CalendarDayItem) -> Unit,
    context: android.content.Context,
) {
    if (items.isEmpty()) {
        item {
            Text(
                emptyMessage,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    } else {
        items(items, key = { "${it.event.id}-${it.event.dtStartMillis}" }) { item ->
            EventRow(
                item = item,
                onClick = { onEdit(item) },
                onOpenTask = item.linkedTask?.let { task -> { onOpenTask(task.id) } },
                onShare = { ItemShare.shareEvent(context, item.event) },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

@Composable
private fun WeekHeader(
    weekStart: Long,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    val context = LocalContext.current
    val end = weekStart + 6 * CalendarViewModel.DAY_MS
    val label = "${DateFormats.date(context, weekStart)} – ${DateFormats.date(context, end)}"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous week")
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onToday) { Text("Today") }
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next week")
        }
    }
}

@Composable
private fun DayNavHeader(
    dayStart: Long,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    val isToday = dayStart == CalendarViewModel.startOfDay(System.currentTimeMillis())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                formatDayTitle(dayStart),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (!isToday) {
                TextButton(onClick = onToday) { Text("Today") }
            }
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
        }
    }
}

@Composable
private fun MonthHeader(
    visibleMonthStart: Long,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit,
) {
    val title = remember(visibleMonthStart) {
        val cal = Calendar.getInstance().apply { timeInMillis = visibleMonthStart }
        val month = cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())
        val year = cal.get(Calendar.YEAR)
        "$month $year"
    }
    val isCurrentMonth = remember(visibleMonthStart) {
        val sel = Calendar.getInstance().apply { timeInMillis = visibleMonthStart }
        val now = Calendar.getInstance()
        sel.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            sel.get(Calendar.MONTH) == now.get(Calendar.MONTH)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevMonth) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (!isCurrentMonth) {
                TextButton(onClick = onToday) { Text("Today") }
            }
        }
        IconButton(onClick = onNextMonth) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
}

@Composable
private fun MonthGrid(
    cells: List<MonthDayCell>,
    selectedDayStart: Long?,
    todayStart: Long,
    onSelect: (Long) -> Unit,
) {
    val weekdays = remember {
        val labels = ArrayList<String>(7)
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        repeat(7) {
            labels += cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault())
                ?.take(2)
                .orEmpty()
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        labels
    }
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            weekdays.forEach { label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    MonthDayCellView(
                        cell = cell,
                        selected = cell.dayStartMillis == selectedDayStart,
                        isToday = cell.dayStartMillis == todayStart,
                        onClick = { onSelect(cell.dayStartMillis) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthDayCellView(
    cell: MonthDayCell,
    selected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val muted = !cell.inCurrentMonth
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(TaskDavRadii.chip))
            .then(
                when {
                    isToday -> Modifier.background(MaterialTheme.colorScheme.primary)
                    selected -> Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                    else -> Modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest)
                },
            )
            .clickable(onClick = onClick),
    ) {
        // Day number is always dead-center in the cell (same for every day).
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                cell.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isToday || selected) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                color = when {
                    isToday -> MaterialTheme.colorScheme.onPrimary
                    selected -> MaterialTheme.colorScheme.onPrimaryContainer
                    muted -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
        // Fixed bottom lane so dots are centered under the number without shifting it.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(10.dp)
                .padding(bottom = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                cell.eventColorsArgb.forEach { argb ->
                    val color = collectionColorOrDefault(argb)
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isToday -> color
                                    muted -> color.copy(alpha = 0.35f)
                                    else -> color
                                },
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun EventRow(
    item: CalendarDayItem,
    onClick: () -> Unit,
    onOpenTask: (() -> Unit)?,
    onShare: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val event = item.event
    val color = collectionColorOrDefault(item.collection?.colorArgb)
    val linkAction = remember(event.location, event.description) {
        resolveEventLink(event.location, event.description)
    }
    val hasRevealActions = onShare != null || linkAction != null

    val cardContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp, end = 12.dp)
                    .width(5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
                    .padding(vertical = 28.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(event.summary, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Text(
                    timeLabel(context, event),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )
                event.description?.takeIf { it.isNotBlank() }?.let { details ->
                    Text(
                        details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val meta = buildList {
                    item.collection?.displayName?.let { add(it) }
                    item.linkedTask?.let { add("Task: ${it.summary}") }
                }.joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                event.location?.takeIf { it.isNotBlank() }?.let { loc ->
                    Text(
                        loc,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (onOpenTask != null) {
                    TextButton(
                        onClick = onOpenTask,
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                    ) {
                        Text("Open task")
                    }
                }
            }
        }
    }

    if (hasRevealActions) {
        SwipeRevealRow(
            modifier = modifier,
            actions = { close ->
                if (onShare != null) {
                    SwipeRevealAction(
                        icon = Icons.Default.Share,
                        contentDescription = "Share",
                        onClick = {
                            onShare()
                            close()
                        },
                    )
                }
                if (linkAction != null) {
                    SwipeRevealAction(
                        icon = linkAction.icon,
                        contentDescription = linkAction.label,
                        onClick = {
                            openEventLink(context, event.location, event.description)
                            close()
                        },
                    )
                }
            },
            content = cardContent,
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            cardContent()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventEditorScreen(
    isNew: Boolean,
    summary: String,
    description: String,
    location: String,
    startMillis: Long,
    endMillis: Long,
    collectionId: Long?,
    collections: List<CollectionEntity>,
    recurrence: EventRecurrence.EditState,
    error: String?,
    onSummary: (String) -> Unit,
    onDescription: (String) -> Unit,
    onLocation: (String) -> Unit,
    onCollection: (Long) -> Unit,
    onRecurrence: (EventRecurrence.EditState) -> Unit,
    onStart: (Long) -> Unit,
    onEnd: (Long) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    onShare: (() -> Unit)? = null,
) {
    var collectionExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val selected = collections.find { it.id == collectionId }
    val repeats = recurrence.mode != EventRecurrence.Mode.NONE

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "New event" else "Edit event") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (onShare != null) {
                        IconButton(onClick = onShare) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                    }
                    TextButton(onClick = onSave) {
                        Text("Save")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = summary,
                onValueChange = onSummary,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Title") },
                singleLine = true,
            )
            OutlinedTextField(
                value = description,
                onValueChange = onDescription,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Description") },
                minLines = 3,
            )
            if (isNew && collections.isNotEmpty()) {
                ExposedDropdownMenuBox(
                    expanded = collectionExpanded,
                    onExpandedChange = { collectionExpanded = it },
                ) {
                    OutlinedTextField(
                        value = selected?.displayName ?: "Calendar",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Calendar") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(collectionExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = collectionExpanded,
                        onDismissRequest = { collectionExpanded = false },
                    ) {
                        collections.forEach { col ->
                            DropdownMenuItem(
                                text = { Text(col.displayName) },
                                onClick = {
                                    onCollection(col.id)
                                    collectionExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            Text(
                "When",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            InlineDateTimePicker(
                label = "Starts",
                valueMillis = startMillis,
                onValueChange = onStart,
            )
            InlineDateTimePicker(
                label = "Ends",
                valueMillis = endMillis,
                onValueChange = onEnd,
            )

            Text(
                "Repeat",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ExpressiveFilterChip(
                    label = "Never",
                    selected = recurrence.mode == EventRecurrence.Mode.NONE,
                    onClick = {
                        onRecurrence(recurrence.copy(mode = EventRecurrence.Mode.NONE))
                    },
                )
                ExpressiveFilterChip(
                    label = "Weekly",
                    selected = recurrence.mode == EventRecurrence.Mode.WEEKLY,
                    onClick = {
                        onRecurrence(recurrence.copy(mode = EventRecurrence.Mode.WEEKLY))
                    },
                )
                ExpressiveFilterChip(
                    label = "Every 2 weeks",
                    selected = recurrence.mode == EventRecurrence.Mode.EVERY_2_WEEKS,
                    onClick = {
                        onRecurrence(recurrence.copy(mode = EventRecurrence.Mode.EVERY_2_WEEKS))
                    },
                )
                ExpressiveFilterChip(
                    label = "Every N weeks",
                    selected = recurrence.mode == EventRecurrence.Mode.EVERY_N_WEEKS,
                    onClick = {
                        onRecurrence(
                            recurrence.copy(
                                mode = EventRecurrence.Mode.EVERY_N_WEEKS,
                                intervalWeeks = recurrence.intervalWeeks.coerceAtLeast(3),
                            ),
                        )
                    },
                )
                if (recurrence.mode == EventRecurrence.Mode.OTHER) {
                    ExpressiveFilterChip(
                        label = "Custom",
                        selected = true,
                        onClick = {},
                    )
                }
            }
            if (recurrence.mode == EventRecurrence.Mode.EVERY_N_WEEKS) {
                OutlinedTextField(
                    value = recurrence.intervalWeeks.toString(),
                    onValueChange = { raw ->
                        val n = raw.filter { it.isDigit() }.toIntOrNull()?.coerceIn(1, 52) ?: 1
                        onRecurrence(recurrence.copy(intervalWeeks = n))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Repeat every (weeks)") },
                    singleLine = true,
                )
            }
            if (recurrence.mode == EventRecurrence.Mode.OTHER) {
                Text(
                    recurrence.otherRrule.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                TextButton(
                    onClick = {
                        onRecurrence(EventRecurrence.EditState())
                    },
                ) { Text("Clear custom rule") }
            }
            if (repeats && recurrence.mode != EventRecurrence.Mode.OTHER) {
                Text(
                    "Ends",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ExpressiveFilterChip(
                        label = "Never",
                        selected = recurrence.count == null,
                        onClick = { onRecurrence(recurrence.copy(count = null)) },
                    )
                    ExpressiveFilterChip(
                        label = "After N times",
                        selected = recurrence.count != null,
                        onClick = {
                            onRecurrence(recurrence.copy(count = recurrence.count ?: 10))
                        },
                    )
                }
                if (recurrence.count != null) {
                    OutlinedTextField(
                        value = recurrence.count.toString(),
                        onValueChange = { raw ->
                            val n = raw.filter { it.isDigit() }.toIntOrNull()?.coerceIn(1, 999)
                            onRecurrence(recurrence.copy(count = n ?: 1))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Number of occurrences") },
                        singleLine = true,
                    )
                }
            }

            Text(
                "Location",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = location,
                onValueChange = onLocation,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Location or meeting link") },
                singleLine = true,
                placeholder = { Text("Address, Meet, Zoom…") },
            )
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            if (onDelete != null) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { showDeleteConfirm = true }) {
                    Text("Delete event", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    if (showDeleteConfirm && onDelete != null) {
        ConfirmDeleteDialog(
            title = "Delete event?",
            body = "This permanently removes the event from the app and the server.",
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

private fun timeLabel(context: android.content.Context, event: EventEntity): String {
    if (event.allDay) return "All day"
    val start = event.dtStartMillis ?: return "No time"
    val end = event.dtEndMillis
    return buildString {
        append(DateFormats.dateTime(context, start))
        if (end != null) {
            append(" – ")
            val sameDay = CalendarViewModel.startOfDay(start) == CalendarViewModel.startOfDay(end)
            if (sameDay) append(DateFormats.time(context, end))
            else append(DateFormats.dateTime(context, end))
        }
    }
}

private fun formatDayTitle(dayStart: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = dayStart }
    val today = CalendarViewModel.startOfDay(System.currentTimeMillis())
    return when (dayStart) {
        today -> "Today"
        today - CalendarViewModel.DAY_MS -> "Yesterday"
        today + CalendarViewModel.DAY_MS -> "Tomorrow"
        else -> {
            val day = cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault())
            val month = cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault())
            val d = cal.get(Calendar.DAY_OF_MONTH)
            "$day, $d $month"
        }
    }
}
