package app.taskdav.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.taskdav.R
import app.taskdav.ui.setup.SetupViewModel
import app.taskdav.ui.theme.collectionColorOrDefault

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsSettingsScreen(
    viewModel: SetupViewModel,
    onBack: () -> Unit,
    onDone: (() -> Unit)? = null,
) {
    val collections by viewModel.collections.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.collections_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (onDone != null && collections.isNotEmpty()) {
                Button(
                    onClick = onDone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(stringResource(R.string.collections_done))
                }
            }
        },
    ) { padding ->
        if (collections.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.collections_empty))
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.setup_collections_hint),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            items(collections, key = { it.id }) { col ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
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
                        Text(
                            collectionCapabilityLabels(
                                supportsVtodo = col.supportsVtodo,
                                supportsVevent = col.supportsVevent,
                                supportsVjournal = col.supportsVjournal,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = col.enabled,
                        onCheckedChange = { viewModel.toggleCollection(col.id, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun collectionCapabilityLabels(
    supportsVtodo: Boolean,
    supportsVevent: Boolean,
    supportsVjournal: Boolean,
): String {
    val caps = buildList {
        if (supportsVtodo) add(stringResource(R.string.collection_cap_tasks))
        if (supportsVevent) add(stringResource(R.string.collection_cap_events))
        if (supportsVjournal) add(stringResource(R.string.collection_cap_notes))
    }
    return caps.joinToString(" · ")
}
