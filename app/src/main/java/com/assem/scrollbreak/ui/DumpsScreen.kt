package com.assem.scrollbreak.ui

import android.content.Context
import android.content.Intent
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.assem.scrollbreak.DumpStore
import com.assem.scrollbreak.service.ScrollBreakService
import java.io.File

@Composable
fun DumpsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val files by DumpStore.files.collectAsState()
    LaunchedEffect(Unit) { DumpStore.refresh(context) }

    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Tap \"Dump in 5 s\", switch to the screen you want to capture (e.g. a YouTube Short), " +
                    "and wait. You can also use the notification actions.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { ScrollBreakService.instance?.requestDump(5000) }) {
                    Text("Dump in 5 s")
                }
                OutlinedButton(onClick = { DumpStore.deleteAll(context) }) { Text("Delete all") }
            }
            if (ScrollBreakService.instance == null) {
                Text("Service is not running.", color = MaterialTheme.colorScheme.error)
            }
        }
        HorizontalDivider()
        LazyColumn(Modifier.fillMaxSize()) {
            items(files, key = { it.name }) { f ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.padding(top = 8.dp)) {
                        Text(f.name, style = MaterialTheme.typography.bodyMedium)
                        Text("${f.length() / 1024} KB", style = MaterialTheme.typography.labelSmall)
                    }
                    Row {
                        TextButton(onClick = { share(context, f) }) { Text("Share") }
                        TextButton(onClick = { DumpStore.delete(context, f) }) { Text("Delete") }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

private fun share(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Export dump"))
}
