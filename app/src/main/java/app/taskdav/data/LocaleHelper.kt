package app.taskdav.data

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

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
     * the Activity and flashes black. Live UI updates via [ProvideAppLocale].
     */
    fun persist(context: Context, preference: LanguagePreference) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, preference.id)
            .commit()
    }

    fun current(context: Context): LanguagePreference {
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, LanguagePreference.SYSTEM.id)
        return LanguagePreference.fromId(id)
    }

    /** Locale matching the stored app language preference. */
    fun localeOf(context: Context): Locale {
        val preference = current(context)
        if (preference == LanguagePreference.SYSTEM || preference.tag.isBlank()) {
            val system = Resources.getSystem().configuration.locales
            return if (system.isEmpty) Locale.getDefault() else system[0]
        }
        return Locale.forLanguageTag(preference.tag)
    }

    /**
     * Context whose resources / [Context.getString] follow the stored app language.
     * Glance widgets sit outside Compose [ProvideAppLocale], so they must use this.
     */
    fun wrap(base: Context): Context {
        val locale = localeOf(base)
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }

    private const val PREFS = "taskdav_locale"
    private const val KEY_LANGUAGE = "language"
}
