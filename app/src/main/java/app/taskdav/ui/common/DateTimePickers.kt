package app.taskdav.ui.common

import android.content.res.Configuration
import android.os.LocaleList
import android.text.format.DateFormat as AndroidDateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.taskdav.R
import app.taskdav.data.DateOrderPreference
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Material date + time editor for forms.
 *
 * Shows compact date/time chips; each opens a proper Material dialog.
 * (Embedding [DatePicker] inside [AlertDialog] content breaks the calendar grid.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InlineDateTimePicker(
    label: String,
    valueMillis: Long,
    onValueChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dateLabel = rememberFormattedDate(valueMillis)
    val is24Hour = remember(context) { AndroidDateFormat.is24HourFormat(context) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = false,
                onClick = { showDatePicker = true },
                label = { Text(dateLabel) },
                leadingIcon = {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                },
            )
            FilterChip(
                selected = false,
                onClick = { showTimePicker = true },
                label = { Text(DateFormats.time(context, valueMillis)) },
                leadingIcon = {
                    Icon(Icons.Outlined.Schedule, contentDescription = null)
                },
            )
        }
    }

    if (showDatePicker) {
        AppDatePickerDialog(
            initialMillis = valueMillis,
            onDismiss = { showDatePicker = false },
            onConfirmUtcMidnight = { picked ->
                val cal = Calendar.getInstance().apply { timeInMillis = valueMillis }
                onValueChange(
                    combineUtcDateWithLocalTime(
                        picked,
                        cal.get(Calendar.HOUR_OF_DAY),
                        cal.get(Calendar.MINUTE),
                    ),
                )
                showDatePicker = false
            },
        )
    }

    if (showTimePicker) {
        val cal = remember(valueMillis) {
            Calendar.getInstance().apply { timeInMillis = valueMillis }
        }
        val timeState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = is24Hour,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.datetime_select_time)) },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    TimePicker(state = timeState)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val base = Calendar.getInstance().apply { timeInMillis = valueMillis }
                        base.set(Calendar.HOUR_OF_DAY, timeState.hour)
                        base.set(Calendar.MINUTE, timeState.minute)
                        base.set(Calendar.SECOND, 0)
                        base.set(Calendar.MILLISECOND, 0)
                        onValueChange(base.timeInMillis)
                        showTimePicker = false
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * Sequential date then time picker dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialog(
    initialMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    var step by remember { mutableStateOf(0) } // 0 = date, 1 = time
    var selectedDateMillis by remember { mutableStateOf(initialMillis) }

    val cal = remember(initialMillis) {
        Calendar.getInstance().apply { timeInMillis = initialMillis }
    }
    val is24Hour = AndroidDateFormat.is24HourFormat(LocalContext.current)

    if (step == 0) {
        AppDatePickerDialog(
            initialMillis = initialMillis,
            onDismiss = onDismiss,
            onConfirmUtcMidnight = { picked ->
                selectedDateMillis = picked
                step = 1
            },
            confirmLabel = stringResource(R.string.next),
        )
    } else {
        val timeState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = is24Hour,
        )
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.datetime_select_time)) },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    TimePicker(state = timeState)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onConfirm(
                            combineUtcDateWithLocalTime(
                                selectedDateMillis,
                                timeState.hour,
                                timeState.minute,
                            ),
                        )
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { step = 0 }) { Text(stringResource(R.string.back)) }
            },
        )
    }
}

/**
 * Stock Material date picker UI, with locale forced to the app date-order setting
 * so typed dates use dd/MM/yyyy (or MM/dd/yyyy) instead of the phone default.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppDatePickerDialog(
    initialMillis: Long,
    onDismiss: () -> Unit,
    onConfirmUtcMidnight: (Long) -> Unit,
    confirmLabel: String? = null,
) {
    val resolvedConfirmLabel = confirmLabel ?: stringResource(R.string.action_ok)
    val dateOrder = LocalDateOrder.current
    val initialUtc = remember(initialMillis) { utcMidnightForLocalDate(initialMillis) }

    ProvideDateOrderLocale(dateOrder) {
        val pickerLocale = LocalConfiguration.current.locales.let { locales ->
            if (locales.isEmpty) Locale.getDefault() else locales[0]
        }
        val dateState = remember(pickerLocale, initialUtc) {
            DatePickerState(
                locale = pickerLocale,
                initialSelectedDateMillis = initialUtc,
                initialDisplayMode = DisplayMode.Picker,
            )
        }

        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = {
                        val picked = dateState.selectedDateMillis ?: return@TextButton
                        onConfirmUtcMidnight(picked)
                    },
                ) { Text(resolvedConfirmLabel) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            // Re-provide inside the dialog window so text-input mode keeps our order.
            ProvideDateOrderLocale(dateOrder) {
                DatePicker(state = dateState)
            }
        }
    }
}

@Composable
private fun ProvideDateOrderLocale(
    dateOrder: DateOrderPreference,
    content: @Composable () -> Unit,
) {
    if (dateOrder == DateOrderPreference.SYSTEM) {
        content()
        return
    }
    val baseContext = LocalContext.current
    val baseConfig = LocalConfiguration.current
    val baseLocale = remember(baseConfig) {
        val locales = baseConfig.locales
        if (!locales.isEmpty) locales[0] else Locale.getDefault()
    }
    val pickerLocale = remember(dateOrder, baseLocale) {
        DateFormats.localeForDateInput(baseLocale, dateOrder)
    }
    val overriddenConfig = remember(baseConfig, pickerLocale) {
        Configuration(baseConfig).apply {
            setLocales(LocaleList(pickerLocale))
        }
    }
    val pickerContext = remember(baseContext, overriddenConfig) {
        baseContext.createConfigurationContext(overriddenConfig)
    }
    CompositionLocalProvider(
        LocalConfiguration provides overriddenConfig,
        LocalContext provides pickerContext,
        content = content,
    )
}

/** Material DatePicker uses UTC midnight for the selected civil day. */
private fun utcMidnightForLocalDate(localMillis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localMillis }
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    utc.clear()
    utc.set(
        local.get(Calendar.YEAR),
        local.get(Calendar.MONTH),
        local.get(Calendar.DAY_OF_MONTH),
    )
    return utc.timeInMillis
}

private fun combineUtcDateWithLocalTime(utcDateMillis: Long, hour: Int, minute: Int): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = utcDateMillis
    }
    val local = Calendar.getInstance()
    local.clear()
    local.set(
        utc.get(Calendar.YEAR),
        utc.get(Calendar.MONTH),
        utc.get(Calendar.DAY_OF_MONTH),
        hour,
        minute,
        0,
    )
    local.set(Calendar.MILLISECOND, 0)
    return local.timeInMillis
}

fun parseCategories(raw: String?): List<String> =
    raw.orEmpty()
        .split(',', ';')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

fun joinCategories(tags: List<String>): String? =
    tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        .takeIf { it.isNotEmpty() }
        ?.joinToString(",")
