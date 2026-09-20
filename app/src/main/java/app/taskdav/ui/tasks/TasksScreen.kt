package app.taskdav.ui.tasks

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.taskdav.data.EventEntity
import app.taskdav.domain.TaskNode
import app.taskdav.domain.TaskTreeBuilder
import app.taskdav.ui.common.parseCategories
import app.taskdav.ui.theme.collectionColorOrDefault
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    viewModel: TasksViewModel,
    onEditTask: (taskId: Long?, isCategory: Boolean, collectionId: Long?) -> Unit,
    onAddSubtask: (parentUid: String, collectionId: Long) -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val forest by viewModel.taskForest.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val eventsByUid = remember(events) { events.associateBy { it.uid } }
    val syncedFlat = remember(forest) { TaskTreeBuilder.flatten(forest) }

    var displayList by remember { mutableStateOf(syncedFlat) }
    var dragging by remember { mutableStateOf(false) }

    LaunchedEffect(syncedFlat, dragging) {
        if (!dragging) {
            displayList = syncedFlat
        }
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        displayList = moveSiblingSubtree(displayList, from.index, to.index)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Tasks")
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
                    IconButton(onClick = { viewModel.setShowCompleted(!ui.showCompleted) }) {
                        if (ui.showCompleted) {
                            Icon(
                                Icons.Default.VisibilityOff,
                                contentDescription = "Hide completed",
                            )
                        } else {
                            Icon(
                                Icons.Default.Visibility,
                                contentDescription = "Show completed",
                            )
                        }
                    }
                    IconButton(
                        onClick = viewModel::syncNow,
                        enabled = !ui.syncing,
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEditTask(null, false, ui.collectionFilter) }) {
                Icon(Icons.Default.Add, contentDescription = "Add task")
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = ui.syncing,
            onRefresh = viewModel::syncNow,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = ui.collectionFilter == null,
                        onClick = { viewModel.setCollectionFilter(null) },
                        label = { Text("All") },
                    )
                    collections.filter { it.enabled && it.supportsVtodo }.forEach { col ->
                        FilterChip(
                            selected = ui.collectionFilter == col.id,
                            onClick = { viewModel.setCollectionFilter(col.id) },
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

                if (displayList.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No tasks yet. Tap + to add a task.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                } else {
                    LazyColumn(
                        state = lazyListState,
                        contentPadding = PaddingValues(bottom = 88.dp),
                    ) {
                        items(displayList, key = { it.task.id }) { node ->
                            ReorderableItem(reorderableState, key = node.task.id) {
                                TaskRow(
                                    node = node,
                                    linkedEvent = node.task.linkedEventUid?.let { eventsByUid[it] },
                                    dragHandleModifier = Modifier.draggableHandle(
                                        onDragStarted = { dragging = true },
                                        onDragStopped = {
                                            val ordered = displayList
                                            dragging = false
                                            viewModel.persistOrder(ordered)
                                        },
                                    ),
                                    onToggle = { viewModel.toggleComplete(node.task.id) },
                                    onOpen = { onEditTask(node.task.id, false, null) },
                                    onAddChild = {
                                        onAddSubtask(node.task.uid, node.task.collectionId)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Move a task (and its contiguous descendants) among siblings of the same parent.
 * Dragging onto a deeper descendant resolves to that sibling branch root.
 */
internal fun moveSiblingSubtree(
    list: List<TaskNode>,
    fromIndex: Int,
    toIndex: Int,
): List<TaskNode> {
    if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) {
        return list
    }
    val fromNode = list[fromIndex]
    var targetIndex = toIndex
    while (targetIndex in list.indices && list[targetIndex].depth > fromNode.depth) {
        targetIndex--
    }
    if (targetIndex !in list.indices) return list
    val toNode = list[targetIndex]
    if (toNode.depth != fromNode.depth) return list
    if (toNode.task.parentUid != fromNode.task.parentUid) return list
    if (targetIndex == fromIndex) return list

    fun subtreeEnd(start: Int): Int {
        val depth = list[start].depth
        var end = start + 1
        while (end < list.size && list[end].depth > depth) end++
        return end
    }

    val fromEnd = subtreeEnd(fromIndex)
    val block = list.subList(fromIndex, fromEnd).toList()
    val mutable = list.toMutableList()
    mutable.subList(fromIndex, fromEnd).clear()

    val adjustedTarget = if (targetIndex > fromIndex) {
        targetIndex - block.size
    } else {
        targetIndex
    }
    val insertAt = if (targetIndex > fromIndex) {
        val targetDepth = mutable[adjustedTarget].depth
        var end = adjustedTarget + 1
        while (end < mutable.size && mutable[end].depth > targetDepth) end++
        end
    } else {
        adjustedTarget
    }
    mutable.addAll(insertAt, block)
    return mutable
}

@Composable
private fun TaskRow(
    node: TaskNode,
    linkedEvent: EventEntity?,
    dragHandleModifier: Modifier,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onAddChild: () -> Unit,
) {
    val completed = TaskTreeBuilder.isCompleted(node.task)
    val isCategory = node.task.isCategory
    val color = collectionColorOrDefault(node.collection?.colorArgb)
    val dateTimeFmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = (12 + node.depth * 20).dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
                .padding(vertical = 14.dp),
        )
        if (isCategory) {
            Icon(
                Icons.Default.Folder,
                contentDescription = "Category",
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .size(22.dp),
                tint = color,
            )
        } else {
            Checkbox(checked = completed, onCheckedChange = { onToggle() })
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                node.task.summary,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (!isCategory && completed) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val started = !isCategory && TaskTreeBuilder.isStarted(node.task)
            val endLabel = node.task.completedMillis?.let { millis ->
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))
            }
            val meta = buildList {
                if (isCategory) add("category")
                when {
                    completed -> add(if (endLabel != null) "Done · $endLabel" else "Done")
                    started -> add("Started")
                }
                node.collection?.displayName?.let { add(it) }
                val tags = parseCategories(node.task.categories)
                if (tags.isNotEmpty()) add(tags.joinToString(", "))
                node.task.dueMillis?.let { due ->
                    add("Due ${dateTimeFmt.format(Date(due))}")
                }
                val eventStart = linkedEvent?.dtStartMillis
                when {
                    eventStart != null -> {
                        val end = linkedEvent?.dtEndMillis
                        add(
                            buildString {
                                append(dateTimeFmt.format(Date(eventStart)))
                                if (end != null) {
                                    append(" – ")
                                    append(
                                        DateFormat.getTimeInstance(DateFormat.SHORT)
                                            .format(Date(end)),
                                    )
                                }
                            },
                        )
                    }
                    !node.task.linkedEventUid.isNullOrBlank() -> add("linked event")
                }
                if (node.children.isNotEmpty()) {
                    add("${node.children.size} ${if (isCategory) "tasks" else "sub"}")
                }
            }.joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (!node.task.linkedEventUid.isNullOrBlank()) {
            Icon(
                Icons.Default.Event,
                contentDescription = "Linked calendar item",
                modifier = Modifier.size(18.dp),
                tint = color,
            )
        }
        IconButton(onClick = onAddChild) {
            Icon(
                Icons.Default.Add,
                contentDescription = if (isCategory) "Add task in category" else "Add subtask",
            )
        }
        IconButton(
            onClick = {},
            modifier = dragHandleModifier,
        ) {
            Icon(Icons.Default.DragHandle, contentDescription = "Reorder")
        }
    }
}
