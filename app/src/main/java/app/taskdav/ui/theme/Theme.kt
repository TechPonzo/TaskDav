package app.taskdav.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.taskdav.TaskDavApp
import app.taskdav.data.AppearanceStore
import app.taskdav.data.DateOrderPreference
import app.taskdav.ui.common.DateFormats

@Composable
fun TaskDavTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as? TaskDavApp
    val seedArgb by if (app?.appearanceStore != null) {
        app.appearanceStore.seedColorArgb.collectAsState(initial = AppearanceStore.DEFAULT_SEED_COLOR)
    } else {
        remember { mutableStateOf(AppearanceStore.DEFAULT_SEED_COLOR) }
    }
    val dateOrderId by if (app?.appearanceStore != null) {
        app.appearanceStore.dateOrder.collectAsState(initial = AppearanceStore.DEFAULT_DATE_ORDER)
    } else {
        remember {
            mutableStateOf(AppearanceStore.DEFAULT_DATE_ORDER)
        }
    }
    val dateOrder = DateOrderPreference.fromId(dateOrderId)
    SideEffect {
        DateFormats.setOrderPreference(dateOrder)
    }
    val dark = isSystemInDarkTheme()
    val (light, darkScheme) = remember(seedArgb) {
        schemesFromSeed(Color(seedArgb))
    }
    MaterialTheme(
        colorScheme = if (dark) darkScheme else light,
        typography = TaskDavTypography,
        shapes = TaskDavShapes,
        content = content,
    )
}

fun collectionColorOrDefault(argb: Int?, fallback: Color = Color(DEFAULT_SEED_ARGB)): Color {
    return if (argb != null) Color(argb) else fallback
}
