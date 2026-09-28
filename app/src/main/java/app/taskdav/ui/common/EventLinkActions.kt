package app.taskdav.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.vector.ImageVector
import app.taskdav.R
import java.util.Locale
import java.util.regex.Pattern

enum class EventLinkKind {
    MEET,
    HANGOUTS,
    ZOOM,
    TEAMS,
    WEBEX,
    MAPS,
    URL,
    ADDRESS,
}

data class EventLinkAction(
    val kind: EventLinkKind,
    val label: String,
    val icon: ImageVector,
    val uri: Uri,
)

private val HttpUrlPattern: Pattern = Pattern.compile(
    """https?://[^\s<>"')\]]+""",
    Pattern.CASE_INSENSITIVE,
)

/**
 * Prefer [location], then the first http(s) URL in [description].
 * Returns null when there is nothing actionable to open.
 */
fun resolveEventLink(context: Context, location: String?, description: String?): EventLinkAction? {
    val loc = location?.trim()?.takeIf { it.isNotEmpty() }
    if (loc != null) {
        classifyCandidate(context, loc)?.let { return it }
    }
    val descUrl = description?.let { firstHttpUrl(it) }
    if (descUrl != null) {
        classifyCandidate(context, descUrl)?.let { return it }
    }
    return null
}

fun openEventLink(context: Context, location: String?, description: String?) {
    val action = resolveEventLink(context, location, description) ?: return
    openUri(context, action.uri, fallbackMapsQuery = if (action.kind == EventLinkKind.ADDRESS) {
        location?.trim()?.takeIf { it.isNotEmpty() }
    } else {
        null
    })
}

/** Kept for call sites that only have a plain address / maps query. */
fun openLocationInMaps(context: Context, location: String) {
    openEventLink(context, location, description = null)
}

private fun classifyCandidate(context: Context, raw: String): EventLinkAction? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null

    if (trimmed.startsWith("geo:", ignoreCase = true)) {
        return EventLinkAction(
            kind = EventLinkKind.MAPS,
            label = context.getString(R.string.link_open_maps),
            icon = Icons.Default.Place,
            uri = Uri.parse(trimmed),
        )
    }

    val asUrl = coerceToUri(trimmed)
    if (asUrl != null) {
        val host = asUrl.host?.lowercase(Locale.US).orEmpty()
        return when {
            host.contains("meet.google.com") -> EventLinkAction(
                kind = EventLinkKind.MEET,
                label = context.getString(R.string.link_join_meet),
                icon = Icons.Default.Videocam,
                uri = asUrl,
            )
            host.contains("hangouts.google.com") ||
                host.contains("duo.google.com") -> EventLinkAction(
                kind = EventLinkKind.HANGOUTS,
                label = context.getString(R.string.link_open_hangouts),
                icon = Icons.Default.Videocam,
                uri = asUrl,
            )
            host.contains("zoom.us") || host.contains("zoom.com") -> EventLinkAction(
                kind = EventLinkKind.ZOOM,
                label = context.getString(R.string.link_join_zoom),
                icon = Icons.Default.Videocam,
                uri = asUrl,
            )
            host.contains("teams.microsoft.com") ||
                host.contains("teams.live.com") -> EventLinkAction(
                kind = EventLinkKind.TEAMS,
                label = context.getString(R.string.link_join_teams),
                icon = Icons.Default.Videocam,
                uri = asUrl,
            )
            host.contains("webex.com") -> EventLinkAction(
                kind = EventLinkKind.WEBEX,
                label = context.getString(R.string.link_join_webex),
                icon = Icons.Default.Videocam,
                uri = asUrl,
            )
            host.contains("maps.google.") ||
                (host.contains("google.com") && asUrl.path.orEmpty().startsWith("/maps")) ||
                (host == "goo.gl" && asUrl.path.orEmpty().startsWith("/maps")) ||
                host.contains("maps.app.goo.gl") -> EventLinkAction(
                kind = EventLinkKind.MAPS,
                label = context.getString(R.string.link_open_maps),
                icon = Icons.Default.Place,
                uri = asUrl,
            )
            else -> EventLinkAction(
                kind = EventLinkKind.URL,
                label = context.getString(R.string.link_open_url),
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                uri = asUrl,
            )
        }
    }

    return EventLinkAction(
        kind = EventLinkKind.ADDRESS,
        label = context.getString(R.string.link_open_maps),
        icon = Icons.Default.Place,
        uri = Uri.parse("geo:0,0?q=${Uri.encode(trimmed)}"),
    )
}

private fun coerceToUri(raw: String): Uri? {
    val candidate = when {
        raw.startsWith("http://", ignoreCase = true) ||
            raw.startsWith("https://", ignoreCase = true) -> raw
        raw.contains("://") -> raw
        raw.contains('.') && !raw.contains(' ') &&
            (raw.contains("meet.google", ignoreCase = true) ||
                raw.contains("zoom.", ignoreCase = true) ||
                raw.contains("teams.", ignoreCase = true) ||
                raw.contains("webex.", ignoreCase = true) ||
                raw.contains("maps.google", ignoreCase = true) ||
                raw.contains("maps.app.goo.gl", ignoreCase = true)) -> "https://$raw"
        else -> return null
    }
    val uri = runCatching { Uri.parse(candidate) }.getOrNull() ?: return null
    return when {
        uri.scheme.equals("geo", ignoreCase = true) -> uri
        !uri.scheme.isNullOrBlank() && !uri.host.isNullOrBlank() -> uri
        else -> null
    }
}

private fun firstHttpUrl(text: String): String? {
    val matcher = HttpUrlPattern.matcher(text)
    if (!matcher.find()) return null
    return matcher.group()?.trimEnd('.', ',', ';', ')', ']', '"', '\'')
}

private fun openUri(context: Context, uri: Uri, fallbackMapsQuery: String?) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        if (fallbackMapsQuery != null) {
            val web = Uri.parse("https://maps.google.com/?q=${Uri.encode(fallbackMapsQuery)}")
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, web).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (_: ActivityNotFoundException) {
            }
        }
    }
}
