package app.taskdav.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.taskdav.TaskDavApp
import app.taskdav.data.AppearanceStore
import app.taskdav.data.DateOrderPreference
import app.taskdav.ui.theme.schemesFromSeed
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as TaskDavApp
    val seedArgb by app.appearanceStore.seedColorArgb.collectAsStateWithLifecycle(
        initialValue = AppearanceStore.DEFAULT_SEED_COLOR,
    )
    val dateOrderId by app.appearanceStore.dateOrder.collectAsStateWithLifecycle(
        initialValue = AppearanceStore.DEFAULT_DATE_ORDER,
    )
    val scope = rememberCoroutineScope()

    var red by remember { mutableFloatStateOf(((seedArgb shr 16) and 0xFF).toFloat()) }
    var green by remember { mutableFloatStateOf(((seedArgb shr 8) and 0xFF).toFloat()) }
    var blue by remember { mutableFloatStateOf((seedArgb and 0xFF).toFloat()) }

    LaunchedEffect(seedArgb) {
        red = ((seedArgb shr 16) and 0xFF).toFloat()
        green = ((seedArgb shr 8) and 0xFF).toFloat()
        blue = (seedArgb and 0xFF).toFloat()
    }

    fun currentArgb(): Int {
        val r = red.toInt().coerceIn(0, 255)
        val g = green.toInt().coerceIn(0, 255)
        val b = blue.toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun persistSeed() {
        scope.launch { app.appearanceStore.setSeedColorArgb(currentArgb()) }
    }

    val previewSeed = Color(currentArgb())
    val (previewLight, previewDark) = remember(red, green, blue) {
        schemesFromSeed(previewSeed)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Pick a brand color. The app builds lighter and darker tones from it.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(20.dp),
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(previewSeed)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                    CircleShape,
                                ),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Brand color", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "#%02X%02X%02X".format(
                                    red.toInt().coerceIn(0, 255),
                                    green.toInt().coerceIn(0, 255),
                                    blue.toInt().coerceIn(0, 255),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    ToneStrip(
                        label = "Light tones",
                        colors = listOf(
                            previewLight.primary,
                            previewLight.primaryContainer,
                            previewLight.secondaryContainer,
                            previewLight.surface,
                        ),
                    )
                    ToneStrip(
                        label = "Dark tones",
                        colors = listOf(
                            previewDark.primary,
                            previewDark.primaryContainer,
                            previewDark.secondaryContainer,
                            previewDark.surface,
                        ),
                    )

                    RgbSlider(
                        label = "R",
                        value = red,
                        trackColor = Color(0xFFE53935),
                        onValueChange = { red = it },
                        onValueChangeFinished = { persistSeed() },
                    )
                    RgbSlider(
                        label = "G",
                        value = green,
                        trackColor = Color(0xFF43A047),
                        onValueChange = { green = it },
                        onValueChangeFinished = { persistSeed() },
                    )
                    RgbSlider(
                        label = "B",
                        value = blue,
                        trackColor = Color(0xFF1E88E5),
                        onValueChange = { blue = it },
                        onValueChangeFinished = { persistSeed() },
                    )
                }
            }

            item {
                Text(
                    "Date format",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    "Your phone language is often English (US), which uses month/day. Pick day/month for 25/9/26.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            items(DateOrderPreference.entries.toList(), key = { it.id }) { option ->
                val selected = option.id == dateOrderId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            },
                            shape = RoundedCornerShape(12.dp),
                        )
                        .clickable {
                            scope.launch { app.appearanceStore.setDateOrder(option.id) }
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        option.label,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToneStrip(
    label: String,
    colors: List<Color>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(10.dp)),
        ) {
            colors.forEach { color ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(color),
                )
            }
        }
    }
}

@Composable
private fun RgbSlider(
    label: String,
    value: Float,
    trackColor: Color,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(16.dp),
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = trackColor,
                activeTrackColor = trackColor,
                inactiveTrackColor = trackColor.copy(alpha = 0.25f),
            ),
        )
        Text(
            value.toInt().coerceIn(0, 255).toString(),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(32.dp),
        )
    }
}
