package app.taskdav.ui.tasks

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import app.taskdav.ui.common.CompactHeader
import app.taskdav.ui.common.ConfirmDeleteDialog
import app.taskdav.ui.common.DockScrollPadding
import app.taskdav.ui.common.ExpressiveEmptyState
import app.taskdav.ui.common.ExpressiveExtendedFab
import app.taskdav.ui.common.ExpressiveFilterChip
import app.taskdav.ui.common.SwipeRevealAction
import app.taskdav.ui.common.SwipeRevealRow
import app.taskdav.ui.common.SyncLoadingBanner
import app.taskdav.ui.common.TagFilterIconButton
import app.taskdav.ui.theme.TaskDavRadii
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
import androidx.compose.ui.res.stringResource
import app.taskdav.R
import app.taskdav.ui.common.DateFormats
import app.taskdav.ui.common.LocalDateOrder
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
    val collapsedCategoryUids by viewModel.collapsedCategoryUids.collectAsStateWithLifecycle()
    val eventsByUid = remember(events) { events.associateBy { it.uid } }
    val syncedFlat = remember(forest) { TaskTreeBuilder.flatten(forest) }
    val visibleFlat = remember(syncedFlat, collapsedCategoryUids) {
        filterCollapsedCategories(syncedFlat, collapsedCategoryUids)
    }

    var displayList by remember { mutableStateOf(visibleFlat) }
    var dragging by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<TaskNode?>(null) }

    // Key only on visibleFlat — keying on dragging reapplied stale Room data on drop.
    // Skip applying when the visible content is unchanged so Room's post-persist
    // re-emit (new entity instances) doesn't make row text look like it reloads.
    LaunchedEffect(visibleFlat) {
        if (dragging) return@LaunchedEffect
        if (!taskListVisuallyEqual(displayList, visibleFlat)) {
            displayList = visibleFlat
        }
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        displayList = relocateTaskSubtree(displayList, from.index, to.index)
    }

    pendingDelete?.let { node ->
        ConfirmDeleteDialog(
            title = stringResource(
                if (node.task.isCategory) {
                    R.string.tasks_delete_title_category
                } else {
                    R.string.tasks_delete_title_task
                },
            ),
            body = if (node.task.isCategory) {
                stringResource(R.string.tasks_delete_body_category)
            } else {
                stringResource(R.string.tasks_delete_body_task, node.task.summary)
            },
            onConfirm = {
                viewModel.deleteTask(node.task.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            Box(modifier = Modifier.navigationBarsPadding().padding(bottom = 72.dp)) {
                ExpressiveExtendedFab(
                    text = stringResource(R.string.tasks_new),
                    icon = Icons.Default.Add,
                    onClick = { onEditTask(null, false, ui.collectionFilter) },
                )
            }
        },
    ) { _ ->
        PullToRefreshBox(
            isRefreshing = ui.syncing,
            onRefresh = viewModel::syncNow,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompactHeader(
                        title = stringResource(R.string.tasks_title),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { viewModel.setShowCompleted(!ui.showCompleted) }) {
                        if (ui.showCompleted) {
                            Icon(
                                Icons.Default.VisibilityOff,
                                contentDescription = stringResource(R.string.tasks_cd_hide_completed),
                            )
                        } else {
                            Icon(
                                Icons.Default.Visibility,
                                contentDescription = stringResource(R.string.tasks_cd_show_completed),
                            )
                        }
                    }
                    TagFilterIconButton(
                        availableTags = ui.availableTags,
                        selectedTag = ui.tagFilter,
                        onSelectTag = viewModel::setTagFilter,
                    )
                    IconButton(
                        onClick = viewModel::syncNow,
                        enabled = !ui.syncing,
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_sync))
                    }
                }
                if (ui.syncing) {
                    SyncLoadingBanner(
                        message = ui.syncMessage?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.tasks_syncing),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ExpressiveFilterChip(
                        label = stringResource(R.string.action_all),
                        selected = ui.collectionFilter == null,
                        onClick = { viewModel.setCollectionFilter(null) },
                    )
                    collections.filter { it.enabled && it.supportsVtodo }.forEach { col ->
                        ExpressiveFilterChip(
                            label = col.displayName,
                            selected = ui.collectionFilter == col.id,
                            onClick = { viewModel.setCollectionFilter(col.id) },
                        )
                    }
                }

                if (displayList.isEmpty()) {
                    ExpressiveEmptyState(
                        title = stringResource(R.string.tasks_empty_title),
                        body = stringResource(R.string.tasks_empty_body),
                        actionLabel = stringResource(R.string.tasks_new),
                        onAction = { onEditTask(null, false, ui.collectionFilter) },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LazyColumn(
                        state = lazyListState,
                        contentPadding = DockScrollPadding,
                    ) {
                        items(displayList, key = { it.task.id }) { node ->
                            ReorderableItem(reorderableState, key = node.task.id) {
                                TaskRow(
                                    node = node,
                                    linkedEvent = node.task.linkedEventUid?.let { eventsByUid[it] },
                                    collapsed = node.task.uid in collapsedCategoryUids,
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
                                    onDelete = { pendingDelete = node },
                                    onToggleCollapse = {
                                        viewModel.toggleCategoryCollapsed(node.task.uid)
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
 * Hide descendants of collapsed categories in a depth-ordered flat list.
 */
internal fun filterCollapsedCategories(
    flat: List<TaskNode>,
    collapsedUids: Set<String>,
): List<TaskNode> {
    if (collapsedUids.isEmpty()) return flat
    val out = ArrayList<TaskNode>(flat.size)
    var skipDeeperThan: Int? = null
    for (node in flat) {
        val skipUntil = skipDeeperThan
        if (skipUntil != null) {
            if (node.depth > skipUntil) continue
            skipDeeperThan = null
        }
        out += node
        if (node.task.isCategory &&
            node.task.uid in collapsedUids &&
            node.children.isNotEmpty()
        ) {
            skipDeeperThan = node.depth
        }
    }
    return out
}

/** Compare what the task rows actually show — ignore Room identity / updatedAt. */
internal fun taskListVisuallyEqual(a: List<TaskNode>, b: List<TaskNode>): Boolean {
    if (a.size != b.size) return false
    for (i in a.indices) {
        val left = a[i]
        val right = b[i]
        val t = left.task
        val u = right.task
        if (t.id != u.id ||
            left.depth != right.depth ||
            t.parentUid != u.parentUid ||
            t.summary != u.summary ||
            t.status != u.status ||
            t.percentComplete != u.percentComplete ||
            t.dueMillis != u.dueMillis ||
            t.completedMillis != u.completedMillis ||
            t.categories != u.categories ||
            t.linkedEventUid != u.linkedEventUid ||
            t.isCategory != u.isCategory ||
            left.collection?.id != right.collection?.id ||
            left.children.size != right.children.size
        ) {
            return false
        }
    }
    return true
}

/**
 * Move a task (and its contiguous descendants) in the flat list.
 *
 * Index semantics match sh.calvin.reorderable's expected single-item mutation
 * `add(toIndex, removeAt(fromIndex))`, generalized to a contiguous subtree.
 *
 * - Dropping onto a category nests into it as the first child.
 * - Sliding into an expanded parent's child rows keeps/joins that parent.
 * - Sliding next to a shallower row moves you out (outdent / new parent).
 * - Categories always stay at the root.
 */
internal fun relocateTaskSubtree(
    list: List<TaskNode>,
    fromIndex: Int,
    toIndex: Int,
): List<TaskNode> {
    if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) {
        return list
    }
    val fromNode = list[fromIndex]

    fun subtreeEnd(start: Int, source: List<TaskNode> = list): Int {
        val depth = source[start].depth
        var end = start + 1
        while (end < source.size && source[end].depth > depth) end++
        return end
    }

    val fromEnd = subtreeEnd(fromIndex)
    // Refuse dropping into own descendants.
    if (toIndex in (fromIndex + 1) until fromEnd) return list

    val movingUids = list.subList(fromIndex, fromEnd).mapTo(HashSet()) { it.task.uid }
    val rawTarget = list[toIndex]

    // Drop onto a category → nest. (Do not auto-nest into tasks that merely have
    // children — that made downward sibling reorders fall into the target's kids.)
    val nestIntoTarget = !fromNode.task.isCategory &&
        rawTarget.task.isCategory &&
        rawTarget.task.uid !in movingUids

    val block = list.subList(fromIndex, fromEnd).toList()
    val mutable = list.toMutableList()
    mutable.subList(fromIndex, fromEnd).clear()

    if (nestIntoTarget) {
        val targetIdx = mutable.indexOfFirst { it.task.uid == rawTarget.task.uid }
        if (targetIdx < 0) return list
        val newParent = rawTarget.task.uid
        val newDepth = rawTarget.depth + 1
        val depthDelta = newDepth - fromNode.depth
        mutable.addAll(targetIdx + 1, reparentBlock(block, newParent, depthDelta))
        return mutable
    }

    // Same formula as the original single-item-style insert. The one-step-down
    // case (toIndex == fromEnd) would no-op for a multi-row block; bump past the
    // hovered target (and its kids) so the list still moves one sibling slot.
    var insertAt = (if (toIndex > fromIndex) toIndex - block.size else toIndex)
        .coerceIn(0, mutable.size)
    if (toIndex > fromIndex && insertAt == fromIndex && insertAt < mutable.size) {
        val targetDepth = mutable[insertAt].depth
        var end = insertAt + 1
        while (end < mutable.size && mutable[end].depth > targetDepth) end++
        insertAt = end
    }

    val prev = mutable.getOrNull(insertAt - 1)
    val next = mutable.getOrNull(insertAt)

    val (newParent, newDepth) = inferParentAndDepth(
        movingCategory = fromNode.task.isCategory,
        prev = prev,
        next = next,
    )
    if (newParent != null && newParent in movingUids) return list

    val depthDelta = newDepth - fromNode.depth
    mutable.addAll(insertAt, reparentBlock(block, newParent, depthDelta))
    return mutable
}

private fun reparentBlock(
    block: List<TaskNode>,
    newParentUid: String?,
    depthDelta: Int,
): List<TaskNode> =
    block.mapIndexed { index, node ->
        val task = if (index == 0) {
            node.task.copy(parentUid = newParentUid)
        } else {
            node.task
        }
        node.copy(
            task = task,
            depth = (node.depth + depthDelta).coerceAtLeast(0),
        )
    }

private fun inferParentAndDepth(
    movingCategory: Boolean,
    prev: TaskNode?,
    next: TaskNode?,
): Pair<String?, Int> {
    if (movingCategory) return null to 0
    if (prev == null) return null to 0

    // Inserting into the child region under prev (next is deeper than prev).
    if (next != null && next.depth > prev.depth) {
        return next.task.parentUid to next.depth
    }

    // Nest under a task/category that has no visible children below this slot
    // when we land immediately after it (into empty / collapsed parent).
    if (next == null || next.depth <= prev.depth) {
        // Becoming a sibling of prev — unless we just stepped onto nesting under
        // an empty parent via category drop (handled separately). Stay sibling.
        return prev.task.parentUid to prev.depth
    }

    return prev.task.parentUid to prev.depth
}

/**
 * Move a task (and its contiguous descendants) among siblings of the same parent.
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
    collapsed: Boolean,
    dragHandleModifier: Modifier,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onAddChild: () -> Unit,
    onDelete: () -> Unit,
    onToggleCollapse: () -> Unit,
) {
    val completed = TaskTreeBuilder.isCompleted(node.task)
    val isCategory = node.task.isCategory
    val color = collectionColorOrDefault(node.collection?.colorArgb)
    val context = LocalContext.current
    val dateOrder = LocalDateOrder.current
    SwipeRevealRow(
        contentColor = MaterialTheme.colorScheme.background,
        cornerRadius = 0.dp,
        actions = { close ->
            SwipeRevealAction(
                icon = Icons.Default.Delete,
                contentDescription = stringResource(R.string.tasks_cd_delete),
                onClick = {
                    onDelete()
                    close()
                },
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        },
    ) { _ ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(start = (16 + node.depth * 14).dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
            if (isCategory) {
                IconButton(
                    onClick = onToggleCollapse,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        if (collapsed) Icons.Default.Folder else Icons.Default.FolderOpen,
                        contentDescription = if (collapsed) {
                            stringResource(R.string.tasks_cd_expand_category)
                        } else {
                            stringResource(R.string.tasks_cd_collapse_category)
                        },
                        modifier = Modifier.size(18.dp),
                        tint = color,
                    )
                }
            } else {
                Checkbox(checked = completed, onCheckedChange = { onToggle() })
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    node.task.summary,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (!isCategory && completed) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val started = !isCategory && TaskTreeBuilder.isStarted(node.task)
                val endLabel = node.task.completedMillis?.let { millis ->
                    DateFormats.time(context, millis)
                }
                val meta = buildList {
                    if (isCategory) add(stringResource(R.string.tasks_meta_category))
                    when {
                        completed -> add(
                            if (endLabel != null) {
                                stringResource(R.string.tasks_meta_done_at, endLabel)
                            } else {
                                stringResource(R.string.tasks_meta_done)
                            },
                        )
                        started -> add(stringResource(R.string.tasks_meta_started))
                    }
                    node.collection?.displayName?.let { add(it) }
                    val tags = parseCategories(node.task.categories)
                    if (tags.isNotEmpty()) add(tags.joinToString(", "))
                    node.task.dueMillis?.let { due ->
                        add(stringResource(R.string.tasks_meta_due, DateFormats.dateTime(context, due, dateOrder)))
                    }
                    val eventStart = linkedEvent?.dtStartMillis
                    when {
                        eventStart != null -> {
                            val end = linkedEvent?.dtEndMillis
                            add(
                                buildString {
                                    append(DateFormats.dateTime(context, eventStart, dateOrder))
                                    if (end != null) {
                                        append(" – ")
                                        append(DateFormats.time(context, end))
                                    }
                                },
                            )
                        }
                        !node.task.linkedEventUid.isNullOrBlank() -> add(stringResource(R.string.tasks_meta_linked_event))
                    }
                    if (node.children.isNotEmpty()) {
                        val count = node.children.size
                        add(
                            if (collapsed) {
                                stringResource(R.string.tasks_meta_count_hidden, count)
                            } else if (isCategory) {
                                stringResource(R.string.tasks_meta_count_tasks, count)
                            } else {
                                stringResource(R.string.tasks_meta_count_sub, count)
                            },
                        )
                    }
                }.joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(meta, style = MaterialTheme.typography.labelSmall)
                }
            }
            if (!node.task.linkedEventUid.isNullOrBlank()) {
                Icon(
                    Icons.Default.Event,
                    contentDescription = stringResource(R.string.tasks_cd_linked_calendar),
                    modifier = Modifier.size(18.dp),
                    tint = color,
                )
            }
            IconButton(onClick = onAddChild) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = if (isCategory) {
                        stringResource(R.string.tasks_cd_add_in_category)
                    } else {
                        stringResource(R.string.tasks_cd_add_subtask)
                    },
                )
            }
            IconButton(
                onClick = {},
                modifier = dragHandleModifier,
            ) {
                Icon(Icons.Default.DragHandle, contentDescription = stringResource(R.string.tasks_cd_drag))
            }
        }
    }
}
