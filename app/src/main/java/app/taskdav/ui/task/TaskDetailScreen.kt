package app.taskdav.ui.task

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.taskdav.R
import app.taskdav.data.TaskEntity
import app.taskdav.domain.TaskTreeBuilder
import app.taskdav.ui.common.DateFormats
import app.taskdav.ui.common.InlineDateTimePicker
import app.taskdav.ui.common.ItemShare
import app.taskdav.ui.common.LinkedCalendarSection
import app.taskdav.ui.common.parseCategories

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    viewModel: TaskDetailViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenSubtask: (Long) -> Unit = {},
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val task = ui.task
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(ui.deleted) {
        if (ui.deleted) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when {
                                task == null -> R.string.task_detail_title
                                task.isCategory -> R.string.task_detail_category
                                else -> R.string.task_detail_title
                            },
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    if (task != null) {
                        IconButton(onClick = { ItemShare.shareTask(context, task) }) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = stringResource(R.string.action_share),
                            )
                        }
                        IconButton(onClick = onEdit) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(R.string.action_edit),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            ui.loading -> {
                Text(
                    stringResource(R.string.loading),
                    modifier = Modifier.padding(padding).padding(16.dp),
                )
            }
            task == null -> {
                Text(
                    stringResource(R.string.task_detail_not_found),
                    modifier = Modifier.padding(padding).padding(16.dp),
                )
            }
            else -> {
                val completed = viewModel.isCompleted(task)
                val started = viewModel.isStarted(task)
                val tags = parseCategories(task.categories)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(task.summary, style = MaterialTheme.typography.headlineSmall)

                    val statusLabel = when {
                        task.isCategory -> stringResource(R.string.task_detail_category)
                        completed -> stringResource(R.string.task_detail_status_done)
                        started -> stringResource(R.string.task_detail_status_started)
                        else -> task.status ?: stringResource(R.string.task_detail_status_needs_action)
                    }
                    Text(statusLabel, style = MaterialTheme.typography.labelLarge)

                    if (!task.description.isNullOrBlank()) {
                        Text(task.description, style = MaterialTheme.typography.bodyLarge)
                    }

                    ui.collection?.let { col ->
                        DetailRow(
                            label = stringResource(R.string.task_detail_label_task_list),
                            value = col.displayName,
                        )
                    }

                    if (tags.isNotEmpty()) {
                        DetailRow(
                            label = stringResource(R.string.task_detail_label_tags),
                            value = tags.joinToString(", "),
                        )
                    }

                    val priority = task.priority ?: 0
                    if (!task.isCategory && priority > 0) {
                        DetailRow(
                            label = stringResource(R.string.task_detail_label_priority),
                            value = priority.toString(),
                        )
                    }

                    if (ui.subtasks.isNotEmpty()) {
                        Text(
                            stringResource(
                                if (task.isCategory) {
                                    R.string.task_detail_tasks_in_category
                                } else {
                                    R.string.task_detail_subtasks
                                },
                                ui.subtasks.size,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            ui.subtasks.forEach { child ->
                                SubtaskRow(
                                    task = child,
                                    onClick = { onOpenSubtask(child.id) },
                                )
                            }
                        }
                    }

                    task.dueMillis?.let {
                        DetailRow(
                            label = stringResource(R.string.task_detail_label_due),
                            value = DateFormats.dateTime(context, it),
                        )
                    }

                    when {
                        ui.linkedEvent != null -> {
                            LinkedCalendarSection(
                                event = ui.linkedEvent,
                                onEditEvent = { viewModel.setShowEditEvent(true) },
                            )
                        }
                        !task.linkedEventUid.isNullOrBlank() -> {
                            Text(
                                stringResource(R.string.task_detail_calendar),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(
                                stringResource(R.string.task_detail_calendar_missing),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                        }
                    }

                    task.dtStartMillis?.let {
                        DetailRow(
                            label = stringResource(R.string.task_detail_label_started),
                            value = DateFormats.dateTime(context, it),
                        )
                    }

                    task.completedMillis?.let {
                        DetailRow(
                            label = stringResource(R.string.task_detail_label_ended),
                            value = DateFormats.dateTime(context, it),
                        )
                    }

                    ui.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }

                    if (!task.isCategory) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(
                                onClick = viewModel::startTask,
                                enabled = !completed,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    stringResource(
                                        if (started) {
                                            R.string.task_detail_restart
                                        } else {
                                            R.string.task_detail_start
                                        },
                                    ),
                                )
                            }
                            Button(
                                onClick = viewModel::endTask,
                                enabled = !completed,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.task_detail_end))
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.action_edit))
                    }

                    if (task.parentUid.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = viewModel::convertKind,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(
                                    if (task.isCategory) {
                                        R.string.task_detail_convert_to_task
                                    } else {
                                        R.string.task_detail_convert_to_category
                                    },
                                ),
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text(
                            stringResource(
                                if (task.isCategory) {
                                    R.string.task_detail_delete_category
                                } else {
                                    R.string.task_detail_delete_task
                                },
                            ),
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm && task != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    stringResource(
                        if (task.isCategory) {
                            R.string.task_detail_delete_title_category
                        } else {
                            R.string.task_detail_delete_title_task
                        },
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        if (task.isCategory) {
                            R.string.task_detail_delete_body_category
                        } else {
                            R.string.task_detail_delete_body_task
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteTask()
                    },
                ) {
                    Text(
                        stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (ui.showEditEvent) {
        AlertDialog(
            onDismissRequest = { viewModel.setShowEditEvent(false) },
            title = { Text(stringResource(R.string.editor_update_event_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ui.eventSummary,
                        onValueChange = viewModel::setEventSummary,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.editor_event_title)) },
                        singleLine = true,
                    )
                    InlineDateTimePicker(
                        label = stringResource(R.string.editor_event_starts),
                        valueMillis = ui.eventStartMillis,
                        onValueChange = viewModel::setEventStart,
                    )
                    InlineDateTimePicker(
                        label = stringResource(R.string.editor_event_ends),
                        valueMillis = ui.eventEndMillis,
                        onValueChange = viewModel::setEventEnd,
                    )
                    OutlinedTextField(
                        value = ui.eventLocation,
                        onValueChange = viewModel::setEventLocation,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.editor_event_location)) },
                        singleLine = true,
                    )
                    ui.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::saveLinkedEvent,
                    enabled = ui.eventEndMillis >= ui.eventStartMillis,
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setShowEditEvent(false) }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SubtaskRow(
    task: TaskEntity,
    onClick: () -> Unit,
) {
    val done = TaskTreeBuilder.isCompleted(task)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                task.summary,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = buildList {
                when {
                    done -> add(stringResource(R.string.task_detail_status_done))
                    TaskTreeBuilder.isStarted(task) -> add(stringResource(R.string.task_detail_status_started))
                    !task.status.isNullOrBlank() -> add(task.status)
                }
                if (!task.linkedEventUid.isNullOrBlank()) {
                    add(stringResource(R.string.task_detail_meta_linked_event))
                }
            }.joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.labelSmall)
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
