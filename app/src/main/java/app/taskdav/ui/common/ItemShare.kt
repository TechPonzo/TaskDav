package app.taskdav.ui.common

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.taskdav.caldav.IcalMapper
import app.taskdav.data.EventEntity
import app.taskdav.data.NoteEntity
import app.taskdav.data.TaskEntity
import java.io.File

object ItemShare {
    fun shareTask(context: Context, task: TaskEntity) {
        val text = buildString {
            append(task.summary)
            task.dueMillis?.let {
                append("\nDue: ")
                append(DateFormats.dateTime(context, it))
            }
            task.description?.takeIf { it.isNotBlank() }?.let {
                append("\n\n")
                append(it)
            }
        }
        val ics = IcalMapper.buildTodoIcs(
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
            existingRaw = task.icsRaw,
            isCategory = task.isCategory,
            sortOrder = task.sortOrder,
        )
        share(context, text, ics, fileName = sanitizeFileName(task.summary) + ".ics", chooserTitle = "Share task")
    }

    fun shareEvent(context: Context, event: EventEntity) {
        val text = buildString {
            append(event.summary)
            event.dtStartMillis?.let {
                append("\n")
                append(DateFormats.dateTime(context, it))
                event.dtEndMillis?.let { end ->
                    append(" – ")
                    append(DateFormats.dateTime(context, end))
                }
            }
            event.location?.takeIf { it.isNotBlank() }?.let {
                append("\n")
                append(it)
            }
            event.description?.takeIf { it.isNotBlank() }?.let {
                append("\n\n")
                append(it)
            }
        }
        val start = event.dtStartMillis ?: System.currentTimeMillis()
        val end = event.dtEndMillis ?: (start + 60 * 60 * 1000L)
        val ics = IcalMapper.buildEventIcs(
            uid = event.uid,
            summary = event.summary,
            description = event.description,
            location = event.location,
            dtStartMillis = start,
            dtEndMillis = end,
            allDay = event.allDay,
            rrule = event.rrule,
        )
        share(context, text, ics, fileName = sanitizeFileName(event.summary) + ".ics", chooserTitle = "Share event")
    }

    fun shareNote(context: Context, note: NoteEntity) {
        val text = buildString {
            append(note.summary)
            note.description?.takeIf { it.isNotBlank() }?.let {
                append("\n\n")
                append(it)
            }
        }
        val ics = IcalMapper.buildNoteIcs(
            uid = note.uid,
            summary = note.summary,
            description = note.description,
            dtStartMillis = note.dtStartMillis,
            categories = note.categories,
        )
        share(context, text, ics, fileName = sanitizeFileName(note.summary) + ".ics", chooserTitle = "Share note")
    }

    /** Share from note editor before the entity is fully persisted. */
    fun shareNoteDraft(
        context: Context,
        uid: String,
        summary: String,
        description: String?,
        categories: String?,
        dtStartMillis: Long?,
    ) {
        val text = buildString {
            append(summary.ifBlank { "Note" })
            description?.takeIf { it.isNotBlank() }?.let {
                append("\n\n")
                append(it)
            }
        }
        val ics = IcalMapper.buildNoteIcs(
            uid = uid,
            summary = summary.ifBlank { "Untitled" },
            description = description,
            dtStartMillis = dtStartMillis,
            categories = categories,
        )
        share(context, text, ics, fileName = sanitizeFileName(summary) + ".ics", chooserTitle = "Share note")
    }

    private fun share(
        context: Context,
        text: String,
        ics: String,
        fileName: String,
        chooserTitle: String,
    ) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(ics)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/calendar"
            putExtra(Intent.EXTRA_SUBJECT, text.lineSequence().firstOrNull().orEmpty())
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newUri(context.contentResolver, fileName, uri)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    private fun sanitizeFileName(raw: String): String {
        val base = raw.trim().ifBlank { "item" }
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .take(40)
        return base.ifBlank { "item" }
    }
}
