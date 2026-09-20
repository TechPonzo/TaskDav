package app.taskdav.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.taskdav.ui.common.DateTimePickerDialog
import app.taskdav.ui.common.LinkedCalendarSection
import app.taskdav.ui.common.joinCategories
import app.taskdav.ui.common.parseCategories
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onBack: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val editor = ui.editor

    var showDuePicker by remember { mutableStateOf(false) }
    var showEventStartPicker by remember { mutableStateOf(false) }
    var showEventEndPicker by remember { mutableStateOf(false) }
    var tagDraft by remember { mutableStateOf("") }

    LaunchedEffect(ui.saved) {
        if (ui.saved) onBack()
    }

    val isCategory = editor?.isCategory == true
    val canToggleKind = editor != null && editor.parentUid.isNullOrBlank()
    val title = when {
        editor == null -> "Task"
        editor.id == null && isCategory -> "New category"
        editor.id == null -> "New task"
        isCategory -> "Edit category"
        else -> "Edit task"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save(tagDraft) },
                        enabled = ui.ready && !ui.saving && editor != null,
                    ) {
                        Text("Save")
                    }
                },
            )
        },
    ) { padding ->
        if (!ui.ready || editor == null) {
            Text("Loading…", modifier = Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }

        val tags = parseCategories(editor.categories)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (canToggleKind) {
                Text("Type", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isCategory,
                        onClick = { viewModel.setIsCategory(false) },
                        label = { Text("Task") },
                    )
                    FilterChip(
                        selected = isCategory,
                        onClick = { viewModel.setIsCategory(true) },
                        label = { Text("Category") },
                    )
                }
                Text(
                    if (isCategory) {
                        "Categories group tasks. Nested items stay as tasks inside this category."
                    } else {
                        "Switch to Category to use this as a folder for other tasks."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }

            OutlinedTextField(
                value = editor.summary,
                onValueChange = viewModel::updateSummary,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (isCategory) "Category name" else "Title") },
                singleLine = true,
            )
            OutlinedTextField(
                value = editor.description,
                onValueChange = viewModel::updateDescription,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Details") },
                minLines = 3,
            )

            var collectionExpanded by remember { mutableStateOf(false) }
            val todoCollections = collections.filter { it.supportsVtodo }
            val selectedCollection = todoCollections.find { it.id == editor.collectionId }
            ExposedDropdownMenuBox(
                expanded = collectionExpanded,
                onExpandedChange = { collectionExpanded = it },
            ) {
                OutlinedTextField(
                    value = selectedCollection?.displayName ?: "Collection",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Task list") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(collectionExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = collectionExpanded,
                    onDismissRequest = { collectionExpanded = false },
                ) {
                    todoCollections.forEach { col ->
                        DropdownMenuItem(
                            text = { Text(col.displayName) },
                            onClick = {
                                viewModel.updateCollection(col.id)
                                collectionExpanded = false
                            },
                        )
                    }
                }
            }

            Text("Tags", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = {
                            viewModel.updateCategories(joinCategories(tags - tag))
                        },
                        label = { Text(tag) },
                        trailingIcon = {
                            Icon(Icons.Default.Close, contentDescription = "Remove $tag")
                        },
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = tagDraft,
                    onValueChange = { tagDraft = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Add tag") },
                    singleLine = true,
                )
                OutlinedButton(
                    onClick = {
                        val next = tagDraft.trim()
                        if (next.isNotEmpty()) {
                            viewModel.updateCategories(joinCategories(tags + next))
                            tagDraft = ""
                        }
                    },
                ) { Text("Add") }
            }

            if (!isCategory) {
                val priority = (editor.priority ?: 0).coerceIn(0, 9)
                Text(
                    "Priority: $priority",
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    "0 = none · 1 = highest · 9 = lowest",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    (0..9).forEach { value ->
                        FilterChip(
                            selected = priority == value,
                            onClick = { viewModel.updatePriority(value) },
                            label = { Text("$value") },
                        )
                    }
                }

                Text("Due", style = MaterialTheme.typography.titleSmall)
                Text(
                    editor.dueMillis?.let { DateFormat.getDateTimeInstance().format(Date(it)) }
                        ?: "No due date",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showDuePicker = true }) { Text("Pick date & time") }
                    OutlinedButton(onClick = { viewModel.updateDue(null) }) { Text("Clear due") }
                }

                Text("Calendar link", style = MaterialTheme.typography.titleMedium)
                val linked = editor.linkedEvent
                if (linked != null || !editor.linkedEventUid.isNullOrBlank()) {
                    LinkedCalendarSection(
                        event = linked,
                        fallbackTitle = "Linked calendar event",
                        onEditEvent = { viewModel.setShowEditEvent(true) },
                    )
                    OutlinedButton(onClick = viewModel::clearLinkedEvent) { Text("Unlink") }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.setShowEventPicker(true) }) {
                            Text("Link existing")
                        }
                        OutlinedButton(onClick = { viewModel.setShowCreateEvent(true) }) {
                            Text("Create event")
                        }
                    }
                }
            }

            ui.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = { viewModel.save(tagDraft) },
                modifier = Modifier.fillMaxWidth(),
                enabled = ui.ready && !ui.saving,
            ) {
                Text(
                    when {
                        ui.saving -> "Saving…"
                        isCategory -> "Save category"
                        else -> "Save task"
                    },
                )
            }
        }
    }

    if (showDuePicker) {
        DateTimePickerDialog(
            initialMillis = editor?.dueMillis ?: System.currentTimeMillis(),
            onDismiss = { showDuePicker = false },
            onConfirm = {
                viewModel.updateDue(it)
                showDuePicker = false
            },
        )
    }

    if (ui.showEventPicker) {
        AlertDialog(
            onDismissRequest = { viewModel.setShowEventPicker(false) },
            title = { Text("Link a calendar event") },
            text = {
                if (events.isEmpty()) {
                    Text("No calendar events synced yet. Enable a calendar that supports events, then sync.")
                } else {
                    LazyColumn {
                        items(events, key = { it.id }) { event ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.pickEvent(event) }
                                    .padding(vertical = 10.dp),
                            ) {
                                Text(event.summary)
                                event.location?.takeIf { it.isNotBlank() }?.let { loc ->
                                    Text(
                                        loc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.setShowEventPicker(false) }) { Text("Close") }
            },
        )
    }

    if (ui.showCreateEvent) {
        val taskListName = collections.find { it.id == editor?.collectionId }?.displayName
        val fmt = DateFormat.getDateTimeInstance()
        AlertDialog(
            onDismissRequest = { viewModel.setShowCreateEvent(false) },
            title = { Text("Create calendar event") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Creates a calendar event on the same list as this task" +
                            (taskListName?.let { " ($it)" } ?: "") +
                            ", so it can appear in your calendar apps.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = ui.eventSummary,
                        onValueChange = viewModel::setEventSummary,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Title") },
                        singleLine = true,
                    )
                    Text("Starts: ${fmt.format(Date(ui.eventStartMillis))}")
                    OutlinedButton(onClick = { showEventStartPicker = true }) {
                        Text("Pick start")
                    }
                    Text("Ends: ${fmt.format(Date(ui.eventEndMillis))}")
                    OutlinedButton(onClick = { showEventEndPicker = true }) {
                        Text("Pick end")
                    }
                    OutlinedTextField(
                        value = ui.eventLocation,
                        onValueChange = viewModel::setEventLocation,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Location") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::createAndLinkEvent,
                    enabled = (editor?.collectionId ?: 0L) > 0L &&
                        ui.eventEndMillis >= ui.eventStartMillis,
                ) { Text("Create & link") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setShowCreateEvent(false) }) { Text("Cancel") }
            },
        )
    }

    if (ui.showEditEvent) {
        val fmt = DateFormat.getDateTimeInstance()
        AlertDialog(
            onDismissRequest = { viewModel.setShowEditEvent(false) },
            title = { Text("Update calendar event") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ui.eventSummary,
                        onValueChange = viewModel::setEventSummary,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Title") },
                        singleLine = true,
                    )
                    Text("Starts: ${fmt.format(Date(ui.eventStartMillis))}")
                    OutlinedButton(onClick = { showEventStartPicker = true }) {
                        Text("Pick start")
                    }
                    Text("Ends: ${fmt.format(Date(ui.eventEndMillis))}")
                    OutlinedButton(onClick = { showEventEndPicker = true }) {
                        Text("Pick end")
                    }
                    OutlinedTextField(
                        value = ui.eventLocation,
                        onValueChange = viewModel::setEventLocation,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Location") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::saveLinkedEvent,
                    enabled = ui.eventEndMillis >= ui.eventStartMillis,
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setShowEditEvent(false) }) { Text("Cancel") }
            },
        )
    }

    if (showEventStartPicker) {
        DateTimePickerDialog(
            initialMillis = ui.eventStartMillis,
            onDismiss = { showEventStartPicker = false },
            onConfirm = {
                viewModel.setEventStart(it)
                showEventStartPicker = false
            },
        )
    }
    if (showEventEndPicker) {
        DateTimePickerDialog(
            initialMillis = ui.eventEndMillis,
            onDismiss = { showEventEndPicker = false },
            onConfirm = {
                viewModel.setEventEnd(it)
                showEventEndPicker = false
            },
        )
    }
}

