package app.taskdav.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.taskdav.data.NoteEntity
import app.taskdav.data.TaskEntity
import app.taskdav.ui.common.parseCategories
import app.taskdav.ui.theme.collectionColorOrDefault

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenTask: (Long) -> Unit,
    onOpenNote: (Long) -> Unit,
    onSeeAllTasks: () -> Unit,
    onSeeAllNotes: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Home") })
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Text(
                    "Calendars",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = ui.filters.collectionId == null,
                        onClick = { viewModel.setCollectionFilter(null) },
                        label = { Text("All") },
                    )
                    ui.collections.forEach { col ->
                        FilterChip(
                            selected = ui.filters.collectionId == col.id,
                            onClick = {
                                viewModel.setCollectionFilter(
                                    if (ui.filters.collectionId == col.id) null else col.id,
                                )
                            },
                            label = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(
                                                collectionColorOrDefault(col.colorArgb),
                                                CircleShape,
                                            ),
                                    )
                                    Text(col.displayName)
                                }
                            },
                        )
                    }
                }
            }

            if (ui.availableTags.isNotEmpty()) {
                item {
                    Text(
                        "Tags",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = ui.filters.tag == null,
                            onClick = { viewModel.setTagFilter(null) },
                            label = { Text("All tags") },
                        )
                        ui.availableTags.forEach { tag ->
                            FilterChip(
                                selected = ui.filters.tag == tag,
                                onClick = {
                                    viewModel.setTagFilter(
                                        if (ui.filters.tag == tag) null else tag,
                                    )
                                },
                                label = { Text(tag) },
                            )
                        }
                    }
                }
            }

            item {
                SectionHeader(
                    title = "Recent tasks",
                    actionLabel = "See all",
                    onAction = onSeeAllTasks,
                )
            }
            if (ui.recentTasks.isEmpty()) {
                item {
                    Text(
                        "No recent tasks",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            } else {
                items(ui.recentTasks, key = { "t-${it.id}" }) { task ->
                    RecentTaskRow(task = task, onClick = { onOpenTask(task.id) })
                }
            }

            item {
                SectionHeader(
                    title = "Recent notes",
                    actionLabel = "See all",
                    onAction = onSeeAllNotes,
                )
            }
            if (ui.recentNotes.isEmpty()) {
                item {
                    Text(
                        "No recent notes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            } else {
                items(ui.recentNotes, key = { "n-${it.id}" }) { note ->
                    RecentNoteRow(note = note, onClick = { onOpenNote(note.id) })
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun RecentTaskRow(task: TaskEntity, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(task.summary, style = MaterialTheme.typography.bodyLarge)
        val tags = parseCategories(task.categories)
        if (tags.isNotEmpty()) {
            Text(tags.joinToString(", "), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun RecentNoteRow(note: NoteEntity, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(note.summary, style = MaterialTheme.typography.bodyLarge)
        val tags = parseCategories(note.categories)
        if (tags.isNotEmpty()) {
            Text(tags.joinToString(", "), style = MaterialTheme.typography.labelSmall)
        } else if (!note.description.isNullOrBlank()) {
            Text(
                note.description,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }
    }
}
