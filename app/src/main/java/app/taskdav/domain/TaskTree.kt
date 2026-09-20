package app.taskdav.domain

import app.taskdav.data.CollectionEntity
import app.taskdav.data.EventEntity
import app.taskdav.data.TaskEntity

data class TaskNode(
    val task: TaskEntity,
    val collection: CollectionEntity?,
    val children: List<TaskNode>,
    val depth: Int,
)

object TaskTreeBuilder {
    fun buildForest(
        tasks: List<TaskEntity>,
        collections: Map<Long, CollectionEntity>,
        collectionFilter: Long? = null,
        showCompleted: Boolean = true,
        tagFilter: String? = null,
    ): List<TaskNode> {
        val filtered = tasks.filter { task ->
            (collectionFilter == null || task.collectionId == collectionFilter) &&
                (showCompleted || task.isCategory || !isCompleted(task)) &&
                (tagFilter == null || tagMatches(task.categories, tagFilter))
        }
        val byUid = filtered.associateBy { it.uid }
        val childrenMap = mutableMapOf<String?, MutableList<TaskEntity>>()
        for (task in filtered) {
            val parent = task.parentUid?.takeIf { it in byUid }
            childrenMap.getOrPut(parent) { mutableListOf() }.add(task)
        }
        fun build(parentUid: String?, depth: Int): List<TaskNode> {
            val kids = childrenMap[parentUid].orEmpty()
                .sortedWith(
                    compareBy(
                        { isCompleted(it) },
                        { it.sortOrder },
                        { it.summary.lowercase() },
                    ),
                )
            return kids.map { task ->
                TaskNode(
                    task = task,
                    collection = collections[task.collectionId],
                    children = build(task.uid, depth + 1),
                    depth = depth,
                )
            }
        }
        return build(null, 0)
    }

    fun flatten(nodes: List<TaskNode>): List<TaskNode> {
        val out = mutableListOf<TaskNode>()
        fun walk(list: List<TaskNode>) {
            for (n in list) {
                out += n
                walk(n.children)
            }
        }
        walk(nodes)
        return out
    }

    fun isCompleted(task: TaskEntity): Boolean {
        if (task.status.equals("COMPLETED", ignoreCase = true)) return true
        if ((task.percentComplete ?: 0) >= 100) return true
        return task.completedMillis != null
    }

    fun isStarted(task: TaskEntity): Boolean {
        if (isCompleted(task)) return false
        if (task.status.equals("IN-PROCESS", ignoreCase = true)) return true
        return task.dtStartMillis != null
    }

    private fun tagMatches(categories: String?, tag: String): Boolean {
        if (categories.isNullOrBlank()) return false
        return categories.split(',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .any { it == tag }
    }
}

data class TaskEditorState(
    val id: Long? = null,
    val uid: String,
    val collectionId: Long,
    val summary: String = "",
    val description: String = "",
    val status: String? = "NEEDS-ACTION",
    val percentComplete: Int? = 0,
    val priority: Int? = 0,
    val dueMillis: Long? = null,
    val categories: String? = null,
    val parentUid: String? = null,
    val linkedEventUid: String? = null,
    val linkedEvent: EventEntity? = null,
    val isCategory: Boolean = false,
)
