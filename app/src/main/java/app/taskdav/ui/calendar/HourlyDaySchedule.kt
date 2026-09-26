package app.taskdav.ui.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.taskdav.ui.common.DateFormats
import app.taskdav.ui.theme.collectionColorOrDefault
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max

private val HourHeight = 56.dp
private val GutterWidth = 52.dp
private val MinEventHeight = 18.dp

/**
 * Google Calendar–style day timeline: 24 hour rows, events as blocks,
 * tap empty hour to create, tap event to edit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HourlyDaySchedule(
    dayStartMillis: Long,
    items: List<CalendarDayItem>,
    onBack: () -> Unit,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onCreateAt: (Long) -> Unit,
    onEdit: (CalendarDayItem) -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val density = LocalDensity.current
    val hourHeightPx = with(density) { HourHeight.toPx() }
    val scroll = rememberScrollState()
    val now = System.currentTimeMillis()
    val todayStart = remember { CalendarViewModel.startOfDay(now) }
    val isToday = dayStartMillis == todayStart

    val allDay = remember(items) { items.filter { it.event.allDay } }
    val timed = remember(items) { items.filter { !it.event.allDay } }

    LaunchedEffect(dayStartMillis, isToday) {
        if (isToday) {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val target = ((hour - 1).coerceAtLeast(0) * hourHeightPx).toInt()
            scroll.scrollTo(target)
        } else {
            scroll.scrollTo(0)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(formatDayScheduleTitle(dayStartMillis))
                        Text(
                            if (isToday) "Today" else DateFormats.date(context, dayStartMillis),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onPrevDay) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous day",
                        )
                    }
                    IconButton(onClick = onNextDay) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next day",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val hour = if (isToday) {
                        Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                    } else {
                        10
                    }
                    onCreateAt(dayStartMillis + hour * TimeUnit.HOURS.toMillis(1))
                },
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add event")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (allDay.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        "All day",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.height(4.dp))
                    allDay.forEach { item ->
                        AllDayEventChip(item = item, onClick = { onEdit(item) })
                        Spacer(Modifier.height(4.dp))
                    }
                }
                HorizontalDivider()
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll),
            ) {
                val totalHeight = HourHeight * 24
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(totalHeight),
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        repeat(24) { hour ->
                            HourRow(
                                hour = hour,
                                onClick = {
                                    onCreateAt(dayStartMillis + hour * TimeUnit.HOURS.toMillis(1))
                                },
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = GutterWidth),
                    ) {
                        timed.forEach { item ->
                            val layout = timedEventLayout(
                                item = item,
                                dayStart = dayStartMillis,
                                hourHeight = HourHeight,
                            ) ?: return@forEach
                            TimedEventBlock(
                                item = item,
                                top = layout.top,
                                height = layout.height,
                                onClick = { onEdit(item) },
                            )
                        }

                        if (isToday) {
                            val minutes = Calendar.getInstance().let {
                                it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
                            }
                            val top = HourHeight * (minutes / 60f)
                            NowLine(top = top)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HourRow(hour: Int, onClick: () -> Unit) {
    val context = LocalContext.current
    val labelMillis = remember(hour) {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(HourHeight)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            DateFormats.time(context, labelMillis),
            modifier = Modifier
                .width(GutterWidth)
                .padding(end = 6.dp, top = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
            )
        }
    }
}

@Composable
private fun TimedEventBlock(
    item: CalendarDayItem,
    top: Dp,
    height: Dp,
    onClick: () -> Unit,
) {
    val color = collectionColorOrDefault(item.collection?.colorArgb)
    val context = LocalContext.current
    val start = item.event.dtStartMillis
    val end = item.event.dtEndMillis
    val timeLabel = when {
        start == null -> null
        end == null -> DateFormats.time(context, start)
        else -> "${DateFormats.time(context, start)} – ${DateFormats.time(context, end)}"
    }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 8.dp)
            .offset(y = top)
            .height(height)
            .clip(RoundedCornerShape(12.dp)),
        color = color.copy(alpha = 0.22f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(color),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    item.event.summary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (timeLabel != null && height >= 28.dp) {
                    Text(
                        timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun AllDayEventChip(item: CalendarDayItem, onClick: () -> Unit) {
    val color = collectionColorOrDefault(item.collection?.colorArgb)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.22f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            item.event.summary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NowLine(top: Dp) {
    val accent = MaterialTheme.colorScheme.tertiary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = top),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(8.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(accent),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .background(accent),
        )
    }
}

private data class EventLayout(val top: Dp, val height: Dp)

private fun timedEventLayout(
    item: CalendarDayItem,
    dayStart: Long,
    hourHeight: Dp,
): EventLayout? {
    val start = item.event.dtStartMillis ?: return null
    val dayEnd = dayStart + CalendarViewModel.DAY_MS
    val end = when {
        item.event.dtEndMillis != null && item.event.dtEndMillis > start -> item.event.dtEndMillis
        else -> start + CalendarViewModel.DEFAULT_DURATION_MS
    }
    val clampedStart = start.coerceIn(dayStart, dayEnd)
    val clampedEnd = end.coerceIn(dayStart, dayEnd)
    if (clampedEnd <= dayStart || clampedStart >= dayEnd) return null
    val startFrac = (clampedStart - dayStart).toFloat() / CalendarViewModel.DAY_MS
    val endFrac = (clampedEnd - dayStart).toFloat() / CalendarViewModel.DAY_MS
    val top = hourHeight * (startFrac * 24f)
    val rawHeight = hourHeight * ((endFrac - startFrac) * 24f)
    val height = max(rawHeight.value, MinEventHeight.value).dp
    return EventLayout(top = top, height = height)
}

private fun formatDayScheduleTitle(dayStart: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = dayStart }
    val day = cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault())
    val month = cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())
    val d = cal.get(Calendar.DAY_OF_MONTH)
    return "$day, $d $month"
}
