package com.winhub.hernandez.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.winhub.hernandez.R
import com.winhub.hernandez.core.GameSaveBackup
import com.winhub.hernandez.ui.screens.OutlinedAlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Asks which emulator account a restored container should run under, when the backup carries one
 * and the prefix already has a different one.
 *
 * A restore applies the backup's account id on its own whenever that is unambiguous — the prefix has
 * no id (rebuilt, the case that strands saves) or the ids already match. This dialog exists only for
 * the genuinely ambiguous case, because the id is prefix-GLOBAL: taking the backup's id makes these
 * saves readable and simultaneously orphans the saves of every other cracked game in the same
 * container. Nobody can pick that for the user.
 *
 * Shown one conflict at a time (a prefix can hold more than one emulator), then [onFinished] reports
 * how many identities were applied vs kept so the caller can fold it into its result message.
 */
@Composable
fun EmuAccountConflictDialog(
    conflicts: List<GameSaveBackup.EmuIdConflict>,
    onFinished: (applied: Int, kept: Int) -> Unit,
) {
    if (conflicts.isEmpty()) return
    val scope = rememberCoroutineScope()
    var index by remember(conflicts) { mutableIntStateOf(0) }
    var applied by remember(conflicts) { mutableIntStateOf(0) }
    var kept by remember(conflicts) { mutableIntStateOf(0) }
    val conflict = conflicts.getOrNull(index) ?: return

    fun advance() {
        if (index + 1 >= conflicts.size) onFinished(applied, kept) else index += 1
    }

    OutlinedAlertDialog(
        onDismissRequest = {
            // Dismissing must not strand the staged copy, and "do nothing" is the safe reading of a
            // stray tap: keep what the container already runs on.
            scope.launch {
                withContext(Dispatchers.IO) { GameSaveBackup.discardEmuIdentity(conflict) }
                kept += 1
                advance()
            }
        },
        title = { Text(stringResource(R.string.emu_id_conflict_title, conflict.label)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.emu_id_conflict_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                AccountLine(R.string.emu_id_conflict_from_backup, conflict.backupId, conflict.backupSaveFolder)
                Spacer(Modifier.height(4.dp))
                AccountLine(R.string.emu_id_conflict_in_container, conflict.currentId, conflict.currentSaveFolder)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.emu_id_conflict_warning),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { GameSaveBackup.applyEmuIdentity(conflict) }
                    if (ok) applied += 1 else kept += 1
                    advance()
                }
            }) { Text(stringResource(R.string.emu_id_conflict_apply)) }
        },
        dismissButton = {
            TextButton(onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) { GameSaveBackup.discardEmuIdentity(conflict) }
                    kept += 1
                    advance()
                }
            }) { Text(stringResource(R.string.emu_id_conflict_keep)) }
        },
    )
}

/** "In this backup: 76561198404161158 (save folder 443895430)" — the folder is the part a user can match. */
@Composable
private fun AccountLine(labelRes: Int, accountId: String, saveFolder: String?) {
    val label = stringResource(labelRes)
    val text = if (saveFolder != null) {
        stringResource(R.string.emu_id_conflict_account_with_folder, label, accountId, saveFolder)
    } else {
        stringResource(R.string.emu_id_conflict_account, label, accountId)
    }
    Text(text = text, style = MaterialTheme.typography.bodyMedium)
}
