package com.assem.scrollbreak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.assem.scrollbreak.core.LatencyStats

@Composable
fun HomeScreen(
    serviceEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenEventLog: () -> Unit,
    onOpenDumps: () -> Unit,
    onOpenSetup: () -> Unit,
    setupRemaining: Int,
    blockingEnabled: Boolean,
    onBlockingChange: (Boolean) -> Unit,
    latency: LatencyStats.Summary,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("ScrollBreak", style = MaterialTheme.typography.headlineMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (serviceEnabled) "Service: ON" else "Service: OFF",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (!serviceEnabled) {
                    Text("Enable ScrollBreak under Accessibility > Installed apps.")
                }
                Button(onClick = onOpenAccessibilitySettings) {
                    Text("Open Accessibility settings")
                }
            }
        }
        if (setupRemaining > 0) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Setup: $setupRemaining step(s) left", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Finish these so Android does not stop ScrollBreak in the background.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onOpenSetup) { Text("Open setup") }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Blocking", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (blockingEnabled) "Short-form players are blocked."
                        else "Dry run: matches are only logged as WOULD_BLOCK.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(checked = blockingEnabled, onCheckedChange = onBlockingChange)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Reaction time", style = MaterialTheme.typography.titleMedium)
                if (latency.count == 0) {
                    Text("No blocks yet.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        "Last ${latency.lastMs} ms · median ${latency.medianMs} ms · " +
                            "95% under ${latency.p95Ms} ms · slowest ${latency.maxMs} ms",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "From the moment the screen changed to the Back action, over the last ${latency.count} blocks. Target: under 300 ms.",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        OutlinedButton(onClick = onOpenEventLog, modifier = Modifier.fillMaxWidth()) {
            Text("Event log")
        }
        OutlinedButton(onClick = onOpenDumps, modifier = Modifier.fillMaxWidth()) {
            Text("Screen dumps")
        }
        OutlinedButton(onClick = onOpenSetup, modifier = Modifier.fillMaxWidth()) {
            Text("Setup checklist")
        }
    }
}
