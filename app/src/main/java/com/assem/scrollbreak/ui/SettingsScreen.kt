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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import com.assem.scrollbreak.AppSettings

/** Messaging apps that can count as "a friend sent this", with friendly names. */
private val friendLabels = linkedMapOf(
    "com.whatsapp" to "WhatsApp",
    "com.whatsapp.w4b" to "WhatsApp Business",
    "org.telegram.messenger" to "Telegram",
    "com.facebook.orca" to "Messenger",
    "org.thoughtcrime.securesms" to "Signal",
    "com.google.android.gm" to "Gmail",
)

@Composable
fun SettingsScreen(
    adminActive: Boolean,
    onEnableAdmin: () -> Unit,
    onOpenAdminSettings: () -> Unit,
    setupLeft: Int,
    onNavigate: (String) -> Unit,
    /** Debug builds only: rules, detection health, event log and screen dumps. */
    showDeveloperTools: Boolean,
    modifier: Modifier = Modifier,
) {
    var allow by remember { mutableFloatStateOf(AppSettings.allowWindowSeconds.toFloat()) }
    var throttle by remember { mutableFloatStateOf(AppSettings.throttleMs.toFloat()) }
    var friction by remember { mutableFloatStateOf(AppSettings.frictionSeconds.toFloat()) }
    var friends by remember { mutableStateOf(AppSettings.friendApps) }
    var haptics by remember { mutableStateOf(AppSettings.hapticsEnabled) }
    val buzz = rememberBuzz()

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {

        Card(Modifier.fillMaxWidth()) {
            NavRow("Setup checklist", if (setupLeft == 0) "All done" else "$setupLeft step(s) still to do") { onNavigate("setup") }
        }

        SliderCard(
            title = "Friend-link window",
            valueLabel = "${allow.toInt()} s",
            help = "A video opened this soon after leaving a messaging app (or a chat or story) counts as sent by a friend and is allowed once.",
            value = allow, range = 1f..30f, step = 1f, majorEvery = 5,
        ) {
            allow = it
            AppSettings.allowWindowSeconds = it.toInt()
        }
        SliderCard(
            title = "Screen-check throttle",
            valueLabel = "${throttle.toInt()} ms",
            help = "Minimum gap between two checks of the same app while its screen is changing. Lower reacts faster and uses more battery.",
            value = throttle, range = 50f..1000f, step = 10f, majorEvery = 10,
        ) {
            throttle = it
            AppSettings.throttleMs = it.toInt()
        }
        SliderCard(
            title = "Turn-off delay",
            valueLabel = if (friction.toInt() == 0) "off" else "${friction.toInt()} s",
            help = "How long the screen for switching ScrollBreak off stays covered by a countdown. 0 turns this off.",
            value = friction, range = 0f..300f, step = 5f, majorEvery = 6,
        ) {
            friction = it
            AppSettings.frictionSeconds = it.toInt()
        }

        SectionLabel("Counts as \"a friend sent this\"")
        friendLabels.entries.chunked(3).forEach { row ->
            // Equal heights, so a two-line name does not make one tile taller than its row.
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (pkg, label) ->
                    AppTile(
                        name = label,
                        glyph = appGlyph(pkg),
                        on = pkg in friends,
                        onToggle = { on ->
                            friends = if (on) friends + pkg else friends - pkg
                            AppSettings.friendApps = friends
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        stateText = { "friend link ${if (it) "allowed once" else "not counted"}" },
                    )
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Vibration", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "A light buzz on switches, app tiles and dial steps.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = haptics,
                    onCheckedChange = { on ->
                        // Saved first so turning it on is felt straight away.
                        AppSettings.hapticsEnabled = on
                        haptics = on
                        buzz(Buzz.CLICK)
                    },
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Uninstall protection", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (adminActive) "On. Android will not uninstall ScrollBreak until you switch this off first."
                    else "Off. Turning it on adds one extra step before ScrollBreak can be uninstalled. It grants no other power.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (adminActive) {
                    OutlinedButton(onClick = onOpenAdminSettings) { Text("Open device-admin settings") }
                } else {
                    Button(onClick = onEnableAdmin) { Text("Turn on") }
                }
            }
        }

        OutlinedButton(
            onClick = {
                AppSettings.resetTuning()
                allow = AppSettings.allowWindowSeconds.toFloat()
                throttle = AppSettings.throttleMs.toFloat()
                friction = AppSettings.frictionSeconds.toFloat()
                friends = AppSettings.friendApps
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Reset these settings to defaults") }

        // Tools for writing and checking detection rules. A shipped (release) build hides them.
        if (showDeveloperTools) {
            SectionLabel("Developer tools")
            Card(Modifier.fillMaxWidth()) {
                Column {
                    NavRow("Rules", "Import, export and reset app rules") { onNavigate("rules") }
                    NavRow("Detection health", "When each signal last matched") { onNavigate("health") }
                    NavRow("Event log", "What ScrollBreak decided, and when") { onNavigate("log") }
                    NavRow("Screen dumps", "Capture screens to write new rules") { onNavigate("dumps") }
                }
            }
        }
    }
}

@Composable
private fun SliderCard(
    title: String,
    valueLabel: String,
    help: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    majorEvery: Int,
    onChange: (Float) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LensDial(
                value = value, range = range, step = step, majorEvery = majorEvery,
                label = valueLabel, onChange = onChange, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
