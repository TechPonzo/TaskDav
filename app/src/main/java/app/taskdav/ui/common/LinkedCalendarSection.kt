package app.taskdav.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.taskdav.data.EventEntity

@Composable
fun LinkedCalendarSection(
    event: EventEntity?,
    fallbackTitle: String? = null,
    onEditEvent: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (event == null && fallbackTitle.isNullOrBlank()) return
    val context = LocalContext.current
    val linkAction = remember(event?.location, event?.description) {
        resolveEventLink(event?.location, event?.description)
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Calendar", style = MaterialTheme.typography.labelMedium)
                Text(
                    event?.summary ?: fallbackTitle.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                )
                event?.dtStartMillis?.let { start ->
                    Text(
                        "Starts: ${DateFormats.dateTime(context, start)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    )
                }
                event?.dtEndMillis?.let { end ->
                    Text(
                        "Ends: ${DateFormats.dateTime(context, end)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    )
                }
                if (event?.allDay == true) {
                    Text(
                        "All-day",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    )
                }
            }
            if (onEditEvent != null && event != null) {
                IconButton(onClick = onEditEvent) {
                    Icon(Icons.Default.Edit, contentDescription = "Update calendar event")
                }
            }
        }

        val location = event?.location?.takeIf { it.isNotBlank() }
        if (location != null || linkAction != null) {
            LocationRow(
                locationText = location ?: linkAction?.label.orEmpty(),
                linkAction = linkAction,
                onOpen = {
                    openEventLink(context, event?.location, event?.description)
                },
            )
        }
    }
}

@Composable
fun LocationRow(
    locationText: String,
    linkAction: EventLinkAction?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                linkAction?.label ?: "Location",
                style = MaterialTheme.typography.labelMedium,
            )
            if (locationText.isNotBlank()) {
                Text(locationText, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (linkAction != null) {
            EventActionIconButton(
                icon = linkAction.icon,
                contentDescription = linkAction.label,
                onClick = onOpen,
            )
        }
    }
}
