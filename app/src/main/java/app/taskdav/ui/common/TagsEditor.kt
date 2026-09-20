package app.taskdav.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * Tag chips plus an autocomplete field that suggests tags already used
 * on tasks/notes. Free-text tags can still be added with Add / keyboard Done.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TagsEditor(
    selected: List<String>,
    knownTags: List<String>,
    draft: String,
    onDraftChange: (String) -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val selectedLower = remember(selected) { selected.map { it.lowercase() }.toSet() }
    val suggestions = remember(draft, knownTags, selectedLower) {
        val query = draft.trim()
        knownTags
            .asSequence()
            .filter { it.lowercase() !in selectedLower }
            .filter {
                query.isEmpty() || it.contains(query, ignoreCase = true)
            }
            .sortedWith(
                compareBy<String> {
                    when {
                        query.isEmpty() -> 1
                        it.equals(query, ignoreCase = true) -> 0
                        it.startsWith(query, ignoreCase = true) -> 1
                        else -> 2
                    }
                }.thenBy { it.lowercase() },
            )
            .take(12)
            .toList()
    }
    val showMenu = menuExpanded && suggestions.isNotEmpty()

    fun commitDraft() {
        val next = draft.trim()
        if (next.isEmpty()) return
        val existing = knownTags.firstOrNull { it.equals(next, ignoreCase = true) } ?: next
        if (existing.lowercase() !in selectedLower) {
            onAdd(existing)
        }
        onDraftChange("")
        menuExpanded = false
    }

    Text("Tags", style = MaterialTheme.typography.titleSmall, modifier = modifier)
    if (selected.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            selected.forEach { tag ->
                InputChip(
                    selected = false,
                    onClick = { onRemove(tag) },
                    label = { Text(tag) },
                    trailingIcon = {
                        Icon(Icons.Default.Close, contentDescription = "Remove $tag")
                    },
                )
            }
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ExposedDropdownMenuBox(
            expanded = showMenu,
            onExpandedChange = { menuExpanded = it },
            modifier = Modifier.weight(1f),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = {
                    onDraftChange(it)
                    menuExpanded = true
                },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryEditable)
                    .fillMaxWidth(),
                label = { Text("Add tag") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commitDraft() }),
            )
            ExposedDropdownMenu(
                expanded = showMenu,
                onDismissRequest = { menuExpanded = false },
            ) {
                suggestions.forEach { suggestion ->
                    DropdownMenuItem(
                        text = { Text(suggestion) },
                        onClick = {
                            onAdd(suggestion)
                            onDraftChange("")
                            menuExpanded = false
                        },
                    )
                }
            }
        }
        OutlinedButton(onClick = { commitDraft() }) {
            Text("Add")
        }
    }
}
