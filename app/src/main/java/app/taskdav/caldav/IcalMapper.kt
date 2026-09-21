package app.taskdav.caldav

import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.data.CalendarOutputter
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.component.VJournal
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.RelType
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.Categories
import net.fortuna.ical4j.model.property.Completed
import net.fortuna.ical4j.model.property.Description
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStamp
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Due
import net.fortuna.ical4j.model.property.LastModified
import net.fortuna.ical4j.model.property.Location
import net.fortuna.ical4j.model.property.PercentComplete
import net.fortuna.ical4j.model.property.Priority
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.RelatedTo
import net.fortuna.ical4j.model.property.Status
import net.fortuna.ical4j.model.property.Summary
import net.fortuna.ical4j.model.property.Uid
import net.fortuna.ical4j.model.property.Version
import net.fortuna.ical4j.model.property.XProperty
import java.io.StringReader
import java.io.StringWriter
import java.util.TimeZone
import java.util.UUID

const val X_TASKDAV_KIND = "X-TASKDAV-KIND"
const val X_TASKDAV_SORT = "X-TASKDAV-SORT"
const val KIND_CATEGORY = "CATEGORY"

data class ParsedTodo(
    val uid: String,
    val summary: String,
    val description: String?,
    val status: String?,
    val percentComplete: Int?,
    val priority: Int?,
    val dtStartMillis: Long?,
    val dueMillis: Long?,
    val completedMillis: Long?,
    val categories: String?,
    val parentUid: String?,
    val linkedEventUid: String?,
    val isCategory: Boolean = false,
    val sortOrder: Int = 0,
    val lastModifiedMillis: Long? = null,
    val icsRaw: String,
)

data class ParsedEvent(
    val uid: String,
    val summary: String,
    val description: String?,
    val location: String?,
    val dtStartMillis: Long?,
    val dtEndMillis: Long?,
    val allDay: Boolean,
    val rrule: String?,
    val lastModifiedMillis: Long? = null,
    val icsRaw: String,
)

data class ParsedNote(
    val uid: String,
    val summary: String,
    val description: String?,
    val dtStartMillis: Long?,
    val categories: String?,
    val lastModifiedMillis: Long? = null,
    val icsRaw: String,
)

object IcalMapper {
    init {
        System.setProperty(
            "net.fortuna.ical4j.timezone.cache.impl",
            "net.fortuna.ical4j.util.MapTimeZoneCache",
        )
        System.setProperty("net.fortuna.ical4j.timezone.update.enabled", "false")
        System.setProperty("ical4j.parsing.relaxed", "true")
        System.setProperty("ical4j.unfolding.relaxed", "true")
        System.setProperty("ical4j.validation.relaxed", "true")
    }

    fun parseTodos(ics: String): List<ParsedTodo> {
        val calendar = CalendarBuilder().build(StringReader(ics))
        val todos = calendar.getComponents<VToDo>(Component.VTODO)
        return todos.map { todo ->
            var parentUid: String? = null
            var linkedEventUid: String? = null
            val relatedProps = mutableListOf<RelatedTo>()
            for (prop in todo.properties) {
                val p = prop as Property
                if (p.name.equals(Property.RELATED_TO, ignoreCase = true)) {
                    relatedProps += p as RelatedTo
                }
            }
            for (relatedTo in relatedProps) {
                val relValue = (relatedTo.getParameter(Parameter.RELTYPE) as? Parameter)?.value?.uppercase()
                val relatedUid = normalizeUid(relatedTo.value) ?: continue
                when (relValue) {
                    "PARENT" -> parentUid = relatedUid
                    "CHILD" -> Unit
                    "RELATED", "SIBLING" -> if (linkedEventUid == null) {
                        linkedEventUid = relatedUid
                    }
                    null -> {
                        // RFC default is PARENT; a second untyped RELATED-TO is the linked event
                        if (parentUid == null) parentUid = relatedUid
                        else if (linkedEventUid == null) linkedEventUid = relatedUid
                    }
                    else -> if (linkedEventUid == null) linkedEventUid = relatedUid
                }
            }
            ParsedTodo(
                uid = todo.uid?.value ?: UUID.randomUUID().toString(),
                summary = todo.summary?.value.orEmpty().ifBlank { "(untitled)" },
                description = todo.description?.value
                    ?: (todo.getProperty(Property.DESCRIPTION) as? Description)?.value,
                status = todo.status?.value,
                percentComplete = todo.percentComplete?.percentage,
                priority = todo.priority?.level,
                dtStartMillis = todo.startDate?.date?.toInstantMillis()
                    ?: (todo.getProperty(Property.DTSTART) as? DtStart)?.date?.toInstantMillis(),
                dueMillis = todo.due?.date?.toInstantMillis()
                    ?: (todo.getProperty(Property.DUE) as? Due)?.date?.toInstantMillis(),
                completedMillis = todo.dateCompleted?.date?.toInstantMillis()
                    ?: (todo.getProperty(Property.COMPLETED) as? Completed)?.date?.toInstantMillis(),
                categories = (todo.getProperty(Property.CATEGORIES) as? Categories)?.value,
                parentUid = parentUid,
                linkedEventUid = linkedEventUid,
                isCategory = todo.properties
                    .filterIsInstance<Property>()
                    .any { prop ->
                        prop.name.equals(X_TASKDAV_KIND, ignoreCase = true) &&
                            prop.value.equals(KIND_CATEGORY, ignoreCase = true)
                    },
                sortOrder = todo.properties
                    .filterIsInstance<Property>()
                    .firstOrNull { it.name.equals(X_TASKDAV_SORT, ignoreCase = true) }
                    ?.value
                    ?.toIntOrNull()
                    ?: 0,
                lastModifiedMillis = readLastModifiedMillis(todo.properties),
                icsRaw = ics,
            )
        }
    }

    fun parseEvents(ics: String): List<ParsedEvent> {
        val calendar = CalendarBuilder().build(StringReader(ics))
        val events = calendar.getComponents<VEvent>(Component.VEVENT)
        return events.map { event ->
            val start = event.startDate
            val allDay = start != null && start.date !is DateTime
            ParsedEvent(
                uid = event.uid?.value ?: UUID.randomUUID().toString(),
                summary = event.summary?.value.orEmpty().ifBlank { "(untitled)" },
                description = event.description?.value,
                location = event.location?.value
                    ?: (event.getProperty(Property.LOCATION) as? Location)?.value,
                dtStartMillis = start?.date?.toInstantMillis(),
                dtEndMillis = event.endDate?.date?.toInstantMillis(),
                allDay = allDay,
                rrule = (event.getProperty(Property.RRULE) as? RRule)?.value
                    ?: event.properties
                        .filterIsInstance<Property>()
                        .firstOrNull { it.name.equals(Property.RRULE, ignoreCase = true) }
                        ?.value,
                lastModifiedMillis = readLastModifiedMillis(event.properties),
                icsRaw = ics,
            )
        }
    }

    fun parseNotes(ics: String): List<ParsedNote> {
        val calendar = CalendarBuilder().build(StringReader(ics))
        val notes = calendar.getComponents<VJournal>(Component.VJOURNAL)
        return notes.map { note ->
            ParsedNote(
                uid = note.uid?.value ?: UUID.randomUUID().toString(),
                summary = note.summary?.value.orEmpty().ifBlank { "(untitled)" },
                description = note.description?.value,
                dtStartMillis = note.startDate?.date?.toInstantMillis(),
                categories = (note.getProperty(Property.CATEGORIES) as? Categories)?.value,
                lastModifiedMillis = readLastModifiedMillis(note.properties),
                icsRaw = ics,
            )
        }
    }

    fun buildNoteIcs(
        uid: String,
        summary: String,
        description: String?,
        dtStartMillis: Long?,
        categories: String?,
    ): String {
        val calendar = Calendar()
        calendar.properties.add(ProdId("-//TaskDav//EN"))
        calendar.properties.add(Version.VERSION_2_0)
        // false = do not auto-add DTSTAMP (we add exactly one below)
        val journal = VJournal(false)
        journal.properties.add(Uid(uid))
        journal.properties.add(DtStamp(DateTime(System.currentTimeMillis()).also { it.isUtc = true }))
        journal.properties.add(Summary(summary))
        if (!description.isNullOrBlank()) {
            journal.properties.add(Description(description))
        }
        if (dtStartMillis != null) {
            journal.properties.add(DtStart(DateTime(dtStartMillis).also { it.isUtc = true }))
        }
        addCategories(journal.properties, categories)
        calendar.components.add(journal)
        return outputCalendar(calendar)
    }

    fun buildTodoIcs(
        uid: String,
        summary: String,
        description: String?,
        status: String?,
        percentComplete: Int?,
        priority: Int?,
        dtStartMillis: Long?,
        dueMillis: Long?,
        completedMillis: Long?,
        categories: String?,
        parentUid: String?,
        linkedEventUid: String?,
        existingRaw: String?,
        isCategory: Boolean = false,
        sortOrder: Int = 0,
    ): String {
        val calendar = Calendar()
        calendar.properties.add(ProdId("-//TaskDav//EN"))
        calendar.properties.add(Version.VERSION_2_0)

        // false = do not auto-add DTSTAMP; duplicate DTSTAMP → Radicale HTTP 400
        val todo = VToDo(false)
        val now = System.currentTimeMillis()
        todo.properties.add(Uid(uid))
        todo.properties.add(DtStamp(DateTime(now).also { it.isUtc = true }))
        todo.properties.add(Summary(summary))
        if (!description.isNullOrBlank()) {
            todo.properties.add(Description(description))
        }
        todoStatus(status)?.let { todo.properties.add(it) }
        if (percentComplete != null) todo.properties.add(PercentComplete(percentComplete))
        if (priority != null && priority > 0) {
            todo.properties.add(Priority(priority.coerceIn(1, 9)))
        }
        if (dtStartMillis != null) {
            todo.properties.add(DtStart(DateTime(dtStartMillis).also { it.isUtc = true }))
        }
        if (dueMillis != null) {
            todo.properties.add(Due(DateTime(dueMillis).also { it.isUtc = true }))
        }
        if (completedMillis != null) {
            todo.properties.add(Completed(DateTime(completedMillis).also { it.isUtc = true }))
        }
        addCategories(todo.properties, categories)
        if (!parentUid.isNullOrBlank()) {
            val related = RelatedTo(parentUid)
            related.parameters.add(RelType.PARENT)
            todo.properties.add(related)
        }
        if (!linkedEventUid.isNullOrBlank()) {
            val related = RelatedTo(linkedEventUid)
            // SIBLING is a standard RELTYPE; servers keep it reliably next to PARENT
            related.parameters.add(RelType.SIBLING)
            todo.properties.add(related)
        }
        if (isCategory) {
            todo.properties.add(XProperty(X_TASKDAV_KIND, KIND_CATEGORY))
        }
        todo.properties.add(XProperty(X_TASKDAV_SORT, sortOrder.toString()))

        // Only preserve unknown X-properties / extras; never revive cleared DUE/DESCRIPTION/etc.
        if (!existingRaw.isNullOrBlank()) {
            try {
                val old = CalendarBuilder().build(StringReader(existingRaw))
                val oldTodo = old.getComponents<VToDo>(Component.VTODO).firstOrNull()
                if (oldTodo != null) {
                    @Suppress("UNCHECKED_CAST")
                    val props = ArrayList<Property>().apply {
                        addAll(oldTodo.properties as Collection<Property>)
                    }
                    for (prop in props) {
                        val name = prop.name
                        if (name in PRESERVE_SKIP) continue
                        if (name.equals(X_TASKDAV_KIND, ignoreCase = true)) continue
                        if (!name.startsWith("X-", ignoreCase = true)) continue
                        val exists = todo.properties.any { p ->
                            (p as Property).name.equals(name, ignoreCase = true)
                        }
                        if (!exists) todo.properties.add(prop)
                    }
                }
            } catch (_: Exception) {
                // ignore preserve failures
            }
        }

        calendar.components.add(todo)
        return outputCalendar(calendar)
    }

    fun buildEventIcs(
        uid: String,
        summary: String,
        description: String?,
        location: String?,
        dtStartMillis: Long,
        dtEndMillis: Long,
        allDay: Boolean,
        rrule: String? = null,
    ): String {
        val calendar = Calendar()
        calendar.properties.add(ProdId("-//TaskDav//EN"))
        calendar.properties.add(Version.VERSION_2_0)

        val event = VEvent(false)
        event.properties.add(Uid(uid))
        event.properties.add(DtStamp(DateTime(System.currentTimeMillis()).also { it.isUtc = true }))
        event.properties.add(Summary(summary))
        if (!description.isNullOrBlank()) event.properties.add(Description(description))
        if (!location.isNullOrBlank()) event.properties.add(Location(location.trim()))
        if (allDay) {
            val start = DtStart(Date(dtStartMillis))
            start.parameters.add(Value.DATE)
            event.properties.add(start)
            val end = DtEnd(Date(dtEndMillis))
            end.parameters.add(Value.DATE)
            event.properties.add(end)
        } else {
            event.properties.add(DtStart(DateTime(dtStartMillis).also { it.isUtc = true }))
            event.properties.add(DtEnd(DateTime(dtEndMillis).also { it.isUtc = true }))
        }
        val rule = rrule?.trim()?.takeIf { it.isNotEmpty() }
        if (rule != null) {
            event.properties.add(RRule(rule))
        }
        calendar.components.add(event)
        return outputCalendar(calendar)
    }

    fun newUid(): String = UUID.randomUUID().toString()

    fun normalizeUid(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return raw.trim()
            .removePrefix("<")
            .removeSuffix(">")
            .trim()
            .takeIf { it.isNotEmpty() }
    }

    fun extractUid(ics: String): String? =
        normalizeUid(
            Regex("""(?im)^UID:([^\r\n]+)""")
                .find(ics)
                ?.groupValues
                ?.getOrNull(1),
        )

    private fun todoStatus(status: String?): Status? {
        if (status.isNullOrBlank()) return Status.VTODO_NEEDS_ACTION
        return when (status.trim().uppercase()) {
            "NEEDS-ACTION", "NEEDS_ACTION" -> Status.VTODO_NEEDS_ACTION
            "COMPLETED" -> Status.VTODO_COMPLETED
            "IN-PROCESS", "IN_PROCESS" -> Status.VTODO_IN_PROCESS
            "CANCELLED", "CANCELED" -> Status.VTODO_CANCELLED
            else -> Status.VTODO_NEEDS_ACTION
        }
    }

    private fun addCategories(properties: MutableList<Property>, categories: String?) {
        val categoryList = categories.orEmpty()
            .split(',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (categoryList.isEmpty()) return
        properties.add(Categories(net.fortuna.ical4j.model.TextList(categoryList.toTypedArray())))
    }

    /** LAST-MODIFIED, else DTSTAMP — used for offline last-write-wins. */
    fun readLastModifiedMillis(properties: Iterable<*>): Long? {
        var lastMod: Long? = null
        var dtStamp: Long? = null
        for (prop in properties) {
            val p = prop as? Property ?: continue
            when {
                p.name.equals(Property.LAST_MODIFIED, ignoreCase = true) -> {
                    lastMod = (p as? LastModified)?.date?.toInstantMillis()
                        ?: p.value?.let { parseIcalDateMillis(it) }
                }
                p.name.equals(Property.DTSTAMP, ignoreCase = true) -> {
                    dtStamp = (p as? DtStamp)?.date?.toInstantMillis()
                        ?: p.value?.let { parseIcalDateMillis(it) }
                }
            }
        }
        return lastMod ?: dtStamp
    }

    private fun parseIcalDateMillis(raw: String): Long? {
        return try {
            val trimmed = raw.trim()
            if (trimmed.length == 8 && trimmed.all { it.isDigit() }) {
                Date(trimmed).toInstantMillis()
            } else {
                DateTime(trimmed).toInstantMillis()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun outputCalendar(calendar: Calendar): String {
        val writer = StringWriter()
        CalendarOutputter(false).output(calendar, writer)
        return writer.toString()
    }

    private val PRESERVE_SKIP = setOf(
        Property.UID, Property.SUMMARY, Property.DESCRIPTION, Property.STATUS,
        Property.PERCENT_COMPLETE, Property.PRIORITY, Property.DTSTART, Property.DUE,
        Property.COMPLETED, Property.CATEGORIES, Property.RELATED_TO, Property.CREATED,
        Property.LAST_MODIFIED, Property.DTSTAMP, Property.SEQUENCE,
        X_TASKDAV_KIND, X_TASKDAV_SORT,
    )
}

private fun java.util.Date.toInstantMillis(): Long {
    return if (this is DateTime) {
        time
    } else {
        val cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.time = this
        cal.timeInMillis
    }
}
