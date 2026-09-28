package app.taskdav.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.taskdav.R
import app.taskdav.ui.theme.collectionColorOrDefault

@Composable
fun SetupScreen(
    viewModel: SetupViewModel,
    onDone: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.setup_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
        }
        item {
            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = viewModel::updateUrl,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.syncing_server_url)) },
                placeholder = { Text(stringResource(R.string.syncing_server_url_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
        }
        item {
            OutlinedTextField(
                value = state.username,
                onValueChange = viewModel::updateUser,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.syncing_username)) },
                singleLine = true,
            )
        }
        item {
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::updatePassword,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.syncing_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
        }
        item {
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
                        Text(stringResource(R.string.working))
                    }
                } else {
                    Text(stringResource(R.string.syncing_save_discover))
                }
            }
        }
        state.error?.let { err ->
            item {
                Text(err, color = MaterialTheme.colorScheme.error)
            }
        }
        state.message?.let { msg ->
            item {
                Text(msg, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (collections.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.syncing_collections), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.setup_collections_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            items(collections, key = { it.id }) { col ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(collectionColorOrDefault(col.colorArgb), CircleShape),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(col.displayName, style = MaterialTheme.typography.bodyLarge)
                        val caps = buildList {
                            if (col.supportsVtodo) add(stringResource(R.string.collection_cap_tasks))
                            if (col.supportsVevent) add(stringResource(R.string.collection_cap_events))
                            if (col.supportsVjournal) add(stringResource(R.string.collection_cap_journal))
                        }.joinToString(" · ")
                        Text(caps, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = col.enabled,
                        onCheckedChange = { viewModel.toggleCollection(col.id, it) },
                    )
                }
            }
            item {
                TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.setup_continue))
                }
            }
        }
    }
}
