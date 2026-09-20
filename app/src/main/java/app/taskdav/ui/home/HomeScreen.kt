package app.taskdav.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.taskdav.ui.common.DateFormats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenTask: (Long) -> Unit,
    onOpenNote: (Long) -> Unit,
    onSeeAllTasks: () -> Unit,
    onSeeAllNotes: () -> Unit,
    onSeeCalendar: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val todayBusy = ui.todayEvents.isNotEmpty() || ui.dueToday.isNotEmpty()
    val hasUpcoming = ui.upcomingEvents.isNotEmpty() || ui.upcomingTasks.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(ui.greeting)
                        val syncLine = when {
                            !ui.syncMessage.isNullOrBlank() -> ui.syncMessage
                            ui.lastSyncAt > 0L -> "Synced ${DateFormats.dateTime(context, ui.lastSyncAt)}"
                            else -> null
                        }
                        if (!syncLine.isNullOrBlank()) {
                            Text(
                                syncLine,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                StatsRow(
                    stats = ui.stats,
                    onOpenTasks = onSeeAllTasks,
                    onOpenCalendar = onSeeCalendar,
                    onOpenNotes = onSeeAllNotes,
                )
            }

            if (ui.overdue.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Overdue",
                        actionLabel = "Tasks",
                        onAction = onSeeAllTasks,
                    )
                }
                items(ui.overdue, key = { "od-${it.id}" }) { item ->
                    AgendaRow(
                        item = item,
                        emphasis = true,
                        timeLabel = item.atMillis?.let { DateFormats.date(context, it) },
                        onClick = { onOpenTask(item.id) },
                    )
                }
            }

            item {
                SectionHeader(
                    title = "Today",
                    actionLabel = "Calendar",
                    onAction = onSeeCalendar,
                )
            }
            if (!todayBusy) {
                item {
                    EmptyHint("Nothing scheduled for today.")
                }
            } else {
                items(ui.todayEvents, key = { "te-${it.id}" }) { item ->
                    AgendaRow(
                        item = item,
                        timeLabel = eventTimeLabel(context, item),
                        onClick = onSeeCalendar,
                    )
                }
                items(ui.dueToday, key = { "td-${it.id}" }) { item ->
                    AgendaRow(
                        item = item,
                        timeLabel = item.atMillis?.let { DateFormats.time(context, it) } ?: "Due today",
                        onClick = { onOpenTask(item.id) },
                    )
                }
            }

            if (hasUpcoming) {
                item {
                    SectionHeader(
                        title = "Coming up",
                        actionLabel = null,
                        onAction = null,
                    )
                }
                items(ui.upcomingEvents, key = { "ue-${it.id}" }) { item ->
                    AgendaRow(
                        item = item,
                        timeLabel = item.atMillis?.let { DateFormats.dateTime(context, it) },
                        onClick = onSeeCalendar,
                    )
                }
                items(ui.upcomingTasks, key = { "ut-${it.id}" }) { item ->
                    AgendaRow(
                        item = item,
                        timeLabel = item.atMillis?.let { "Due ${DateFormats.date(context, it)}" },
                        onClick = { onOpenTask(item.id) },
                    )
                }
            }

            item {
                SectionHeader(
                    title = "Recent notes",
                    actionLabel = "See all",
                    onAction = onSeeAllNotes,
                )
            }
            if (ui.recentNotes.isEmpty()) {
                item { EmptyHint("No notes yet.") }
            } else {
                items(ui.recentNotes, key = { "n-${it.id}" }) { item ->
                    AgendaRow(
                        item = item,
                        timeLabel = item.atMillis?.let { DateFormats.date(context, it) },
                        onClick = { onOpenNote(item.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsRow(
    stats: HomeStats,
    onOpenTasks: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenNotes: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatTile(
            label = "Open",
            value = stats.openTasks.toString(),
            icon = Icons.Outlined.CheckCircle,
            onClick = onOpenTasks,
        )
        if (stats.overdueTasks > 0) {
            StatTile(
                label = "Overdue",
                value = stats.overdueTasks.toString(),
                icon = Icons.Outlined.WarningAmber,
                tintError = true,
                onClick = onOpenTasks,
            )
        }
        StatTile(
            label = "Due today",
            value = stats.dueTodayTasks.toString(),
            icon = Icons.Outlined.CheckCircle,
            onClick = onOpenTasks,
        )
        StatTile(
            label = "Today",
            value = stats.todayEvents.toString(),
            icon = Icons.Outlined.Event,
            onClick = onOpenCalendar,
        )
        StatTile(
            label = "Notes",
            value = stats.notes.toString(),
            icon = Icons.Outlined.Description,
            onClick = onOpenNotes,
        )
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit,
    tintError: Boolean = false,
) {
    val contentColor = if (tintError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.8f),
                )
                Text(
                    value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun AgendaRow(
    item: HomeAgendaItem,
    timeLabel: String?,
    onClick: () -> Unit,
    emphasis: Boolean = false,
) {
    val kindLabel = when (item.kind) {
        HomeItemKind.EVENT -> "Event"
        HomeItemKind.TASK -> "Task"
        HomeItemKind.NOTE -> "Note"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (emphasis) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                val detail = listOfNotNull(kindLabel, item.subtitle)
                    .joinToString(" · ")
                Text(
                    detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!timeLabel.isNullOrBlank()) {
                Text(
                    timeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (emphasis) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    },
                )
            }
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

private fun eventTimeLabel(context: android.content.Context, item: HomeAgendaItem): String {
    val start = item.atMillis ?: return "Event"
    return if (item.allDay) "All day" else DateFormats.time(context, start)
}
