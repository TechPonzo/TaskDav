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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.taskdav.data.CollectionEntity
import app.taskdav.data.EventEntity
import app.taskdav.ui.common.DateFormats
import app.taskdav.ui.common.DateTimePickerDialog
import app.taskdav.ui.common.openLocationInMaps
import app.taskdav.ui.theme.collectionColorOrDefault
import java.util.Calendar
import java.util.Locale

private val TodayDotRed = Color(0xFFE53935)
private val EventDot = Color(0xFF1B6CA8)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onOpenTask: (Long) -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val dayItems by viewModel.dayItems.collectAsStateWithLifecycle()
    val monthCells by viewModel.monthCells.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val eventCollections = remember(collections) {
        collections.filter { it.supportsVevent || it.supportsVtodo }
    }
    val todayStart = remember { CalendarViewModel.startOfDay(System.currentTimeMillis()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Calendar")
                        val subtitle = ui.error ?: ui.syncMessage
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::syncNow, enabled = !ui.syncing) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::openCreate) {
                Icon(Icons.Default.Add, contentDescription = "Add event")
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = ui.syncing,
            onRefresh = viewModel::syncNow,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
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
                if (eventCollections.size > 1) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = ui.collectionFilter == null,
                                onClick = { viewModel.setCollectionFilter(null) },
                                label = { Text("All") },
                            )
                            eventCollections.forEach { col ->
                                FilterChip(
                                    selected = ui.collectionFilter == col.id,
                                    onClick = { viewModel.setCollectionFilter(col.id) },
                                    label = { Text(col.displayName) },
                                )
                            }
                        }
                    }
                }
                val selectedDay = ui.selectedDayStartMillis
                if (selectedDay == null) {
                    item {
                        Text(
                            "Tap a day to see its events.",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                } else {
                    item {
                        Text(
                            formatDayTitle(selectedDay),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    if (dayItems.isEmpty()) {
                        item {
                            Text(
                                "No events this day. Sync or tap + to add one.",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                        }
                    } else {
                        items(dayItems, key = { it.event.id }) { item ->
                            EventRow(
                                item = item,
                                onClick = { viewModel.openEdit(item.event) },
                                onOpenTask = item.linkedTask?.let { task ->
                                    { onOpenTask(task.id) }
                                },
                                onOpenLocation = item.event.location
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { loc ->
                                        { openLocationInMaps(context, loc) }
                                    },
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (ui.showEditor) {
        EventEditorDialog(
            isNew = ui.editingEventId == null,
            summary = ui.editorSummary,
            location = ui.editorLocation,
            startMillis = ui.editorStartMillis,
            endMillis = ui.editorEndMillis,
            collectionId = ui.editorCollectionId,
            collections = eventCollections,
            error = ui.error,
            onSummary = viewModel::setEditorSummary,
            onLocation = viewModel::setEditorLocation,
            onCollection = viewModel::setEditorCollection,
            onPickStart = { showStartPicker = true },
            onPickEnd = { showEndPicker = true },
            onDismiss = viewModel::dismissEditor,
            onSave = viewModel::saveEditor,
            onDelete = if (ui.editingEventId != null) viewModel::deleteEditing else null,
        )
    }

    if (showStartPicker) {
        DateTimePickerDialog(
            initialMillis = ui.editorStartMillis,
            onDismiss = { showStartPicker = false },
            onConfirm = {
                viewModel.setEditorStart(it)
                showStartPicker = false
            },
        )
    }
    if (showEndPicker) {
        DateTimePickerDialog(
            initialMillis = ui.editorEndMillis,
            onDismiss = { showEndPicker = false },
            onConfirm = {
                viewModel.setEditorEnd(it)
                showEndPicker = false
            },
        )
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
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (selected) {
                    Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .then(
                    if (selected) {
                        Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                cell.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday || selected) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    selected -> MaterialTheme.colorScheme.onPrimary
                    muted -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(10.dp),
        ) {
            if (isToday) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(TodayDotRed),
                )
            }
            if (cell.eventCount > 0) {
                val dots = cell.eventCount.coerceAtMost(3)
                repeat(dots) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(
                                if (muted) EventDot.copy(alpha = 0.35f) else EventDot,
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
    onOpenLocation: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val event = item.event
    val color = collectionColorOrDefault(item.collection?.colorArgb)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp, end = 10.dp)
                .width(4.dp)
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
            if (onOpenTask != null || onOpenLocation != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onOpenTask != null) {
                        TextButton(onClick = onOpenTask) { Text("Open task") }
                    }
                    if (onOpenLocation != null) {
                        IconButton(onClick = onOpenLocation, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Place, contentDescription = "Open in maps")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventEditorDialog(
    isNew: Boolean,
    summary: String,
    location: String,
    startMillis: Long,
    endMillis: Long,
    collectionId: Long?,
    collections: List<CollectionEntity>,
    error: String?,
    onSummary: (String) -> Unit,
    onLocation: (String) -> Unit,
    onCollection: (Long) -> Unit,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val context = LocalContext.current
    var collectionExpanded by remember { mutableStateOf(false) }
    val selected = collections.find { it.id == collectionId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New event" else "Edit event") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = summary,
                    onValueChange = onSummary,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Title") },
                    singleLine = true,
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
                Text("Starts: ${DateFormats.dateTime(context, startMillis)}")
                OutlinedButton(onClick = onPickStart) { Text("Pick start") }
                Text("Ends: ${DateFormats.dateTime(context, endMillis)}")
                OutlinedButton(onClick = onPickEnd) { Text("Pick end") }
                OutlinedTextField(
                    value = location,
                    onValueChange = onLocation,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Location") },
                    singleLine = true,
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
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
