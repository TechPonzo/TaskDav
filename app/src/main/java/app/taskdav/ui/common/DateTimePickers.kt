package app.taskdav.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.util.Calendar
import java.util.TimeZone

/**
 * Sequential date then time picker. [initialMillis] seeds both; result is local wall time.
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

    if (step == 0) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = utcMidnightForLocalDate(initialMillis),
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = {
                        val picked = dateState.selectedDateMillis ?: return@TextButton
                        selectedDateMillis = picked
                        step = 1
                    },
                ) { Text("Next") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            },
        ) {
            DatePicker(state = dateState)
        }
    } else {
        val timeState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Select time") },
            text = { TimePicker(state = timeState) },
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
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { step = 0 }) { Text("Back") }
            },
        )
    }
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
