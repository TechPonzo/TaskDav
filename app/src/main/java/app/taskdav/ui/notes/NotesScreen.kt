package app.taskdav.ui.notes

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.taskdav.ui.common.CompactHeader
import app.taskdav.ui.common.DockScrollPadding
import app.taskdav.ui.common.ExpressiveEmptyState
import app.taskdav.ui.common.ExpressiveExtendedFab
import app.taskdav.ui.common.ExpressiveFilterChip
import app.taskdav.ui.common.ItemShare
import app.taskdav.ui.common.SyncLoadingBanner
import app.taskdav.ui.common.TagFilterIconButton
import app.taskdav.ui.common.TagsEditor
import app.taskdav.ui.common.joinCategories
import app.taskdav.ui.common.parseCategories
import app.taskdav.ui.theme.TaskDavRadii

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NotesScreen(
    viewModel: NotesViewModel,
    onEditNote: (Long?) -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val journalCollections = remember(collections) {
        collections.filter { it.enabled && it.supportsVjournal }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            Box(modifier = Modifier.navigationBarsPadding().padding(bottom = 72.dp)) {
                ExpressiveExtendedFab(
                    text = "New note",
                    icon = Icons.Default.Add,
                    onClick = { onEditNote(null) },
                )
            }
        },
    ) { _ ->
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompactHeader(
                    title = "Notes",
                    modifier = Modifier.weight(1f),
                )
                TagFilterIconButton(
                    availableTags = ui.availableTags,
                    selectedTag = ui.tagFilter,
                    onSelectTag = viewModel::setTagFilter,
                )
                IconButton(onClick = viewModel::syncNow, enabled = !ui.syncing) {
                    Icon(Icons.Default.Refresh, contentDescription = "Sync")
                }
            }
            if (ui.syncing) {
                SyncLoadingBanner(
                    message = ui.syncMessage?.takeIf { it.isNotBlank() }
                        ?: "Syncing notes…",
                )
            }
            if (journalCollections.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ExpressiveFilterChip(
                        label = "All",
                        selected = ui.collectionFilter == null,
                        onClick = { viewModel.setCollectionFilter(null) },
                    )
                    journalCollections.forEach { col ->
                        ExpressiveFilterChip(
                            label = col.displayName,
                            selected = ui.collectionFilter == col.id,
                            onClick = {
                                viewModel.setCollectionFilter(
                                    if (ui.collectionFilter == col.id) null else col.id,
                                )
                            },
                        )
                    }
                }
            }

            AnimatedContent(
                targetState = ui.notes.isEmpty(),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "notes_empty",
                modifier = Modifier.fillMaxSize(),
            ) { empty ->
                if (empty) {
                    ExpressiveEmptyState(
                        title = "No notes yet",
                        body = "Sync or add a note to get started.",
                        actionLabel = "New note",
                        onAction = { onEditNote(null) },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LazyColumn(
                        contentPadding = DockScrollPadding,
                    ) {
                        items(ui.notes, key = { it.id }) { note ->
                            Row(
                                modifier = Modifier
                                    .animateItem()
                                    .fillMaxWidth()
                                    .clickable { onEditNote(note.id) }
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(note.summary, style = MaterialTheme.typography.bodyLarge)
                                    val tags = parseCategories(note.categories)
                                    if (tags.isNotEmpty()) {
                                        Text(
                                            tags.joinToString(", "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    } else if (!note.description.isNullOrBlank()) {
                                        Text(
                                            note.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    viewModel: NoteEditorViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val knownTags by viewModel.knownTags.collectAsStateWithLifecycle()
    var tagDraft by remember { mutableStateOf("") }
    val tags = parseCategories(state.categories)
    val context = LocalContext.current

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
                    if (state.ready && state.summary.isNotBlank()) {
                        IconButton(
                            onClick = {
                                ItemShare.shareNoteDraft(
                                    context = context,
                                    uid = state.uid,
                                    summary = state.summary,
                                    description = state.description,
                                    categories = state.categories,
                                    dtStartMillis = null,
                                )
                            },
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                    }
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

            TagsEditor(
                selected = tags,
                knownTags = knownTags,
                draft = tagDraft,
                onDraftChange = { tagDraft = it },
                onAdd = { tag ->
                    viewModel.updateCategories(joinCategories(tags + tag))
                },
                onRemove = { tag ->
                    viewModel.updateCategories(joinCategories(tags - tag))
                },
            )

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
