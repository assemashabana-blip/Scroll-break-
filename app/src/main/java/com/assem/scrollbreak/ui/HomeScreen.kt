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
import com.assem.scrollbreak.core.BlockStats
import com.assem.scrollbreak.core.LatencyStats

/** Everything Home needs; grouped so adding a card does not mean another ten parameters. */
class HomeState(
    val serviceEnabled: Boolean,
    val setupRemaining: Int,
    val blockingEnabled: Boolean,
    val latency: LatencyStats.Summary,
    val stats: BlockStats.Summary,
    /** Short-video rules by app name, with whether each is switched on. */
    val apps: List<Pair<String, Boolean>>,
)

class HomeActions(
    val openAccessibilitySettings: () -> Unit,
    val openSetup: () -> Unit,
    val setBlocking: (Boolean) -> Unit,
    val setAppEnabled: (String, Boolean) -> Unit,
    val navigate: (String) -> Unit,
)

@Composable
fun HomeScreen(state: HomeState, actions: HomeActions, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BrandHeader()

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (state.serviceEnabled) "Service: ON" else "Service: OFF", style = MaterialTheme.typography.titleMedium)
                if (!state.serviceEnabled) Text("Enable ScrollBreak under Accessibility > Installed apps.")
                Button(onClick = actions.openAccessibilitySettings) { Text("Open Accessibility settings") }
            }
        }

        if (state.setupRemaining > 0) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Setup: ${state.setupRemaining} step(s) left", style = MaterialTheme.typography.titleMedium)
                    Text("Finish these so Android does not stop ScrollBreak in the background.", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = actions.openSetup) { Text("Open setup") }
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
                        if (state.blockingEnabled) "Short-form players are blocked."
                        else "Dry run: matches are only logged as WOULD_BLOCK.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(checked = state.blockingEnabled, onCheckedChange = actions.setBlocking)
            }
        }

        if (state.apps.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Apps", style = MaterialTheme.typography.titleMedium)
                    state.apps.forEach { (app, on) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(app.replaceFirstChar { it.uppercase() }, Modifier.weight(1f))
                            Switch(checked = on, onCheckedChange = { actions.setAppEnabled(app, it) })
                        }
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Blocked today: ${state.stats.today}", style = MaterialTheme.typography.titleMedium)
                Text("Last 7 days: ${state.stats.last7Days}", style = MaterialTheme.typography.bodyMedium)
                state.stats.todayByApp.forEach { (app, n) ->
                    Text("${app.replaceFirstChar { it.uppercase() }}: $n", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Reaction time", style = MaterialTheme.typography.titleMedium)
                val l = state.latency
                if (l.count == 0) {
                    Text("No blocks yet.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        "Last ${l.lastMs} ms · median ${l.medianMs} ms · 95% under ${l.p95Ms} ms · slowest ${l.maxMs} ms",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "From the moment the screen changed to the Back action, over the last ${l.count} blocks. Target: under 300 ms.",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        listOf(
            "settings" to "Settings",
            "rules" to "Rules (import / export)",
            "health" to "Detection health",
            "log" to "Event log",
            "dumps" to "Screen dumps",
            "setup" to "Setup checklist",
        ).forEach { (route, label) ->
            OutlinedButton(onClick = { actions.navigate(route) }, modifier = Modifier.fillMaxWidth()) { Text(label) }
        }
    }
}
