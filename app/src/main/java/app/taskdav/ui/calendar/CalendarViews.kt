package app.taskdav.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.taskdav.data.CalendarViewMode
import java.util.Calendar
import java.util.Locale

@Composable
fun CalendarViewPickerDialog(
    selected: CalendarViewMode,
    onSelect: (CalendarViewMode) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Calendar view",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            CalendarViewMode.entries.forEach { mode ->
                val isSelected = mode == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        )
                        .clickable { onSelect(mode) }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onSelect(mode) },
                    )
                    Text(
                        mode.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Close")
            }
        }
    }
}

@Composable
fun YearHeader(
    year: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    val thisYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous year")
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                year.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (year != thisYear) {
                TextButton(onClick = onToday) { Text("Today") }
            }
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next year")
        }
    }
}

@Composable
fun YearGrid(
    year: Int,
    months: List<List<MonthDayCell>>,
    todayStart: Long,
    selectedDayStart: Long?,
    onSelectDay: (Long) -> Unit,
    onSelectMonth: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        months.chunked(3).forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEachIndexed { colIndex, cells ->
                    val monthIndex = rowIndex * 3 + colIndex
                    MiniMonth(
                        year = year,
                        monthIndex = monthIndex,
                        cells = cells,
                        todayStart = todayStart,
                        selectedDayStart = selectedDayStart,
                        onSelectDay = onSelectDay,
                        onSelectMonth = { onSelectMonth(monthIndex) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MiniMonth(
    year: Int,
    monthIndex: Int,
    cells: List<MonthDayCell>,
    todayStart: Long,
    selectedDayStart: Long?,
    onSelectDay: (Long) -> Unit,
    onSelectMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val monthName = remember(year, monthIndex) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthIndex)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault())
            ?.uppercase(Locale.getDefault())
            .orEmpty()
    }
    Column(modifier = modifier) {
        Text(
            monthName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelectMonth)
                .padding(bottom = 2.dp),
            textAlign = TextAlign.Center,
        )
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    val isToday = cell.dayStartMillis == todayStart
                    val selected = cell.dayStartMillis == selectedDayStart
                    val hasEvents = cell.eventColorsArgb.isNotEmpty()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clickable {
                                if (cell.inCurrentMonth) onSelectDay(cell.dayStartMillis)
                                else onSelectMonth()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (cell.inCurrentMonth) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .then(
                                        when {
                                            isToday -> Modifier
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primaryContainer)
                                            selected -> Modifier
                                                .clip(CircleShape)
                                                .background(
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                                )
                                            else -> Modifier
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    cell.dayOfMonth.toString(),
                                    fontSize = 9.sp,
                                    lineHeight = 9.sp,
                                    fontWeight = if (hasEvents || isToday) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Normal
                                    },
                                    color = when {
                                        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                                        hasEvents -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                    },
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                            includeFontPadding = false,
                                        ),
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WeekStrip(
    weekStart: Long,
    selectedDayStart: Long?,
    todayStart: Long,
    eventDays: Set<Long>,
    onSelect: (Long) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(7) { i ->
            val day = weekStart + i * CalendarViewModel.DAY_MS
            val cal = remember(day) { Calendar.getInstance().apply { timeInMillis = day } }
            val label = cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault())
                ?.take(2).orEmpty()
            val selected = day == selectedDayStart
            val isToday = day == todayStart
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        when {
                            isToday -> Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                            selected -> Modifier.background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            )
                            else -> Modifier
                        },
                    )
                    .clickable { onSelect(day) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Text(
                    cal.get(Calendar.DAY_OF_MONTH).toString(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isToday) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                if (day in eventDays) {
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(
                                if (isToday) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.primary,
                            ),
                    )
                } else {
                    Spacer(modifier = Modifier.height(9.dp))
                }
            }
        }
    }
}
