package app.taskdav.ui.notes

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.taskdav.ui.common.joinCategories
import app.taskdav.ui.common.parseCategories

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NotesScreen(
    viewModel: NotesViewModel,
    onEditNote: (Long?) -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Notes")
                        val subtitle = ui.error ?: ui.syncMessage
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::syncNow, enabled = !ui.syncing) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEditNote(null) }) {
                Icon(Icons.Default.Add, contentDescription = "Add note")
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = notes.isEmpty(),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "notes_empty",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) { empty ->
            if (empty) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No notes yet. Sync or add one.")
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 88.dp),
                ) {
                    items(notes, key = { it.id }) { note ->
                        Row(
                            modifier = Modifier
                                .animateItem()
                                .fillMaxWidth()
                                .clickable { onEditNote(note.id) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(note.summary, style = MaterialTheme.typography.bodyLarge)
                                val tags = parseCategories(note.categories)
                                if (tags.isNotEmpty()) {
                                    Text(
                                        tags.joinToString(", "),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                } else if (!note.description.isNullOrBlank()) {
                                    Text(
                                        note.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.deleteNote(note.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteEditorScreen(
    viewModel: NoteEditorViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    var tagDraft by remember { mutableStateOf("") }
    val tags = parseCategories(state.categories)

    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.id == null) "New note" else "Edit note") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save(tagDraft) },
                        enabled = state.ready && !state.saving,
                    ) {
                        Text("Save")
                    }
                },
            )
        },
    ) { padding ->
        if (!state.ready) {
            Text("Loading…", modifier = Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.summary,
                onValueChange = viewModel::updateSummary,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Title") },
                singleLine = true,
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::updateDescription,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Details") },
                minLines = 6,
            )

            var expanded by remember { mutableStateOf(false) }
            val journalCols = collections.filter { it.supportsVjournal }
            val selected = journalCols.find { it.id == state.collectionId }
                ?: collections.find { it.id == state.collectionId }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = selected?.displayName ?: "Collection",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Note collection") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    (journalCols.ifEmpty { collections }).forEach { col ->
                        DropdownMenuItem(
                            text = { Text(col.displayName) },
                            onClick = {
                                viewModel.updateCollection(col.id)
                                expanded = false
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
                        onClick = { viewModel.updateCategories(joinCategories(tags - tag)) },
                        label = { Text(tag) },
                        trailingIcon = {
                            Icon(Icons.Default.Close, contentDescription = "Remove")
                        },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
