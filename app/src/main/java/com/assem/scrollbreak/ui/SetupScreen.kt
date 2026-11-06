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
import com.assem.scrollbreak.Setup
import com.assem.scrollbreak.SetupStep
import com.assem.scrollbreak.StepState

@Composable
fun SetupScreen(steps: List<SetupStep>, onChanged: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val left = Setup.remaining(steps)
        Text(
            if (left == 0) "All set. ScrollBreak is configured to keep running."
            else "$left of ${steps.size} steps still need attention. Come back here after each one.",
            style = MaterialTheme.typography.bodyMedium,
        )
        steps.forEach { step ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(step.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        when (step.state) {
                            StepState.DONE -> "Done"
                            StepState.TODO -> "To do"
                            StepState.MANUAL -> "Check this yourself"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (step.state == StepState.DONE) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                    )
                    Text(step.help, style = MaterialTheme.typography.bodyMedium)
                    if (step.state != StepState.DONE) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { step.action(); onChanged() }) { Text(step.actionLabel) }
                            step.markDone?.let { mark ->
                                OutlinedButton(onClick = { mark(); onChanged() }) { Text("I've done this") }
                            }
                        }
                    }
                }
            }
        }
    }
}
