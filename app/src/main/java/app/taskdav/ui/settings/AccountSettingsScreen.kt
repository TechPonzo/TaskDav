package app.taskdav.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.taskdav.TaskDavApp
import app.taskdav.data.SyncBackend
import app.taskdav.ui.setup.SetupViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(
    viewModel: SetupViewModel,
    onBack: () -> Unit,
    onOpenCollections: (() -> Unit)? = null,
    onLocalReady: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isLocal = state.syncBackend == SyncBackend.LOCAL
    val app = LocalContext.current.applicationContext as TaskDavApp
    val mirrorEnabled by app.phoneCalendarMirror.collectAsStateWithLifecycle()
    val importEnabled by app.phoneCalendarImport.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var publishMessage by remember { mutableStateOf<String?>(null) }
    var statusTick by remember { mutableStateOf(0) }
    val publishStatus = remember(mirrorEnabled, statusTick) {
        if (mirrorEnabled) {
            app.repository.phoneCalendarPublishStatus()
        } else {
            null
        }
    }

    fun runPublish() {
        scope.launch {
            val result = app.repository.publishToPhoneCalendar()
            publishMessage = result.message
            statusTick += 1
        }
    }

    val mirrorPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val ok = grants.values.all { it }
        if (ok) {
            app.repository.setPhoneCalendarMirrorEnabled(true)
            app.notifyPhoneCalendarMirrorChanged()
            runPublish()
        } else {
            app.repository.setPhoneCalendarMirrorEnabled(false)
            app.notifyPhoneCalendarMirrorChanged()
            publishMessage = "Calendar permission denied."
        }
    }

    val importPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val ok = grants[Manifest.permission.READ_CALENDAR] == true
        if (ok) {
            app.repository.setPhoneCalendarImportEnabled(true)
            app.notifyPhoneCalendarImportChanged()
            scope.launch { app.repository.importPhoneCalendarEvents() }
        } else {
            app.repository.setPhoneCalendarImportEnabled(false)
            app.notifyPhoneCalendarImportChanged()
        }
    }

    fun setMirror(enabled: Boolean) {
        if (enabled) {
            if (app.repository.hasPhoneCalendarPermission() &&
                app.systemCalendarMirror.hasPermission()
            ) {
                app.repository.setPhoneCalendarMirrorEnabled(true)
                app.notifyPhoneCalendarMirrorChanged()
                runPublish()
            } else {
                mirrorPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_CALENDAR,
                        Manifest.permission.WRITE_CALENDAR,
                    ),
                )
            }
        } else {
            app.repository.setPhoneCalendarMirrorEnabled(false)
            app.notifyPhoneCalendarMirrorChanged()
            scope.launch {
                app.repository.clearPhoneCalendarMirror()
                publishMessage = null
                statusTick += 1
            }
        }
    }

    LaunchedEffect(mirrorEnabled) {
        if (mirrorEnabled) statusTick += 1
    }

    fun setImport(enabled: Boolean) {
        if (enabled) {
            if (app.repository.hasPhoneCalendarReadPermission()) {
                app.repository.setPhoneCalendarImportEnabled(true)
                app.notifyPhoneCalendarImportChanged()
                scope.launch { app.repository.importPhoneCalendarEvents() }
            } else {
                importPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_CALENDAR,
                        Manifest.permission.WRITE_CALENDAR,
                    ),
                )
            }
        } else {
            app.repository.setPhoneCalendarImportEnabled(false)
            app.notifyPhoneCalendarImportChanged()
            scope.launch { app.repository.clearPhoneCalendarImport() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Syncing") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Where should calendars, tasks, and notes live?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
            Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
                SyncBackendRow(
                    title = "This device only",
                    subtitle = "Stay local — nothing uploaded",
                    selected = isLocal,
                    onClick = { viewModel.selectSyncBackend(SyncBackend.LOCAL) },
                )
                SyncBackendRow(
                    title = "CalDAV sync",
                    subtitle = "Sync with your CalDAV server",
                    selected = !isLocal,
                    onClick = { viewModel.selectSyncBackend(SyncBackend.CALDAV) },
                )
            }

            if (isLocal) {
                Text(
                    "Everything stays on this phone. Nothing is uploaded until you switch to CalDAV.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Button(
                    onClick = {
                        viewModel.useLocalStorage {
                            onLocalReady?.invoke() ?: onBack()
                        }
                    },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.busy) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Working…")
                        }
                    } else {
                        Text("Use local storage")
                    }
                }
            } else {
                Text(
                    "Connect to your CalDAV server (for example the same one DAVx⁵ uses).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                OutlinedTextField(
                    value = state.baseUrl,
                    onValueChange = viewModel::updateUrl,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Server URL") },
                    placeholder = { Text("https://caldav.example/user/") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(
                    value = state.username,
                    onValueChange = viewModel::updateUser,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Username") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.password,
                    onValueChange = viewModel::updatePassword,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Button(
                    onClick = viewModel::saveAndDiscover,
                    enabled = !state.busy && state.baseUrl.isNotBlank() && state.username.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.busy) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Working…")
                        }
                    } else {
                        Text("Save & discover collections")
                    }
                }
                state.error?.let { err ->
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                state.message?.let { msg ->
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (state.discovered && onOpenCollections != null) {
                    Button(
                        onClick = onOpenCollections,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Collections to sync")
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text("Phone calendar", style = MaterialTheme.typography.titleSmall)
            Text(
                "Works without DAVx⁵. Local-only users can still share events with the Android Calendar app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Import into TaskDav")
                    Text(
                        "Pull events from Google Calendar and other calendars on this phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    )
                }
                Switch(
                    checked = importEnabled,
                    onCheckedChange = { setImport(it) },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Show TaskDav in Android Calendar")
                    Text(
                        "Creates a TaskDav account (like DAVx⁵) and publishes your events. On Samsung: Calendar → ☰ → manage calendars, and also check Settings → Accounts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    )
                }
                Switch(
                    checked = mirrorEnabled,
                    onCheckedChange = { setMirror(it) },
                )
            }
            if (mirrorEnabled && publishStatus != null) {
                Text(
                    publishStatus.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (publishStatus.calendarId != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                OutlinedButton(
                    onClick = { runPublish() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Publish events now")
                }
            }
            publishMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SyncBackendRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            ),
        color = MaterialTheme.colorScheme.background,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RadioButton(
                selected = selected,
                onClick = null,
            )
        }
    }
}
