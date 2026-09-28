package app.taskdav.ui.common

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.taskdav.data.LanguagePreference
import java.util.Locale

/**
 * Forces Compose [stringResource] lookups to use [language] immediately.
 *
 * Wraps the real Activity context and re-provides Activity-scoped owners so
 * permission launchers (e.g. Syncing calendar access) keep working.
 */
@Composable
fun ProvideAppLocale(
    language: LanguagePreference,
    content: @Composable () -> Unit,
) {
    val base = LocalContext.current
    val activityResultRegistryOwner = checkNotNull(LocalActivityResultRegistryOwner.current) {
        "LocalActivityResultRegistryOwner missing"
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    val localized = remember(language, base) {
        LocalizedContextWrapper(base, localeFor(language))
    }

    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides localized.resources.configuration,
        LocalActivityResultRegistryOwner provides activityResultRegistryOwner,
        LocalLifecycleOwner provides lifecycleOwner,
        content = content,
    )
}

private fun localeFor(language: LanguagePreference): Locale =
    when (language) {
        LanguagePreference.SYSTEM -> {
            val system = Resources.getSystem().configuration.locales
            if (system.isEmpty) Locale.getDefault() else system[0]
        }
        else -> Locale.forLanguageTag(language.tag)
    }

private class LocalizedContextWrapper(
    base: Context,
    locale: Locale,
) : ContextWrapper(base) {
    private val localized: Context = run {
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        base.createConfigurationContext(config)
    }

    override fun getResources(): Resources = localized.resources

    override fun getAssets(): AssetManager = localized.assets
}
