package com.assem.scrollbreak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.assem.scrollbreak.AppSettings
import com.assem.scrollbreak.EmergencyStore
import kotlinx.coroutines.delay

private enum class EmergencyDialog { SET, USE, CHANGE, REMOVE }

/**
 * The emergency code: a friend or family member sets a six-digit code, and only whoever knows it
 * can lift the lock timer and every app limit and block. Lives in Settings, which stays reachable
 * while locked.
 */
@Composable
fun EmergencyCard(modifier: Modifier = Modifier) {
    // Bumped after a change so "is a code set" is read again.
    var version by remember { mutableIntStateOf(0) }
    val isSet = remember(version) { EmergencyStore.isSet }
    val protectionMs by produceState(AppSettings.protectionRemainingMs()) {
        while (true) {
            value = AppSettings.protectionRemainingMs()
            delay(1000)
        }
    }
    var dialog by remember { mutableStateOf<EmergencyDialog?>(null) }

    fun changed() {
        version++
    }

    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Emergency code", style = MaterialTheme.typography.titleMedium)
            Text(
                if (isSet) {
                    "A six-digit code is set. Whoever knows it can remove the lock timer and every app limit and block from here."
                } else {
                    "Ask a friend or family member to set a six-digit code and keep it to themselves. If you ever truly need to remove every lock, limit and block, they can give it to you."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isSet) {
                Button(onClick = { dialog = EmergencyDialog.USE }, modifier = Modifier.fillMaxWidth()) {
                    Text("Enter the code")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { dialog = EmergencyDialog.CHANGE }) { Text("Change it") }
                    TextButton(onClick = { dialog = EmergencyDialog.REMOVE }) { Text("Remove it") }
                }
            } else {
                Button(
                    onClick = { dialog = EmergencyDialog.SET },
                    enabled = protectionMs <= 0,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Set a code") }
                if (protectionMs > 0) {
                    Text(
                        "A code can only be set while no lock timer or app limit is running.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    when (dialog) {
        EmergencyDialog.SET -> CodeDialog(
            title = "Set the emergency code",
            steps = listOf("Choose a six-digit code", "Type it again"),
            doneText = "Code set",
            onDismiss = { dialog = null },
        ) { c ->
            if (c[0] != c[1]) "The two codes did not match. Start again."
            else EmergencyStore.setFirst(c[0]).let { if (it.ok) changed().let { null } else it.message }
        }
        EmergencyDialog.USE -> CodeDialog(
            title = "Emergency code",
            steps = listOf("Enter the code to remove every lock, limit and block"),
            doneText = "All locks removed",
            onDismiss = { dialog = null },
        ) { c ->
            EmergencyStore.unlockEverything(c[0]).let { if (it.ok) null else it.message }
        }
        EmergencyDialog.CHANGE -> CodeDialog(
            title = "Change the code",
            steps = listOf("Enter the current code", "Choose the new code", "Type the new code again"),
            doneText = "Code changed",
            onDismiss = { dialog = null },
        ) { c ->
            if (c[1] != c[2]) "The two new codes did not match. Start again."
            else EmergencyStore.change(c[0], c[1]).let { if (it.ok) changed().let { null } else it.message }
        }
        EmergencyDialog.REMOVE -> CodeDialog(
            title = "Remove the code",
            steps = listOf("Enter the current code. Without a code there is no emergency way out of a lock."),
            doneText = "Code removed",
            onDismiss = { dialog = null },
        ) { c ->
            EmergencyStore.remove(c[0]).let { if (it.ok) changed().let { null } else it.message }
        }
        null -> Unit
    }
}
