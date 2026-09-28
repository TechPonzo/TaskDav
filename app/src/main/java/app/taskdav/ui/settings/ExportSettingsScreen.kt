package app.taskdav.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.taskdav.R
import app.taskdav.TaskDavApp
import app.taskdav.caldav.CollectionExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as TaskDavApp
    val collections by app.repository.observeCollections().collectAsStateWithLifecycle(emptyList())
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val allSelected = selected.size == collections.size && collections.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_export)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            selected = if (allSelected) {
                                emptySet()
                            } else {
                                collections.map { it.id }.toSet()
                            }
                        },
                    ) {
                        Text(
                            stringResource(
                                if (allSelected) R.string.export_clear else R.string.export_select_all,
                            ),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.export_intro),
                style = MaterialTheme.typography.bodySmall,
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(collections, key = { it.id }) { col ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = col.id in selected,
                            onCheckedChange = { checked ->
                                selected = if (checked) selected + col.id else selected - col.id
                            },
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
                    }
                }
            }
            Button(
                onClick = {
                    if (selected.isEmpty()) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.export_toast_none),
                            Toast.LENGTH_SHORT,
                        ).show()
                        return@Button
                    }
                    scope.launch {
                        busy = true
                        try {
                            val toExport = collections.filter { it.id in selected }
                            val exporter = CollectionExporter(app.database)
                            val files = withContext(Dispatchers.IO) {
                                exporter.exportToCache(context, toExport)
                            }
                            exporter.shareFiles(context, files)
                            Toast.makeText(
                                context,
                                context.getString(R.string.export_ready, files.size),
                                Toast.LENGTH_SHORT,
                            ).show()
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                e.message ?: context.getString(R.string.export_failed),
                                Toast.LENGTH_LONG,
                            ).show()
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy && selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(if (busy) R.string.export_busy else R.string.export_action),
                )
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
