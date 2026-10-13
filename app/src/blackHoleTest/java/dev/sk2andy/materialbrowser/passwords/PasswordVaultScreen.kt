package dev.sk2andy.materialbrowser.passwords

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.passwords.PasswordVaultRules
import dev.sk2andy.materialbrowser.data.passwords.VaultField
import dev.sk2andy.materialbrowser.data.passwords.VaultCredential
import dev.sk2andy.materialbrowser.data.passwords.VaultSummary

@Composable
internal fun PasswordVaultScreen(
    unlocked: Boolean, busy: Boolean, entries: List<VaultSummary>, editor: VaultCredential?, message: Int?,
    onUnlock: () -> Unit, onLock: () -> Unit, onAdd: () -> Unit, onView: (String) -> Unit,
    onEdit: (VaultField, String) -> Unit, onCancelEdit: () -> Unit, onSave: () -> Unit,
    onDelete: (Set<String>) -> Unit, onDeleteAll: (String) -> Unit, onClose: () -> Unit,
) {
    // Nothing confidential uses rememberSaveable, clipboard, browser state or an autofill node.
    var query by remember(unlocked) { mutableStateOf("") }
    var selected by remember(unlocked) { mutableStateOf(emptySet<String>()) }
    var deletion by remember(unlocked) { mutableStateOf<Set<String>?>(null) }
    var deleteAll by remember(unlocked) { mutableStateOf(false) }
    var confirmation by remember(deleteAll) { mutableStateOf("") }
    Surface(modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
        Column(Modifier.safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.vault_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.vault_warning), style = MaterialTheme.typography.bodySmall)
            message?.let { Text(stringResource(it), Modifier.testTag("vault_status")) }
            if (!unlocked) {
                Text(stringResource(R.string.vault_locked), Modifier.testTag("vault_locked"))
                Button(onClick = onUnlock, enabled = !busy, modifier = Modifier.testTag("vault_unlock")) { Text(stringResource(R.string.vault_unlock)) }
            } else if (editor != null) {
                CredentialEditor(editor, busy, onEdit, onSave, onCancelEdit)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onAdd, enabled = !busy, modifier = Modifier.testTag("vault_add")) { Text(stringResource(R.string.vault_add)) }
                    TextButton(onClick = onLock, enabled = !busy, modifier = Modifier.testTag("vault_lock")) { Text(stringResource(R.string.vault_lock)) }
                }
                OutlinedTextField(query, { query = it.take(4_096) }, label = { Text(stringResource(R.string.vault_search)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth().testTag("vault_search"))
                if (selected.isNotEmpty()) {
                    TextButton(onClick = { deletion = selected }, enabled = !busy, modifier = Modifier.testTag("vault_delete_selected")) {
                        Text(stringResource(R.string.vault_delete_selected, selected.size))
                    }
                }
                if (entries.isNotEmpty()) {
                    TextButton(onClick = { deleteAll = true }, enabled = !busy, modifier = Modifier.testTag("vault_delete_all")) { Text(stringResource(R.string.vault_delete_all)) }
                } else Text(stringResource(R.string.vault_empty), Modifier.testTag("vault_empty"))
                LazyColumn(Modifier.weight(1f)) {
                    items(PasswordVaultRules.search(entries, query), key = { it.id }) { entry ->
                        Column {
                            Row {
                                Checkbox(entry.id in selected, { checked -> selected = if (checked) selected + entry.id else selected - entry.id },
                                    enabled = !busy, modifier = Modifier.testTag("vault_select_${entry.id}"))
                                Column(Modifier.weight(1f)) {
                                    Text(entry.site)
                                    Text(entry.username)
                                }
                            }
                            Row {
                                TextButton(onClick = { onView(entry.id) }, enabled = !busy, modifier = Modifier.testTag("vault_view_${entry.id}")) { Text(stringResource(R.string.vault_view)) }
                                TextButton(onClick = { deletion = setOf(entry.id) }, enabled = !busy, modifier = Modifier.testTag("vault_delete_${entry.id}")) { Text(stringResource(R.string.vault_delete)) }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
            TextButton(onClick = onClose) { Text(stringResource(R.string.vault_close)) }
        }
    }
    deletion?.let { ids ->
        AlertDialog(onDismissRequest = { deletion = null },
            modifier = Modifier.semantics { testTagsAsResourceId = true },
            title = { Text(stringResource(R.string.vault_delete)) },
            text = { Text(stringResource(R.string.vault_delete_confirmation, ids.size)) },
            confirmButton = { TextButton(onClick = { deletion = null; selected = emptySet(); onDelete(ids) }, modifier = Modifier.testTag("vault_confirm_delete")) { Text(stringResource(R.string.vault_confirm)) } },
            dismissButton = { TextButton(onClick = { deletion = null }) { Text(stringResource(R.string.vault_cancel)) } })
    }
    if (deleteAll) {
        AlertDialog(onDismissRequest = { deleteAll = false },
            modifier = Modifier.semantics { testTagsAsResourceId = true },
            title = { Text(stringResource(R.string.vault_delete_all)) },
            text = { Column {
                Text(stringResource(R.string.vault_delete_all_confirmation, entries.size))
                OutlinedTextField(confirmation, { confirmation = it.take(32) }, singleLine = true, modifier = Modifier.testTag("vault_confirmation"))
            } },
            confirmButton = { TextButton(onClick = { deleteAll = false; selected = emptySet(); onDeleteAll(confirmation) }, enabled = confirmation == "APAGAR TODAS", modifier = Modifier.testTag("vault_confirm_all")) { Text(stringResource(R.string.vault_confirm)) } },
            dismissButton = { TextButton(onClick = { deleteAll = false }) { Text(stringResource(R.string.vault_cancel)) } })
    }
}

@Composable
private fun CredentialEditor(entry: VaultCredential, busy: Boolean, onEdit: (VaultField, String) -> Unit, onSave: () -> Unit, onCancel: () -> Unit) {
    var visible by remember(entry.id) { mutableStateOf(false) }
    OutlinedTextField(entry.site, { onEdit(VaultField.Site, it) },
        label = { Text(stringResource(R.string.vault_site)) }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("vault_site"))
    OutlinedTextField(entry.username, { onEdit(VaultField.Username, it) },
        label = { Text(stringResource(R.string.vault_username)) }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("vault_username"))
    OutlinedTextField(entry.password, { onEdit(VaultField.Password, it) },
        label = { Text(stringResource(R.string.vault_password)) }, enabled = !busy, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().testTag("vault_password"))
    TextButton(onClick = { visible = !visible }, enabled = !busy, modifier = Modifier.testTag("vault_show")) { Text(stringResource(if (visible) R.string.vault_hide else R.string.vault_show)) }
    Row {
        Button(onClick = onSave, enabled = !busy, modifier = Modifier.testTag("vault_save")) { Text(stringResource(R.string.vault_save)) }
        TextButton(onClick = onCancel, enabled = !busy, modifier = Modifier.testTag("vault_cancel_edit")) { Text(stringResource(R.string.vault_cancel)) }
    }
}
