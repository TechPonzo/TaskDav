package app.taskdav.ui.settings

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.taskdav.ui.setup.SetupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(
    viewModel: SetupViewModel,
    onBack: () -> Unit,
    onContinueAfterDiscover: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account") },
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Connect to your CalDAV server (for example the same one DAVx⁵ uses).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
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
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            if (state.discovered && onContinueAfterDiscover != null) {
                TextButton(onClick = onContinueAfterDiscover, modifier = Modifier.fillMaxWidth()) {
                    Text("Continue")
                }
            }
        }
    }
}
