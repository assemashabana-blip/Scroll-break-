package com.assem.scrollbreak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assem.scrollbreak.AppLog
import com.assem.scrollbreak.core.LogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EventLogScreen(modifier: Modifier = Modifier) {
    val live by AppLog.events.entries.collectAsState()
    var frozen by remember { mutableStateOf<List<LogEntry>?>(null) }
    val shown = frozen ?: live
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { frozen = if (frozen == null) live else null }) {
                Text(if (frozen == null) "Pause" else "Resume")
            }
            OutlinedButton(onClick = { AppLog.events.clear(); frozen = null }) { Text("Clear") }
            Text("${shown.size} events", Modifier.padding(top = 12.dp))
        }
        HorizontalDivider()
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it.id }) { e ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(
                        "${timeFormat.format(Date(e.timeMs))}  ${e.eventType}  [${e.state}]",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        e.packageName,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                    Text(
                        e.className + if (e.detail.isNotEmpty()) "  ${e.detail}" else "",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}
