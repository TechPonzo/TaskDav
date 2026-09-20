package app.taskdav.caldav

import android.graphics.Color
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URI

data class RemoteCollection(
    val href: String,
    val displayName: String,
    val colorArgb: Int?,
    val supportsVtodo: Boolean,
    val supportsVevent: Boolean,
    val supportsVjournal: Boolean,
    val ctag: String?,
    val syncToken: String?,
)

data class RemoteObject(
    val href: String,
    val etag: String?,
    val ics: String,
)

object MultistatusParser {
    fun firstHref(xml: String, localName: String): String? {
        val parser = newParser(xml)
        var event = parser.eventType
        var capture = false
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (parser.name.equals(localName, ignoreCase = true)) {
                        capture = true
                    } else if (capture && parser.name.equals("href", ignoreCase = true)) {
                        return parser.nextText().trim()
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals(localName, ignoreCase = true)) capture = false
                }
            }
            event = parser.next()
        }
        return null
    }

    fun parseCollections(xml: String, baseUrl: String): List<RemoteCollection> {
        val base = URI(ensureTrailingSlash(baseUrl))
        val results = mutableListOf<RemoteCollection>()
        val parser = newParser(xml)
        var event = parser.eventType
        var inResponse = false
        var href: String? = null
        var displayName: String? = null
        var color: String? = null
        var ctag: String? = null
        var syncToken: String? = null
        var isCalendar = false
        var supportsVtodo = false
        var supportsVevent = false
        var supportsVjournal = false
        var inResourceType = false
        var inCompSet = false

        fun flush() {
            if (inResponse && isCalendar && !href.isNullOrBlank()) {
                val absolute = resolveHref(base, href!!)
                results += RemoteCollection(
                    href = absolute,
                    displayName = displayName?.ifBlank { null } ?: absolute.trimEnd('/').substringAfterLast('/'),
                    colorArgb = parseAppleColor(color),
                    supportsVtodo = supportsVtodo,
                    supportsVevent = supportsVevent,
                    supportsVjournal = supportsVjournal,
                    ctag = ctag,
                    syncToken = syncToken,
                )
            }
            href = null
            displayName = null
            color = null
            ctag = null
            syncToken = null
            isCalendar = false
            supportsVtodo = false
            supportsVevent = false
            supportsVjournal = false
            inResourceType = false
            inCompSet = false
            inResponse = false
        }

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name
                    when {
                        name.equals("response", ignoreCase = true) -> inResponse = true
                        inResponse && name.equals("href", ignoreCase = true) && href == null ->
                            href = parser.nextText().trim()
                        inResponse && name.equals("displayname", ignoreCase = true) ->
                            displayName = parser.nextText().trim()
                        inResponse && name.equals("calendar-color", ignoreCase = true) ->
                            color = parser.nextText().trim()
                        inResponse && name.equals("getctag", ignoreCase = true) ->
                            ctag = parser.nextText().trim()
                        inResponse && name.equals("sync-token", ignoreCase = true) ->
                            syncToken = parser.nextText().trim()
                        inResponse && name.equals("resourcetype", ignoreCase = true) ->
                            inResourceType = true
                        inResourceType && name.equals("calendar", ignoreCase = true) ->
                            isCalendar = true
                        inResponse && name.equals("supported-calendar-component-set", ignoreCase = true) ->
                            inCompSet = true
                        inCompSet && name.equals("comp", ignoreCase = true) -> {
                            when (parser.getAttributeValue(null, "name")?.uppercase()) {
                                "VTODO" -> supportsVtodo = true
                                "VEVENT" -> supportsVevent = true
                                "VJOURNAL" -> supportsVjournal = true
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name
                    when {
                        name.equals("response", ignoreCase = true) -> flush()
                        name.equals("resourcetype", ignoreCase = true) -> inResourceType = false
                        name.equals("supported-calendar-component-set", ignoreCase = true) -> inCompSet = false
                    }
                }
            }
            event = parser.next()
        }
        return results
    }

    fun parseCalendarObjects(xml: String, baseUrl: String): List<RemoteObject> {
        val base = URI(ensureTrailingSlash(baseUrl))
        val results = mutableListOf<RemoteObject>()
        val parser = newParser(xml)
        var event = parser.eventType
        var inResponse = false
        var href: String? = null
        var etag: String? = null
        var calendarData: String? = null
        var statusOk = true

        fun flush() {
            if (inResponse && statusOk && !href.isNullOrBlank() && !calendarData.isNullOrBlank()) {
                results += RemoteObject(
                    href = resolveHref(base, href!!),
                    etag = etag,
                    ics = calendarData!!.trim(),
                )
            }
            href = null
            etag = null
            calendarData = null
            statusOk = true
            inResponse = false
        }

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name
                    when {
                        name.equals("response", ignoreCase = true) -> inResponse = true
                        inResponse && name.equals("href", ignoreCase = true) && href == null ->
                            href = parser.nextText().trim()
                        inResponse && name.equals("getetag", ignoreCase = true) ->
                            etag = parser.nextText().trim().trim('"')
                        inResponse && name.equals("calendar-data", ignoreCase = true) ->
                            calendarData = parser.nextText()
                        inResponse && name.equals("status", ignoreCase = true) -> {
                            val status = parser.nextText()
                            if (status.contains("404") || status.contains("410")) statusOk = false
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("response", ignoreCase = true)) flush()
                }
            }
            event = parser.next()
        }
        return results
    }

    fun parseAppleColor(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        var hex = raw.trim()
        if (!hex.startsWith("#")) hex = "#$hex"
        return try {
            when (hex.length) {
                9 -> { // #RRGGBBAA
                    val rgb = hex.substring(0, 7)
                    Color.parseColor(rgb)
                }
                7 -> Color.parseColor(hex)
                else -> null
            }
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun newParser(xml: String): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        return factory.newPullParser().apply {
            setInput(StringReader(xml))
        }
    }

    fun ensureTrailingSlash(url: String): String =
        if (url.endsWith("/")) url else "$url/"

    fun resolveHref(base: URI, href: String): String {
        val resolved = if (href.startsWith("http://") || href.startsWith("https://")) {
            URI(href)
        } else {
            base.resolve(href)
        }
        return resolved.normalize().toString()
    }

    fun joinUrl(base: String, path: String): String {
        val b = ensureTrailingSlash(base)
        val p = path.removePrefix("/")
        return URI(b).resolve(p).normalize().toString()
    }
}
