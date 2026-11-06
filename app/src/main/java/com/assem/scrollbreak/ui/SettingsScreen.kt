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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
    modifier: Modifier = Modifier,
) {
    var allow by remember { mutableFloatStateOf(AppSettings.allowWindowSeconds.toFloat()) }
    var throttle by remember { mutableFloatStateOf(AppSettings.throttleMs.toFloat()) }
    var friction by remember { mutableFloatStateOf(AppSettings.frictionSeconds.toFloat()) }
    var friends by remember { mutableStateOf(AppSettings.friendApps) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {

        SliderCard(
            title = "Friend-link window: ${allow.toInt()} s",
            help = "A video opened this soon after leaving a messaging app (or a chat or story) counts as sent by a friend and is allowed once.",
            value = allow, range = 1f..30f,
        ) {
            allow = it
            AppSettings.allowWindowSeconds = it.toInt()
        }
        SliderCard(
            title = "Screen-check throttle: ${throttle.toInt()} ms",
            help = "Minimum gap between two checks of the same app while its screen is changing. Lower reacts faster and uses more battery.",
            value = throttle, range = 50f..1000f,
        ) {
            throttle = it
            AppSettings.throttleMs = it.toInt()
        }
        SliderCard(
            title = if (friction.toInt() == 0) "Turn-off delay: off" else "Turn-off delay: ${friction.toInt()} s",
            help = "How long the screen for switching ScrollBreak off stays covered by a countdown. 0 turns this off.",
            value = friction, range = 0f..300f,
        ) {
            friction = it
            AppSettings.frictionSeconds = it.toInt()
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Apps that count as \"a friend sent this\"", style = MaterialTheme.typography.titleMedium)
                friendLabels.forEach { (pkg, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = pkg in friends,
                            onCheckedChange = { on ->
                                friends = if (on) friends + pkg else friends - pkg
                                AppSettings.friendApps = friends
                            },
                        )
                        Text(label)
                    }
                }
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
    }
}

@Composable
private fun SliderCard(
    title: String,
    help: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(help, style = MaterialTheme.typography.bodySmall)
            Slider(value = value, onValueChange = onChange, valueRange = range)
        }
    }
}
