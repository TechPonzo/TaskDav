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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.taskdav.R
import app.taskdav.ui.common.DateFormats
import app.taskdav.ui.common.DenseAgendaRow
import app.taskdav.ui.common.DockScrollPadding
import app.taskdav.ui.common.EmptyLine
import app.taskdav.ui.common.PeekCard
import app.taskdav.ui.common.PulseChip
import app.taskdav.ui.common.SectionLabel
import app.taskdav.ui.theme.collectionColorOrDefault
import java.text.SimpleDateFormat
import java.util.Calendar
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
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> R.string.home_greeting_morning
            in 12..17 -> R.string.home_greeting_afternoon
            in 18..21 -> R.string.home_greeting_evening
            else -> R.string.home_greeting_hello
        }
    }.let { stringResource(it) }
    val statsLine = buildString {
        append(stringResource(R.string.home_stats_open, ui.stats.openTasks))
        if (ui.stats.overdueTasks > 0) {
            append(" · ")
            append(stringResource(R.string.home_stats_late, ui.stats.overdueTasks))
        }
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
                            greeting,
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
                        statsLine,
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
                            text = stringResource(R.string.home_chip_overdue, ui.stats.overdueTasks),
                            onClick = onSeeAllTasks,
                            alert = true,
                        )
                    }
                    PulseChip(
                        text = stringResource(R.string.home_chip_due_today, ui.stats.dueTodayTasks),
                        onClick = onSeeAllTasks,
                    )
                    PulseChip(
                        text = stringResource(R.string.home_chip_events, ui.stats.todayEvents),
                        onClick = onSeeCalendar,
                    )
                    PulseChip(
                        text = stringResource(R.string.home_chip_notes, ui.stats.notes),
                        onClick = onSeeAllNotes,
                    )
                }
            }

            if (ui.overdue.isNotEmpty()) {
                item {
                    SectionLabel(
                        title = stringResource(R.string.home_section_needs_attention),
                        actionLabel = stringResource(R.string.action_all),
                        onAction = onSeeAllTasks,
                    )
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
                SectionLabel(
                    title = stringResource(R.string.home_section_today),
                    actionLabel = stringResource(R.string.home_action_calendar),
                    onAction = onSeeCalendar,
                )
            }
            if (!todayBusy) {
                item { EmptyLine(stringResource(R.string.home_empty_clear_day)) }
            } else {
                items(ui.todayEvents, key = { "te-${it.id}" }) { item ->
                    DenseAgendaRow(
                        title = item.title,
                        meta = listOfNotNull(
                            stringResource(R.string.home_meta_event),
                            item.subtitle,
                        ).joinToString(" · "),
                        trailing = eventTimeLabel(item),
                        onClick = onSeeCalendar,
                        accent = collectionColorOrDefault(item.colorArgb),
                    )
                }
                items(ui.dueToday, key = { "td-${it.id}" }) { item ->
                    DenseAgendaRow(
                        title = item.title,
                        meta = listOfNotNull(
                            stringResource(R.string.home_meta_task),
                            item.subtitle,
                        ).joinToString(" · "),
                        trailing = item.atMillis?.let { DateFormats.time(context, it) }
                            ?: stringResource(R.string.home_trailing_due),
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
                SectionLabel(
                    title = stringResource(R.string.home_section_this_week),
                    actionLabel = stringResource(R.string.home_action_calendar),
                    onAction = onSeeCalendar,
                )
            }
            if (!hasThisWeek) {
                item { EmptyLine(stringResource(R.string.home_empty_nothing_this_week)) }
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
                    SectionLabel(
                        title = stringResource(R.string.home_section_coming_up),
                        actionLabel = stringResource(R.string.home_action_calendar),
                        onAction = onSeeCalendar,
                    )
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
                SectionLabel(
                    title = stringResource(R.string.home_section_notes),
                    actionLabel = stringResource(R.string.action_all),
                    onAction = onSeeAllNotes,
                )
            }
            if (ui.recentNotes.isEmpty()) {
                item { EmptyLine(stringResource(R.string.home_empty_no_notes)) }
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

@Composable
private fun eventTimeLabel(item: HomeAgendaItem): String {
    val context = LocalContext.current
    val start = item.atMillis ?: return stringResource(R.string.home_meta_event)
    return if (item.allDay) {
        stringResource(R.string.all_day)
    } else {
        DateFormats.time(context, start)
    }
}
