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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.assem.scrollbreak.LoadedRule
import com.assem.scrollbreak.RuleSource
import com.assem.scrollbreak.core.rules.Purpose

@Composable
fun RulesScreen(
    loaded: List<LoadedRule>,
    errors: List<String>,
    onImport: () -> Unit,
    onExport: (LoadedRule) -> Unit,
    onReset: (LoadedRule) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Rules say how each app's screens are recognised. An imported file replaces the built-in rule " +
                "of the same app name; it is checked first and rejected if invalid.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("Import rule file (.json)") }

        errors.forEach {
            Text("Rejected: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        loaded.forEach { l ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(l.rule.app, style = MaterialTheme.typography.titleMedium)
                    Text(
                        (if (l.source == RuleSource.IMPORTED) "Imported" else "Built-in") +
                            (if (l.rule.purpose == Purpose.FRICTION) " · turn-off friction" else "") +
                            " · ${l.fileName}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text("Apps: ${l.rule.packages.joinToString()}", style = MaterialTheme.typography.bodySmall)
                    Text("Recognises: ${l.rule.signalNames().joinToString().ifEmpty { "-" }}", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onExport(l) }) { Text("Export") }
                        if (l.source == RuleSource.IMPORTED) {
                            OutlinedButton(onClick = { onReset(l) }) { Text("Use built-in") }
                        }
                    }
                }
            }
        }
    }
}
