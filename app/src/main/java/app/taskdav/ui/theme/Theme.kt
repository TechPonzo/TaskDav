package app.taskdav.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.taskdav.TaskDavApp
import app.taskdav.data.AppearanceStore

enum class AppThemeId(val id: String, val label: String) {
    Forest("forest", "Forest"),
    Ocean("ocean", "Ocean"),
    Sand("sand", "Sand"),
    Slate("slate", "Slate"),
    HighContrast("high_contrast", "High contrast"),
    ;

    companion object {
        fun fromId(id: String): AppThemeId =
            entries.find { it.id == id } ?: Forest
    }
}

data class ThemePreviewColors(
    val primary: Color,
    val background: Color,
    val surface: Color,
)

fun AppThemeId.previewLight(): ThemePreviewColors = when (this) {
    AppThemeId.Forest -> ThemePreviewColors(Color(0xFF2B6A4F), Color(0xFFF3EDE3), Color(0xFFFBF8F3))
    AppThemeId.Ocean -> ThemePreviewColors(Color(0xFF1B6CA8), Color(0xFFE8F2F8), Color(0xFFF5FAFD))
    AppThemeId.Sand -> ThemePreviewColors(Color(0xFF8B6914), Color(0xFFF7F1E3), Color(0xFFFFFBF2))
    AppThemeId.Slate -> ThemePreviewColors(Color(0xFF4A5568), Color(0xFFEEF1F4), Color(0xFFF8FAFC))
    AppThemeId.HighContrast -> ThemePreviewColors(Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFFFFFFFF))
}

private fun schemesFor(id: AppThemeId): Pair<ColorScheme, ColorScheme> = when (id) {
    AppThemeId.Forest -> lightColorScheme(
        primary = Color(0xFF2B6A4F),
        onPrimary = Color.White,
        secondary = Color(0xFF1E4A38),
        onSecondary = Color.White,
        background = Color(0xFFF3EDE3),
        onBackground = Color(0xFF1A1F1C),
        surface = Color(0xFFFBF8F3),
        onSurface = Color(0xFF1A1F1C),
    ) to darkColorScheme(
        primary = Color(0xFF7CBC9E),
        onPrimary = Color(0xFF1A1F1C),
        secondary = Color(0xFFA8D5C0),
        background = Color(0xFF121612),
        onBackground = Color(0xFFE8EEE9),
        surface = Color(0xFF1A201C),
        onSurface = Color(0xFFE8EEE9),
    )
    AppThemeId.Ocean -> lightColorScheme(
        primary = Color(0xFF1B6CA8),
        onPrimary = Color.White,
        secondary = Color(0xFF0E4A78),
        onSecondary = Color.White,
        background = Color(0xFFE8F2F8),
        onBackground = Color(0xFF0F1C28),
        surface = Color(0xFFF5FAFD),
        onSurface = Color(0xFF0F1C28),
    ) to darkColorScheme(
        primary = Color(0xFF7EB8E0),
        onPrimary = Color(0xFF0F1C28),
        secondary = Color(0xFFA8D0EC),
        background = Color(0xFF0D141C),
        onBackground = Color(0xFFE4EEF6),
        surface = Color(0xFF15202A),
        onSurface = Color(0xFFE4EEF6),
    )
    AppThemeId.Sand -> lightColorScheme(
        primary = Color(0xFF8B6914),
        onPrimary = Color.White,
        secondary = Color(0xFF5C470E),
        onSecondary = Color.White,
        background = Color(0xFFF7F1E3),
        onBackground = Color(0xFF2A2418),
        surface = Color(0xFFFFFBF2),
        onSurface = Color(0xFF2A2418),
    ) to darkColorScheme(
        primary = Color(0xFFD4B45A),
        onPrimary = Color(0xFF2A2418),
        secondary = Color(0xFFE6C97A),
        background = Color(0xFF1A1710),
        onBackground = Color(0xFFF3EBD8),
        surface = Color(0xFF242018),
        onSurface = Color(0xFFF3EBD8),
    )
    AppThemeId.Slate -> lightColorScheme(
        primary = Color(0xFF4A5568),
        onPrimary = Color.White,
        secondary = Color(0xFF2D3748),
        onSecondary = Color.White,
        background = Color(0xFFEEF1F4),
        onBackground = Color(0xFF1A202C),
        surface = Color(0xFFF8FAFC),
        onSurface = Color(0xFF1A202C),
    ) to darkColorScheme(
        primary = Color(0xFFA0AEC0),
        onPrimary = Color(0xFF1A202C),
        secondary = Color(0xFFCBD5E0),
        background = Color(0xFF12161C),
        onBackground = Color(0xFFE2E8F0),
        surface = Color(0xFF1A202C),
        onSurface = Color(0xFFE2E8F0),
    )
    AppThemeId.HighContrast -> lightColorScheme(
        primary = Color(0xFF000000),
        onPrimary = Color.White,
        secondary = Color(0xFF222222),
        onSecondary = Color.White,
        background = Color(0xFFFFFFFF),
        onBackground = Color(0xFF000000),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF000000),
        outline = Color(0xFF000000),
    ) to darkColorScheme(
        primary = Color(0xFFFFFFFF),
        onPrimary = Color(0xFF000000),
        secondary = Color(0xFFEEEEEE),
        background = Color(0xFF000000),
        onBackground = Color(0xFFFFFFFF),
        surface = Color(0xFF000000),
        onSurface = Color(0xFFFFFFFF),
        outline = Color(0xFFFFFFFF),
    )
}

@Composable
fun TaskDavTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as? TaskDavApp
    val themeIdFlow = app?.appearanceStore?.themeId
    val themeId by if (themeIdFlow != null) {
        themeIdFlow.collectAsState(initial = AppearanceStore.DEFAULT_THEME)
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(AppearanceStore.DEFAULT_THEME) }
    }
    val dark = isSystemInDarkTheme()
    val (light, darkScheme) = schemesFor(AppThemeId.fromId(themeId))
    MaterialTheme(
        colorScheme = if (dark) darkScheme else light,
        content = content,
    )
}

fun collectionColorOrDefault(argb: Int?, fallback: Color = Color(0xFF2B6A4F)): Color {
    return if (argb != null) Color(argb) else fallback
}
