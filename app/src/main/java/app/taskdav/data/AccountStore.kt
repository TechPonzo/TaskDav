package app.taskdav.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class AccountCredentials(
    val baseUrl: String,
    val username: String,
    val password: String,
    val calendarHome: String? = null,
)

enum class SyncBackend(val id: String, val badgeLabel: String) {
    LOCAL("local", "LOCAL"),
    CALDAV("caldav", "CALDAV"),
    ;

    companion object {
        fun fromId(id: String?): SyncBackend =
            entries.find { it.id == id } ?: LOCAL
    }
}

class AccountStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "taskdav_account",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /**
     * Where data lives. Defaults to CalDAV if credentials already exist (upgrade path),
     * otherwise Local-only.
     */
    fun syncBackend(): SyncBackend {
        val stored = prefs.getString(KEY_SYNC_BACKEND, null)
        if (stored != null) return SyncBackend.fromId(stored)
        return if (isConfigured()) SyncBackend.CALDAV else SyncBackend.LOCAL
    }

    fun setSyncBackend(backend: SyncBackend) {
        prefs.edit().putString(KEY_SYNC_BACKEND, backend.id).apply()
    }

    fun isCalDavMode(): Boolean = syncBackend() == SyncBackend.CALDAV

    fun phoneCalendarMirrorEnabled(): Boolean =
        prefs.getBoolean(KEY_PHONE_CALENDAR_MIRROR, false)

    fun setPhoneCalendarMirrorEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PHONE_CALENDAR_MIRROR, enabled).apply()
    }

    fun isConfigured(): Boolean {
        val url = prefs.getString(KEY_BASE_URL, null)
        val user = prefs.getString(KEY_USERNAME, null)
        return !url.isNullOrBlank() && !user.isNullOrBlank()
    }

    fun load(): AccountCredentials? {
        if (!isConfigured()) return null
        return AccountCredentials(
            baseUrl = prefs.getString(KEY_BASE_URL, "")!!.trim().trimEnd('/'),
            username = prefs.getString(KEY_USERNAME, "")!!,
            password = prefs.getString(KEY_PASSWORD, "")!!,
            calendarHome = prefs.getString(KEY_CALENDAR_HOME, null),
        )
    }

    fun save(credentials: AccountCredentials) {
        prefs.edit()
            .putString(KEY_BASE_URL, credentials.baseUrl.trim().trimEnd('/'))
            .putString(KEY_USERNAME, credentials.username)
            .putString(KEY_PASSWORD, credentials.password)
            .putString(KEY_CALENDAR_HOME, credentials.calendarHome)
            .apply()
    }

    fun saveCalendarHome(home: String?) {
        prefs.edit().putString(KEY_CALENDAR_HOME, home).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun lastSyncMessage(): String? = prefs.getString(KEY_LAST_SYNC_MSG, null)

    fun lastSyncAt(): Long = prefs.getLong(KEY_LAST_SYNC_AT, 0L)

    fun setLastSync(at: Long, message: String) {
        prefs.edit()
            .putLong(KEY_LAST_SYNC_AT, at)
            .putString(KEY_LAST_SYNC_MSG, message)
            .apply()
    }

    companion object {
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_CALENDAR_HOME = "calendar_home"
        private const val KEY_LAST_SYNC_AT = "last_sync_at"
        private const val KEY_LAST_SYNC_MSG = "last_sync_msg"
        private const val KEY_SYNC_BACKEND = "sync_backend"
        private const val KEY_PHONE_CALENDAR_MIRROR = "phone_calendar_mirror"
    }
}
