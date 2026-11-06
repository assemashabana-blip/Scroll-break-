package com.assem.scrollbreak.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.assem.scrollbreak.R
import com.assem.scrollbreak.core.BlockStats
import com.assem.scrollbreak.core.LatencyStats
import com.assem.scrollbreak.ui.theme.SbAccent

/** Everything Home needs; grouped so adding a card does not mean another ten parameters. */
class HomeState(
    val serviceEnabled: Boolean,
    val setupRemaining: Int,
    val blockingEnabled: Boolean,
    val latency: LatencyStats.Summary,
    val stats: BlockStats.Summary,
    /** Short-video rules by app name, with whether each is switched on. */
    val apps: List<Pair<String, Boolean>>,
    /** Android packages per app name, used to show each app's own icon. */
    val appPackages: Map<String, List<String>>,
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandHeader(Modifier.weight(1f))
            IconButton(onClick = { actions.navigate("settings") }) {
                Icon(
                    painterResource(R.drawable.ic_gear),
                    contentDescription = "Settings",
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // Status: no words. A white circle means blocking is off, the accent colour means it is on.
        val active = state.serviceEnabled && state.blockingEnabled
        val orb by animateColorAsState(if (active) SbAccent else Color.White, label = "orb")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).background(orb)
                            .semantics { contentDescription = if (active) "Blocking is on" else "Blocking is off" },
                    )
                    Spacer(Modifier.weight(1f))
                    Switch(checked = state.blockingEnabled, onCheckedChange = actions.setBlocking)
                }
                if (!state.serviceEnabled) {
                    Button(onClick = actions.openAccessibilitySettings, modifier = Modifier.fillMaxWidth()) {
                        Text("Turn on in Accessibility settings")
                    }
                }
            }
        }

        if (state.setupRemaining > 0) {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${state.setupRemaining} setup step(s) left", style = MaterialTheme.typography.titleMedium)
                        Text("Keeps Android from stopping ScrollBreak.", style = MaterialTheme.typography.bodySmall, color = muted)
                    }
                    Button(onClick = actions.openSetup) { Text("Finish") }
                }
            }
        }

        // Numbers: blocked today, last 7 days, typical reaction time.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Today", state.stats.today.toString(), "blocked", Modifier.weight(1f))
            StatTile("7 days", state.stats.last7Days.toString(), "blocked", Modifier.weight(1f))
            val l = state.latency
            StatTile("Reaction", if (l.count == 0) "–" else l.medianMs.toString(), if (l.count == 0) "no blocks yet" else "ms median", Modifier.weight(1f))
        }
        if (state.latency.count > 0) {
            val l = state.latency
            Text(
                "Last ${l.lastMs} ms · 95% under ${l.p95Ms} ms · slowest ${l.maxMs} ms over ${l.count} blocks. Target: under 300 ms.",
                style = MaterialTheme.typography.labelSmall, color = muted, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        if (state.apps.isNotEmpty()) {
            SectionLabel("Apps")
            state.apps.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (app, on) ->
                        AppTile(
                            name = appDisplayName(app),
                            packages = state.appPackages[app].orEmpty(),
                            on = on,
                            onToggle = { actions.setAppEnabled(app, it) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Keep a short last row aligned with the columns above it.
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
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
