package app.taskdav.ui.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.taskdav.ui.common.DateFormats
import app.taskdav.ui.common.DenseAgendaRow
import app.taskdav.ui.common.DockScrollPadding
import app.taskdav.ui.common.EmptyLine
import app.taskdav.ui.common.PeekCard
import app.taskdav.ui.common.PulseChip
import app.taskdav.ui.common.SectionLabel
import app.taskdav.ui.theme.collectionColorOrDefault
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val hasThisWeek = ui.thisWeekEvents.isNotEmpty() || ui.thisWeekTasks.isNotEmpty()
    val comingUpPeeks = remember(ui.upcomingEvents, ui.upcomingTasks) {
        (ui.upcomingEvents + ui.upcomingTasks).take(8)
    }
    val thisWeekPeeks = remember(ui.thisWeekEvents, ui.thisWeekTasks) {
        (ui.thisWeekEvents + ui.thisWeekTasks).take(10)
    }
    val dateLabel = remember {
        SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date())
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { _ ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = DockScrollPadding,
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            ui.greeting,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            dateLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        buildString {
                            append(ui.stats.openTasks)
                            append(" open")
                            if (ui.stats.overdueTasks > 0) {
                                append(" · ")
                                append(ui.stats.overdueTasks)
                                append(" late")
                            }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (ui.stats.overdueTasks > 0) {
                        PulseChip(
                            text = "${ui.stats.overdueTasks} overdue",
                            onClick = onSeeAllTasks,
                            alert = true,
                        )
                    }
                    PulseChip(
                        text = "${ui.stats.dueTodayTasks} due today",
                        onClick = onSeeAllTasks,
                    )
                    PulseChip(
                        text = "${ui.stats.todayEvents} events",
                        onClick = onSeeCalendar,
                    )
                    PulseChip(
                        text = "${ui.stats.notes} notes",
                        onClick = onSeeAllNotes,
                    )
                }
            }

            if (ui.overdue.isNotEmpty()) {
                item {
                    SectionLabel(title = "Needs attention", actionLabel = "All", onAction = onSeeAllTasks)
                }
                items(ui.overdue.take(4), key = { "od-${it.id}" }) { item ->
                    DenseAgendaRow(
                        title = item.title,
                        meta = item.subtitle,
                        trailing = item.atMillis?.let { DateFormats.date(context, it) },
                        onClick = { onOpenTask(item.id) },
                        accent = collectionColorOrDefault(item.colorArgb),
                        emphasis = true,
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                SectionLabel(title = "Today", actionLabel = "Calendar", onAction = onSeeCalendar)
            }
            if (!todayBusy) {
                item { EmptyLine("Clear day.") }
            } else {
                items(ui.todayEvents, key = { "te-${it.id}" }) { item ->
                    DenseAgendaRow(
                        title = item.title,
                        meta = listOfNotNull("Event", item.subtitle).joinToString(" · "),
                        trailing = eventTimeLabel(context, item),
                        onClick = onSeeCalendar,
                        accent = collectionColorOrDefault(item.colorArgb),
                    )
                }
                items(ui.dueToday, key = { "td-${it.id}" }) { item ->
                    DenseAgendaRow(
                        title = item.title,
                        meta = listOfNotNull("Task", item.subtitle).joinToString(" · "),
                        trailing = item.atMillis?.let { DateFormats.time(context, it) } ?: "Due",
                        onClick = { onOpenTask(item.id) },
                        accent = collectionColorOrDefault(item.colorArgb),
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )
                Spacer(modifier = Modifier.height(4.dp))
                SectionLabel(title = "This week", actionLabel = "Calendar", onAction = onSeeCalendar)
            }
            if (!hasThisWeek) {
                item { EmptyLine("Nothing else this week.") }
            } else {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        thisWeekPeeks.forEach { item ->
                            PeekCard(
                                title = item.title,
                                meta = item.atMillis?.let { DateFormats.date(context, it) }
                                    ?: item.subtitle,
                                onClick = {
                                    when (item.kind) {
                                        HomeItemKind.TASK -> onOpenTask(item.id)
                                        HomeItemKind.NOTE -> onOpenNote(item.id)
                                        HomeItemKind.EVENT -> onSeeCalendar()
                                    }
                                },
                                accent = collectionColorOrDefault(item.colorArgb),
                            )
                        }
                    }
                }
            }

            if (comingUpPeeks.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SectionLabel(title = "Coming up", actionLabel = "Calendar", onAction = onSeeCalendar)
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        comingUpPeeks.forEach { item ->
                            PeekCard(
                                title = item.title,
                                meta = item.atMillis?.let { DateFormats.date(context, it) }
                                    ?: item.subtitle,
                                onClick = {
                                    when (item.kind) {
                                        HomeItemKind.TASK -> onOpenTask(item.id)
                                        HomeItemKind.NOTE -> onOpenNote(item.id)
                                        HomeItemKind.EVENT -> onSeeCalendar()
                                    }
                                },
                                accent = collectionColorOrDefault(item.colorArgb),
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )
                Spacer(modifier = Modifier.height(4.dp))
                SectionLabel(title = "Notes", actionLabel = "All", onAction = onSeeAllNotes)
            }
            if (ui.recentNotes.isEmpty()) {
                item { EmptyLine("No notes yet.") }
            } else {
                items(ui.recentNotes.take(3), key = { "n-${it.id}" }) { item ->
                    DenseAgendaRow(
                        title = item.title,
                        meta = item.subtitle,
                        trailing = item.atMillis?.let { DateFormats.date(context, it) },
                        onClick = { onOpenNote(item.id) },
                        accent = collectionColorOrDefault(item.colorArgb),
                    )
                }
            }
        }
    }
}

private fun eventTimeLabel(context: android.content.Context, item: HomeAgendaItem): String {
    val start = item.atMillis ?: return "Event"
    return if (item.allDay) "All day" else DateFormats.time(context, start)
}
