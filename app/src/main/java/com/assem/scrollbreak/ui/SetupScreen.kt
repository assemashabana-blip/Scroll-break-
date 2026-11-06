package com.assem.scrollbreak.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.assem.scrollbreak.SetupStep
import com.assem.scrollbreak.StepState
import com.assem.scrollbreak.ui.theme.SbAccent

@Composable
fun SetupScreen(steps: List<SetupStep>, onChanged: () -> Unit, modifier: Modifier = Modifier) {
    val done = steps.count { it.state == StepState.DONE }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProgressCard(done, steps.size)
        steps.forEach { step -> StepCard(step, onChanged) }
    }
}

/** A ring that fills as steps get done, with the count in the middle. */
@Composable
private fun ProgressCard(done: Int, total: Int) {
    val fraction by animateFloatAsState(if (total == 0) 1f else done.toFloat() / total, tween(600), label = "progress")
    val allDone = done == total
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val fill = if (allDone) MaterialTheme.colorScheme.onSurface else SbAccent
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                    val inset = stroke.width / 2
                    val arc = Size(size.width - stroke.width, size.height - stroke.width)
                    drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = stroke)
                    if (fraction > 0f) drawArc(fill, -90f, 360f * fraction, false, Offset(inset, inset), arc, style = stroke)
                }
                Text("$done/$total", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (allDone) "All set" else "${total - done} to go",
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (allDone) "ScrollBreak is configured to keep running."
                    else "Finish these so Android does not stop ScrollBreak. Come back after each one.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StepCard(step: SetupStep, onChanged: () -> Unit) {
    val finished = step.state == StepState.DONE
    // Open steps start expanded so the action is in reach; finished ones fold away.
    var expanded by remember(step.id, finished) { mutableStateOf(!finished) }
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, tween(250), label = "arrow")
    Card(Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
        // One size animation for the whole card. The gap above the details lives inside them, so
        // nothing jumps when they finish folding (a spacedBy gap would vanish at the last frame).
        Column(Modifier.animateContentSize(tween(250)).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StatusBadge(step.state)
                Column(Modifier.weight(1f)) {
                    Text(step.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        when (step.state) {
                            StepState.DONE -> "Done"
                            StepState.TODO -> "Not done"
                            StepState.MANUAL -> "Check this yourself"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (finished) MaterialTheme.colorScheme.onSurfaceVariant else SbAccent,
                    )
                }
                Text("⌄", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.rotate(arrow))
            }
            if (expanded) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(step.help, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!finished) {
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

/** Filled tick when done, orange ring with ! when not, grey ring with ? when only the user can tell. */
@Composable
private fun StatusBadge(state: StepState) {
    val scheme = MaterialTheme.colorScheme
    val fill by animateColorAsState(if (state == StepState.DONE) scheme.onSurface else Color.Transparent, label = "badgeFill")
    val ring = when (state) {
        StepState.DONE -> scheme.onSurface
        StepState.TODO -> SbAccent
        StepState.MANUAL -> scheme.outline
    }
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(fill).border(2.dp, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            when (state) {
                StepState.DONE -> "✓"
                StepState.TODO -> "!"
                StepState.MANUAL -> "?"
            },
            fontWeight = FontWeight.Bold,
            color = if (state == StepState.DONE) scheme.background else ring,
        )
    }
}
