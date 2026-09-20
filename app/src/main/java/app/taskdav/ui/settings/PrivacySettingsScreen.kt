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
fun PrivacySettingsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy & license") },
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
                TaskDav stores your account credentials and synced data only on this device.
                
                Credentials are kept in encrypted preferences on the phone. Tasks, notes, and calendar links are saved in a local database and sent only to the CalDAV server you configure—nothing is uploaded to TaskDav developers or advertising networks.
                
                The app does not include analytics, crash-reporting SDKs, ads, or Google Play Services.
                
                Your calendar and task data remains under your control on your own server (or any CalDAV host you choose). You can delete the app or clear its data at any time to remove the local copy.
                
                TaskDav is free and open-source software licensed under the GNU General Public License version 3 (GPLv3). You are free to use, study, share, and improve it under the terms of that license.
                
                Third-party libraries used by TaskDav keep their own licenses; see Credits.
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
