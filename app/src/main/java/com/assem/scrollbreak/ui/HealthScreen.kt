package com.assem.scrollbreak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.assem.scrollbreak.AppLog
import com.assem.scrollbreak.LoadedRule
import com.assem.scrollbreak.core.rules.Purpose
import kotlinx.coroutines.delay

@Composable
fun HealthScreen(loaded: List<LoadedRule>, onExportLogs: () -> Unit, modifier: Modifier = Modifier) {
    val entries by AppLog.health.entries.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "When each way of recognising a screen last matched since the app started. " +
                "If an app updates and a signal that used to match stops, its rule needs a new dump.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { AppLog.health.clear() }) { Text("Reset") }
            OutlinedButton(onClick = onExportLogs) { Text("Export logs") }
        }
        loaded.filter { it.rule.purpose == Purpose.SHORT_VIDEO }.forEach { l ->
            val seen = entries[l.rule.app].orEmpty()
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(l.rule.app, style = MaterialTheme.typography.titleMedium)
                    l.rule.signalNames().forEach { signal ->
                        val e = seen[signal]
                        Text(
                            if (e == null) "$signal: never matched"
                            else "$signal: ${ago(now - e.lastMs)} ago · ${e.count} time(s)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (e == null) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

private fun ago(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> "${s}s"
        s < 3600 -> "${s / 60}m ${s % 60}s"
        else -> "${s / 3600}h ${(s % 3600) / 60}m"
    }
}
