package app.taskdav.caldav

import app.taskdav.data.AccountCredentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.net.URI

class CollectionDiscoverer(
    private val httpFactory: CalDavHttpFactory = CalDavHttpFactory(),
) {
    fun testAndDiscover(credentials: AccountCredentials): DiscoveryResult {
        val client = httpFactory.create(credentials)
        val base = MultistatusParser.ensureTrailingSlash(credentials.baseUrl)
        requireValidUrl(base)

        // Prefer stored calendar home; otherwise discover; finally treat base as home (Radicale).
        val calendarHome = credentials.calendarHome?.takeIf { it.isNotBlank() }
            ?: discoverCalendarHome(client, base)
            ?: base

        val collections = listCollections(client, calendarHome)
        return DiscoveryResult(
            calendarHome = MultistatusParser.ensureTrailingSlash(calendarHome),
            collections = collections,
        )
    }

    fun listCollections(credentials: AccountCredentials, calendarHome: String): List<RemoteCollection> {
        val client = httpFactory.create(credentials)
        return listCollections(client, calendarHome)
    }

    private fun discoverCalendarHome(client: OkHttpClient, base: String): String? {
        return try {
            val principalHref = propfindHref(client, base, CalDavXml.CURRENT_USER_PRINCIPAL, "current-user-principal")
                ?: return null
            val principalUrl = MultistatusParser.resolveHref(URI(base), principalHref)
            val homeHref = propfindHref(client, principalUrl, CalDavXml.CALENDAR_HOME, "calendar-home-set")
                ?: return null
            MultistatusParser.resolveHref(URI(MultistatusParser.ensureTrailingSlash(principalUrl)), homeHref)
        } catch (_: Exception) {
            null
        }
    }

    private fun propfindHref(
        client: OkHttpClient,
        url: String,
        body: String,
        containerLocalName: String,
    ): String? {
        client.propfind(url, depth = 0, body = body).use { response ->
            val xml = response.requireSuccess("PROPFIND $containerLocalName")
            return MultistatusParser.firstHref(xml, containerLocalName)
        }
    }

    private fun listCollections(client: OkHttpClient, calendarHome: String): List<RemoteCollection> {
        val home = MultistatusParser.ensureTrailingSlash(calendarHome)
        client.propfind(home, depth = 1, body = CalDavXml.COLLECTIONS).use { response ->
            val xml = response.requireSuccess("PROPFIND collections")
            return MultistatusParser.parseCollections(xml, home)
                .filter { it.supportsVtodo || it.supportsVevent || it.supportsVjournal }
        }
    }

    private fun requireValidUrl(url: String) {
        if (url.toHttpUrlOrNull() == null) {
            throw CalDavException("Invalid URL: $url")
        }
    }
}

data class DiscoveryResult(
    val calendarHome: String,
    val collections: List<RemoteCollection>,
)
