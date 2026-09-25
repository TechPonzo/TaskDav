package app.taskdav.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHubScreen(
    onSyncing: () -> Unit,
    onAppearance: () -> Unit,
    onExport: () -> Unit,
    onPrivacy: () -> Unit,
    onCredits: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item {
                SettingsRow(
                    title = "Syncing",
                    subtitle = "Local-only or CalDAV sync",
                    icon = { Icon(Icons.Default.Sync, contentDescription = null) },
                    onClick = onSyncing,
                )
            }
            item {
                SettingsRow(
                    title = "Appearance",
                    subtitle = "App color theme",
                    icon = { Icon(Icons.Default.Palette, contentDescription = null) },
                    onClick = onAppearance,
                )
            }
            item {
                SettingsRow(
                    title = "Export",
                    subtitle = "Share calendars as ICS files",
                    icon = { Icon(Icons.Default.Share, contentDescription = null) },
                    onClick = onExport,
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item {
                SettingsRow(
                    title = "Privacy & license",
                    subtitle = "How TaskDav handles your data",
                    icon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    onClick = onPrivacy,
                )
            }
            item {
                SettingsRow(
                    title = "Credits",
                    subtitle = "Open-source acknowledgements",
                    icon = { Icon(Icons.Default.Info, contentDescription = null) },
                    onClick = onCredits,
                )
            }
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        leadingContent = icon,
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}
