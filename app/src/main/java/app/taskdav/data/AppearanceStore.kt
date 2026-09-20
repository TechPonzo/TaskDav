package app.taskdav.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

class AppearanceStore(private val context: Context) {
    val themeId: Flow<String> = context.appearanceDataStore.data.map { prefs ->
        prefs[KEY_THEME] ?: DEFAULT_THEME
    }

    val dateOrder: Flow<String> = context.appearanceDataStore.data.map { prefs ->
        prefs[KEY_DATE_ORDER] ?: DEFAULT_DATE_ORDER
    }

    suspend fun setThemeId(id: String) {
        context.appearanceDataStore.edit { it[KEY_THEME] = id }
    }

    suspend fun setDateOrder(id: String) {
        context.appearanceDataStore.edit { it[KEY_DATE_ORDER] = id }
    }

    companion object {
        const val DEFAULT_THEME = "forest"
        /** Day-first matches common EU usage; override with Phone default if needed. */
        const val DEFAULT_DATE_ORDER = "dmy"
        private val KEY_THEME = stringPreferencesKey("theme_id")
        private val KEY_DATE_ORDER = stringPreferencesKey("date_order")
    }
}
