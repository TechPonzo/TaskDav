package app.taskdav.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.taskdav.ui.theme.DEFAULT_SEED_ARGB
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appearanceDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "taskdav_appearance",
)

enum class DateOrderPreference(val id: String, val label: String) {
    SYSTEM("system", "Phone default"),
    DAY_MONTH_YEAR("dmy", "Day / month / year"),
    MONTH_DAY_YEAR("mdy", "Month / day / year"),
    ;

    companion object {
        fun fromId(id: String?): DateOrderPreference =
            entries.find { it.id == id } ?: DAY_MONTH_YEAR
    }
}

enum class CalendarViewMode(val id: String, val label: String) {
    DAILY("daily", "Daily view"),
    WEEKLY("weekly", "Weekly view"),
    MONTHLY("monthly", "Monthly view"),
    MONTHLY_AND_DAILY("monthly_daily", "Monthly and daily view"),
    YEARLY("yearly", "Yearly view"),
    EVENT_LIST("event_list", "Simple event list"),
    ;

    companion object {
        fun fromId(id: String?): CalendarViewMode =
            entries.find { it.id == id } ?: MONTHLY_AND_DAILY
    }
}

class AppearanceStore(private val context: Context) {
    val seedColorArgb: Flow<Int> = context.appearanceDataStore.data.map { prefs ->
        prefs[KEY_SEED_COLOR] ?: seedFromLegacyTheme(prefs[KEY_THEME])
    }

    val dateOrder: Flow<String> = context.appearanceDataStore.data.map { prefs ->
        prefs[KEY_DATE_ORDER] ?: DEFAULT_DATE_ORDER
    }

    val calendarViewMode: Flow<String> = context.appearanceDataStore.data.map { prefs ->
        prefs[KEY_CALENDAR_VIEW] ?: DEFAULT_CALENDAR_VIEW
    }

    suspend fun setSeedColorArgb(argb: Int) {
        context.appearanceDataStore.edit {
            it[KEY_SEED_COLOR] = argb
            it.remove(KEY_THEME)
        }
    }

    suspend fun setDateOrder(id: String) {
        context.appearanceDataStore.edit { it[KEY_DATE_ORDER] = id }
    }

    suspend fun setCalendarViewMode(id: String) {
        context.appearanceDataStore.edit { it[KEY_CALENDAR_VIEW] = id }
    }

    companion object {
        val DEFAULT_SEED_COLOR: Int = DEFAULT_SEED_ARGB
        /** Day-first matches common EU usage; override with Phone default if needed. */
        const val DEFAULT_DATE_ORDER = "dmy"
        const val DEFAULT_CALENDAR_VIEW = "monthly_daily"
        private val KEY_SEED_COLOR = intPreferencesKey("seed_color_argb")
        private val KEY_THEME = stringPreferencesKey("theme_id")
        private val KEY_DATE_ORDER = stringPreferencesKey("date_order")
        private val KEY_CALENDAR_VIEW = stringPreferencesKey("calendar_view_mode")

        private fun seedFromLegacyTheme(themeId: String?): Int = when (themeId) {
            "ocean" -> (0xFF shl 24) or (0x15 shl 16) or (0x65 shl 8) or 0xA0
            "sand" -> (0xFF shl 24) or (0x7A shl 16) or (0x5C shl 8) or 0x00
            "slate" -> (0xFF shl 24) or (0x3F shl 16) or (0x4A shl 8) or 0x5A
            "high_contrast" -> (0xFF shl 24)
            else -> DEFAULT_SEED_COLOR
        }
    }
}
