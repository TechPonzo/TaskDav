package app.taskdav.caldav

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.taskdav.data.CollectionEntity
import app.taskdav.data.TaskDavDatabase
import java.io.File

class CollectionExporter(
    private val db: TaskDavDatabase,
) {
    suspend fun exportToCache(context: Context, collections: List<CollectionEntity>): List<File> {
        val dir = File(context.cacheDir, "exports").apply {
            deleteRecursively()
            mkdirs()
        }
        return collections.map { col ->
            val safeName = col.displayName.replace(Regex("[^a-zA-Z0-9._-]+"), "_").ifBlank { "calendar" }
            val file = File(dir, "$safeName.ics")
            file.writeText(buildCollectionIcs(col), Charsets.UTF_8)
            file
        }
    }

    private suspend fun buildCollectionIcs(col: CollectionEntity): String {
        val parts = mutableListOf<String>()
        parts += "BEGIN:VCALENDAR"
        parts += "VERSION:2.0"
        parts += "PRODID:-//TaskDav//EN"
        parts += "X-WR-CALNAME:${escapeIcalText(col.displayName)}"

        for (task in db.tasks().getByCollection(col.id)) {
            val ics = if (!task.icsRaw.isNullOrBlank()) {
                extractComponent(task.icsRaw, "VTODO")
            } else {
                extractComponent(
                    IcalMapper.buildTodoIcs(
                        uid = task.uid,
                        summary = task.summary,
                        description = task.description,
                        status = task.status,
                        percentComplete = task.percentComplete,
                        priority = task.priority,
                        dtStartMillis = task.dtStartMillis,
                        dueMillis = task.dueMillis,
                        completedMillis = task.completedMillis,
                        categories = task.categories,
                        parentUid = task.parentUid,
                        linkedEventUid = task.linkedEventUid,
                        existingRaw = null,
                        isCategory = task.isCategory,
                    ),
                    "VTODO",
                )
            }
            if (ics != null) parts += ics
        }
        for (event in db.events().getByCollection(col.id)) {
            val ics = if (!event.icsRaw.isNullOrBlank()) {
                extractComponent(event.icsRaw, "VEVENT")
            } else {
                val start = event.dtStartMillis ?: System.currentTimeMillis()
                val end = event.dtEndMillis ?: (start + 3_600_000)
                extractComponent(
                    IcalMapper.buildEventIcs(
                        uid = event.uid,
                        summary = event.summary,
                        description = event.description,
                        dtStartMillis = start,
                        dtEndMillis = end,
                        allDay = event.allDay,
                    ),
                    "VEVENT",
                )
            }
            if (ics != null) parts += ics
        }
        for (note in db.notes().getByCollection(col.id)) {
            val ics = if (!note.icsRaw.isNullOrBlank()) {
                extractComponent(note.icsRaw, "VJOURNAL")
            } else {
                extractComponent(
                    IcalMapper.buildNoteIcs(
                        uid = note.uid,
                        summary = note.summary,
                        description = note.description,
                        dtStartMillis = note.dtStartMillis,
                        categories = note.categories,
                    ),
                    "VJOURNAL",
                )
            }
            if (ics != null) parts += ics
        }

        parts += "END:VCALENDAR"
        return parts.joinToString("\r\n") + "\r\n"
    }

    fun shareFiles(context: Context, files: List<File>) {
        if (files.isEmpty()) return
        val uris = ArrayList(
            files.map { file ->
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            },
        )
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/calendar"
                putExtra(Intent.EXTRA_STREAM, uris[0])
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "text/calendar"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        context.startActivity(Intent.createChooser(intent, "Export calendars"))
    }

    private fun extractComponent(ics: String, name: String): String? {
        val begin = "BEGIN:$name"
        val end = "END:$name"
        val startIdx = ics.indexOf(begin, ignoreCase = true)
        val endIdx = ics.indexOf(end, ignoreCase = true)
        if (startIdx < 0 || endIdx < 0) return null
        return ics.substring(startIdx, endIdx + end.length).trim()
    }

    private fun escapeIcalText(value: String): String =
        value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")
}
