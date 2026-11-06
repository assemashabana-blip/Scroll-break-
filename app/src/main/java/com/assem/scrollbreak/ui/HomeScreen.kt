package com.assem.scrollbreak.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.assem.scrollbreak.ui.theme.SbAccent
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
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BrandHeader(Modifier.padding(bottom = 4.dp))

        // Status hero: the one thing that matters at a glance.
        val protectedNow = state.serviceEnabled && state.blockingEnabled
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(14.dp).clip(CircleShape)
                            .background(if (protectedNow) MaterialTheme.colorScheme.onSurface else SbAccent),
                    )
                    Text(
                        when {
                            !state.serviceEnabled -> "Not running"
                            !state.blockingEnabled -> "Dry run"
                            else -> "Blocking is on"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.serviceEnabled) Switch(checked = state.blockingEnabled, onCheckedChange = actions.setBlocking)
                }
                Text(
                    when {
                        !state.serviceEnabled -> "Turn ScrollBreak on under Accessibility > Installed apps, or Android will not let it watch for feeds."
                        !state.blockingEnabled -> "Matches are only logged as WOULD_BLOCK. Nothing is blocked."
                        else -> "Short-form feeds are blocked. One video from a friend still plays."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                )
                if (!state.serviceEnabled) {
                    Button(onClick = actions.openAccessibilitySettings, modifier = Modifier.fillMaxWidth()) {
                        Text("Open Accessibility settings")
                    }
                }
            }
        }

        if (state.setupRemaining > 0) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${state.setupRemaining} setup step(s) left", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Finish these so Android does not stop ScrollBreak in the background.",
                        style = MaterialTheme.typography.bodyMedium, color = muted,
                    )
                    Button(onClick = actions.openSetup) { Text("Open setup") }
                }
            }
        }

        // Numbers: blocked today, last 7 days, typical reaction time.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Today", state.stats.today.toString(), "blocked", Modifier.weight(1f))
            StatTile("7 days", state.stats.last7Days.toString(), "blocked", Modifier.weight(1f))
            val l = state.latency
            StatTile("Reaction", if (l.count == 0) "\u2013" else l.medianMs.toString(), if (l.count == 0) "no blocks yet" else "ms median", Modifier.weight(1f))
        }
        if (state.latency.count > 0) {
            val l = state.latency
            Text(
                "Last ${l.lastMs} ms \u00B7 95% under ${l.p95Ms} ms \u00B7 slowest ${l.maxMs} ms over ${l.count} blocks. Target: under 300 ms.",
                style = MaterialTheme.typography.labelSmall, color = muted, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        if (state.stats.todayByApp.isNotEmpty()) {
            Text(
                state.stats.todayByApp.entries.joinToString(" \u00B7 ") { "${appDisplayName(it.key)} ${it.value}" },
                style = MaterialTheme.typography.labelSmall, color = muted, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        if (state.apps.isNotEmpty()) {
            SectionLabel("Apps")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    state.apps.forEach { (app, on) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(appDisplayName(app), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Switch(checked = on, onCheckedChange = { actions.setAppEnabled(app, it) })
                        }
                    }
                }
            }
        }

        SectionLabel("More")
        Card(Modifier.fillMaxWidth()) {
            Column {
                NavRow("Settings", "Friend window, speed, uninstall protection") { actions.navigate("settings") }
                NavRow("Rules", "Import, export and reset app rules") { actions.navigate("rules") }
                NavRow("Detection health", "When each signal last matched") { actions.navigate("health") }
                NavRow("Event log", "What ScrollBreak decided, and when") { actions.navigate("log") }
                NavRow("Screen dumps", "Capture screens to write new rules") { actions.navigate("dumps") }
                NavRow("Setup checklist", "Keep Android from stopping the service") { actions.navigate("setup") }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
