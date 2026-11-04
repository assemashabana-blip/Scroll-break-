package com.assem.scrollbreak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

@Composable
fun HomeScreen(
    serviceEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenEventLog: () -> Unit,
    onOpenDumps: () -> Unit,
    blockingEnabled: Boolean,
    onBlockingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
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
        OutlinedButton(onClick = onOpenEventLog, modifier = Modifier.fillMaxWidth()) {
            Text("Event log")
        }
        OutlinedButton(onClick = onOpenDumps, modifier = Modifier.fillMaxWidth()) {
            Text("Screen dumps")
        }
    }
}
