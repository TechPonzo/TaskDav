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

class AppearanceStore(private val context: Context) {
    val themeId: Flow<String> = context.appearanceDataStore.data.map { prefs ->
        prefs[KEY_THEME] ?: DEFAULT_THEME
    }

    suspend fun setThemeId(id: String) {
        context.appearanceDataStore.edit { it[KEY_THEME] = id }
    }

    companion object {
        const val DEFAULT_THEME = "forest"
        private val KEY_THEME = stringPreferencesKey("theme_id")
    }
}
