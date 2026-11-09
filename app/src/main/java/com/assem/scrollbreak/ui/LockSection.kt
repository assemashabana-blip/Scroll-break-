package com.assem.scrollbreak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.assem.scrollbreak.AppSettings
import com.assem.scrollbreak.core.LockTimer
import com.assem.scrollbreak.ui.theme.SbAccent
import kotlinx.coroutines.delay

/** Milliseconds left on the lock timer (0 when none), re-read every second while on screen. */
@Composable
fun rememberLockRemaining(): State<Long> = produceState(AppSettings.lockRemainingMs()) {
    while (true) {
        value = AppSettings.lockRemainingMs()
        delay(1000)
    }
}

/**
 * The lock timer inside the blocking card. Idle: a dial for the length and a button. Running:
 * the countdown, with nothing to press, because a lock that can be cancelled is not a lock.
 */
@Composable
fun LockSection(remainingMs: Long, onStart: (minutes: Int) -> Unit, modifier: Modifier = Modifier) {
    val buzz = rememberBuzz()
    var minutes by rememberSaveable { mutableIntStateOf(30) }
    var confirming by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (remainingMs > 0) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("LOCKED", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    LockTimer.format(remainingMs),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = SbAccent,
                )
                Text(
                    "Blocking stays on until the timer ends. It cannot be turned off or cancelled, and ScrollBreak cannot be switched off or uninstalled meanwhile.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Text("Lock blocking on", style = MaterialTheme.typography.titleMedium)
            Text(
                "Drag the dial, or tap the number to type any time. Until the timer ends you cannot switch blocking off.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DurationPicker(minutes = minutes, onMinutes = { minutes = it }, maxMinutes = AppSettings.MAX_LOCK_MINUTES)
            Button(onClick = { confirming = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Lock for ${LockTimer.describe(minutes)}")
            }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Lock for ${LockTimer.describe(minutes)}?") },
            text = {
                Text("Blocking turns on and you will not be able to turn it off, cancel the timer or change the settings until it ends.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    buzz(Buzz.HEAVY)
                    onStart(minutes)
                }) { Text("Lock") }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Not now") } },
        )
    }
}
