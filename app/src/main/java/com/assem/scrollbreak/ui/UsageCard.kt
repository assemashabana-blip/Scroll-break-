package com.assem.scrollbreak.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.assem.scrollbreak.core.UsageTime
import kotlin.math.max

/**
 * Time spent in each watched app, today or over 7 days. [usage] is null until the user grants
 * Usage access; the card then explains why and links to the setting.
 */
@Composable
fun UsageCard(usage: UsageTime.Summary?, onAllow: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (usage == null) {
                Text("Time in apps", style = MaterialTheme.typography.titleMedium)
                Text(
                    "See how long you spend in each of these apps. Android keeps this count; allow ScrollBreak to read it. It stays on this phone.",
                    style = MaterialTheme.typography.bodySmall, color = muted,
                )
                Button(onClick = onAllow, modifier = Modifier.fillMaxWidth()) { Text("Allow usage access") }
                return@Column
            }

            var week by rememberSaveable { mutableStateOf(false) }
            val byApp = if (week) usage.weekByApp else usage.todayByApp
            val total = if (week) usage.perDay.sum() else usage.todayMs

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Time in apps", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                RangeChip("Today", !week) { week = false }
                RangeChip("7 days", week) { week = true }
            }
            Text(UsageTime.format(total), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)

            val shown = byApp.entries.filter { it.value >= 60_000 }.take(6)
            if (shown.isEmpty()) {
                Text("No time in these apps yet.", style = MaterialTheme.typography.bodySmall, color = muted)
            } else {
                val ink = MaterialTheme.colorScheme.onSurface
                val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
                val top = max(shown.first().value, 1L)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    shown.forEach { (app, ms) ->
                        val fraction by animateFloatAsState(ms.toFloat() / top, tween(600), label = "usage")
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val glyph = appGlyph(app)
                            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                                if (glyph != null) Icon(painterResource(glyph), null, Modifier.size(20.dp), tint = ink)
                            }
                            Text(appDisplayName(app), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(84.dp))
                            Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(track)) {
                                Box(Modifier.fillMaxWidth(fraction).height(10.dp).clip(RoundedCornerShape(5.dp)).background(ink))
                            }
                            Text(
                                UsageTime.format(ms), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(64.dp), textAlign = TextAlign.End,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RangeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) scheme.surface else scheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = 6.dp)
            .clip(CircleShape)
            .background(if (selected) scheme.onSurface else scheme.surface.copy(alpha = 0f))
            .semantics { this.selected = selected }
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
