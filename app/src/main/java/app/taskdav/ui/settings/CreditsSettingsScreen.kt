package app.taskdav.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditsSettingsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Credits") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Text(
            text = """
                TaskDav
                Copyright © TaskDav contributors
                Licensed under the GNU General Public License v3.0 (GPLv3)
                
                Built with:
                
                • dav4jvm (bitfireAT) — WebDAV / CalDAV client library
                • ical4j — iCalendar parsing and generation
                • AndroidX, Jetpack Compose, Room, WorkManager — Android application frameworks
                • OkHttp — HTTP client
                
                TaskDav is designed to work alongside DAVx⁵ and other CalDAV clients on the same server. Those projects are separate and retain their own licenses and trademarks.
                
                Thank you to the FOSS CalDAV community.
            """.trimIndent(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        )
    }
}
