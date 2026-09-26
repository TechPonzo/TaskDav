package app.taskdav.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider as GlanceColorProvider
import app.taskdav.TaskDavApp
import app.taskdav.data.AppearanceStore
import app.taskdav.ui.theme.schemesFromSeed
import kotlinx.coroutines.flow.first

data class WidgetColors(
    val primary: GlanceColorProvider,
    val onPrimary: GlanceColorProvider,
    val primaryContainer: GlanceColorProvider,
    val onPrimaryContainer: GlanceColorProvider,
    val surface: GlanceColorProvider,
    val onSurface: GlanceColorProvider,
    val onSurfaceVariant: GlanceColorProvider,
    val outlineVariant: GlanceColorProvider,
)

object WidgetTheme {
    suspend fun colors(context: Context): WidgetColors {
        val seedArgb = when (val app = context.applicationContext) {
            is TaskDavApp -> runCatching { app.appearanceStore.seedColorArgb.first() }
                .getOrDefault(AppearanceStore.DEFAULT_SEED_COLOR)
            else -> AppearanceStore.DEFAULT_SEED_COLOR
        }
        val (light, dark) = schemesFromSeed(Color(seedArgb))
        return WidgetColors(
            primary = ColorProvider(day = light.primary, night = dark.primary),
            onPrimary = ColorProvider(day = light.onPrimary, night = dark.onPrimary),
            primaryContainer = ColorProvider(
                day = light.primaryContainer,
                night = dark.primaryContainer,
            ),
            onPrimaryContainer = ColorProvider(
                day = light.onPrimaryContainer,
                night = dark.onPrimaryContainer,
            ),
            surface = ColorProvider(day = light.surface, night = dark.surface),
            onSurface = ColorProvider(day = light.onSurface, night = dark.onSurface),
            onSurfaceVariant = ColorProvider(
                day = light.onSurfaceVariant,
                night = dark.onSurfaceVariant,
            ),
            outlineVariant = ColorProvider(
                day = light.outlineVariant,
                night = dark.outlineVariant,
            ),
        )
    }
}
