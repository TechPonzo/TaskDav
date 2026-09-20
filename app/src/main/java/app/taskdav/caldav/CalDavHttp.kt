package app.taskdav.caldav

import app.taskdav.data.AccountCredentials
import at.bitfire.dav4jvm.BasicDigestAuthHandler
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit

class CalDavHttpFactory {
    fun create(credentials: AccountCredentials): OkHttpClient {
        val auth = BasicDigestAuthHandler(
            /* domain = */ null,
            credentials.username,
            credentials.password,
        )
        return OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .authenticator(auth)
            .addNetworkInterceptor(auth)
            .build()
    }
}

object CalDavXml {
    val XML = "application/xml; charset=utf-8".toMediaType()
    val ICS = "text/calendar; charset=utf-8".toMediaType()

    const val NS_DAV = "DAV:"
    const val NS_CALDAV = "urn:ietf:params:xml:ns:caldav"
    const val NS_CS = "http://calendarserver.org/ns/"
    const val NS_APPLE = "http://apple.com/ns/ical/"

    fun propfindDepth(depth: Int, body: String): Pair<Map<String, String>, String> {
        return mapOf("Depth" to depth.toString()) to body
    }

    val CURRENT_USER_PRINCIPAL = """
        <?xml version="1.0" encoding="utf-8" ?>
        <d:propfind xmlns:d="DAV:">
          <d:prop>
            <d:current-user-principal/>
          </d:prop>
        </d:propfind>
    """.trimIndent()

    val CALENDAR_HOME = """
        <?xml version="1.0" encoding="utf-8" ?>
        <d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
          <d:prop>
            <c:calendar-home-set/>
          </d:prop>
        </d:propfind>
    """.trimIndent()

    val COLLECTIONS = """
        <?xml version="1.0" encoding="utf-8" ?>
        <d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav"
                    xmlns:cs="http://calendarserver.org/ns/"
                    xmlns:a="http://apple.com/ns/ical/">
          <d:prop>
            <d:displayname/>
            <d:resourcetype/>
            <cs:getctag/>
            <d:sync-token/>
            <c:supported-calendar-component-set/>
            <a:calendar-color/>
          </d:prop>
        </d:propfind>
    """.trimIndent()

    fun calendarQuery(component: String): String = """
        <?xml version="1.0" encoding="utf-8" ?>
        <c:calendar-query xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
          <d:prop>
            <d:getetag/>
            <c:calendar-data/>
          </d:prop>
          <c:filter>
            <c:comp-filter name="VCALENDAR">
              <c:comp-filter name="$component"/>
            </c:comp-filter>
          </c:filter>
        </c:calendar-query>
    """.trimIndent()

    fun calendarQueryEventByUid(uid: String): String {
        val escaped = uid
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
        return """
            <?xml version="1.0" encoding="utf-8" ?>
            <c:calendar-query xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
              <d:prop>
                <d:getetag/>
                <c:calendar-data/>
              </d:prop>
              <c:filter>
                <c:comp-filter name="VCALENDAR">
                  <c:comp-filter name="VEVENT">
                    <c:prop-filter name="UID">
                      <c:text-match collation="i;octet">$escaped</c:text-match>
                    </c:prop-filter>
                  </c:comp-filter>
                </c:comp-filter>
              </c:filter>
            </c:calendar-query>
        """.trimIndent()
    }
}

fun OkHttpClient.propfind(url: String, depth: Int, body: String): Response {
    val request = Request.Builder()
        .url(url)
        .method("PROPFIND", body.toRequestBody(CalDavXml.XML))
        .header("Depth", depth.toString())
        .header("Content-Type", "application/xml; charset=utf-8")
        .build()
    return newCall(request).execute()
}

fun OkHttpClient.report(url: String, body: String): Response {
    val request = Request.Builder()
        .url(url)
        .method("REPORT", body.toRequestBody(CalDavXml.XML))
        .header("Depth", "1")
        .header("Content-Type", "application/xml; charset=utf-8")
        .build()
    return newCall(request).execute()
}

fun OkHttpClient.putIcs(url: String, ics: String, etag: String?): Response {
    val builder = Request.Builder()
        .url(url)
        .put(ics.toRequestBody(CalDavXml.ICS))
        .header("Content-Type", "text/calendar; charset=utf-8")
    // Only use If-Match when updating a known resource. Prefer unconditional create
    // (Radicale + Digest often mishandle If-None-Match: * on first PUT).
    if (!etag.isNullOrBlank()) {
        builder.header("If-Match", quotedEtag(etag))
    }
    return newCall(builder.build()).execute()
}

/** PUT without conditional headers (overwrite / create). */
fun OkHttpClient.putIcsUnconditional(url: String, ics: String): Response {
    val request = Request.Builder()
        .url(url)
        .put(ics.toRequestBody(CalDavXml.ICS))
        .header("Content-Type", "text/calendar; charset=utf-8")
        .build()
    return newCall(request).execute()
}

fun OkHttpClient.getResource(url: String): Response {
    return newCall(Request.Builder().url(url).get().build()).execute()
}

fun OkHttpClient.deleteResource(url: String, etag: String?): Response {
    val builder = Request.Builder().url(url).delete()
    if (!etag.isNullOrBlank()) {
        builder.header("If-Match", quotedEtag(etag))
    }
    return newCall(builder.build()).execute()
}

fun OkHttpClient.deleteResourceUnconditional(url: String): Response {
    return newCall(Request.Builder().url(url).delete().build()).execute()
}

/** RFC 7232 opaque-tag for If-Match / If-None-Match. */
fun quotedEtag(etag: String): String {
    val raw = etag.trim()
        .removePrefix("W/")
        .trim()
        .trim('"')
    return "\"$raw\""
}

fun Response.requireSuccess(action: String): String {
    val text = body?.string().orEmpty()
    if (!isSuccessful && code !in 200..299) {
        throw CalDavException("$action failed: HTTP $code ${message.orEmpty()}\n$text".trim())
    }
    return text
}

class CalDavException(message: String, cause: Throwable? = null) : Exception(message, cause)
