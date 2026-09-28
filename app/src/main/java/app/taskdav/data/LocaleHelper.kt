package app.taskdav.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

enum class LanguagePreference(val id: String, val tag: String) {
    SYSTEM("system", ""),
    ENGLISH("en", "en"),
    SPANISH("es", "es"),
    ITALIAN("it", "it"),
    ;

    companion object {
        fun fromId(id: String?): LanguagePreference =
            entries.find { it.id == id } ?: SYSTEM
    }
}

object LocaleHelper {
    fun apply(preference: LanguagePreference) {
        val locales = if (preference == LanguagePreference.SYSTEM || preference.tag.isBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(preference.tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun applyStored(context: Context) {
        apply(current(context))
    }

    /**
     * Saves the preference for the next cold start.
     * Does **not** call [AppCompatDelegate.setApplicationLocales] — that recreates
     * the Activity and flashes black. Live UI updates via [app.taskdav.ui.common.ProvideAppLocale].
     */
    fun persist(context: Context, preference: LanguagePreference) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, preference.id)
            .apply()
    }

    fun current(context: Context): LanguagePreference {
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, LanguagePreference.SYSTEM.id)
        return LanguagePreference.fromId(id)
    }

    private const val PREFS = "taskdav_locale"
    private const val KEY_LANGUAGE = "language"
}
