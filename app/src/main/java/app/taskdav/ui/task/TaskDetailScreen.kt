package app.taskdav.ui.task

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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import app.taskdav.ui.common.parseCategories
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    viewModel: TaskDetailViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val task = ui.task
    val fmt = DateFormat.getDateTimeInstance()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(ui.deleted) {
        if (ui.deleted) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            task == null -> "Task"
                            task.isCategory -> "Category"
                            else -> "Task"
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (task != null) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            ui.loading -> {
                Text("Loading…", modifier = Modifier.padding(padding).padding(16.dp))
            }
            task == null -> {
                Text(
                    "Task not found",
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
                        task.isCategory -> "Category"
                        completed -> "Done"
                        started -> "Started"
                        else -> task.status ?: "Needs action"
                    }
                    Text(statusLabel, style = MaterialTheme.typography.labelLarge)

                    if (!task.description.isNullOrBlank()) {
                        Text(task.description, style = MaterialTheme.typography.bodyLarge)
                    }

                    ui.collection?.let { col ->
                        DetailRow(label = "Task list", value = col.displayName)
                    }

                    if (tags.isNotEmpty()) {
                        DetailRow(label = "Tags", value = tags.joinToString(", "))
                    }

                    val priority = task.priority ?: 0
                    if (!task.isCategory && priority > 0) {
                        DetailRow(label = "Priority", value = priority.toString())
                    }

                    task.dueMillis?.let {
                        DetailRow(label = "Due", value = fmt.format(Date(it)))
                    }

                    ui.linkedEventSummary?.let { summary ->
                        DetailRow(label = "Calendar", value = summary)
                    }

                    task.dtStartMillis?.let {
                        DetailRow(label = "Started", value = fmt.format(Date(it)))
                    }

                    task.completedMillis?.let {
                        DetailRow(label = "Ended", value = fmt.format(Date(it)))
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
                                Text(if (started) "Restart" else "Start task")
                            }
                            Button(
                                onClick = viewModel::endTask,
                                enabled = !completed,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("End task")
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Edit")
                    }

                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text(if (task.isCategory) "Delete category" else "Delete task")
                    }
                }
            }
        }
    }

    if (showDeleteConfirm && task != null) {
        val label = if (task.isCategory) "category" else "task"
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete $label?") },
            text = {
                Text(
                    if (task.isCategory) {
                        "This permanently removes the category and its nested tasks from the app and the server."
                    } else {
                        "This permanently removes the task from the app and the server."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteTask()
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
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
