package com.assem.scrollbreak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.assem.scrollbreak.core.LockTimer
import com.assem.scrollbreak.core.LockUnit

/**
 * A length of time chosen with the lens dial, with min / h / d chips to change what a tick means
 * and a pencil chip on the number to type days, hours and minutes. Used for the lock timer, a
 * daily app limit and an app block; [maxMinutes] is the longest each of them allows.
 */
@Composable
fun DurationPicker(
    minutes: Int,
    onMinutes: (Int) -> Unit,
    maxMinutes: Int,
    modifier: Modifier = Modifier,
) {
    val buzz = rememberBuzz()
    val units = LockUnit.available(maxMinutes)
    // What one tick of the dial means. It follows a length that is typed in; the chips change it by hand.
    var unit by rememberSaveable {
        mutableStateOf(LockUnit.forMinutes(minutes).takeIf { it in units } ?: units.last())
    }
    var typing by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Rebuilt when the unit changes so the dial starts from the new scale.
        key(unit) {
            LensDial(
                value = unit.toDial(minutes),
                range = unit.min.toFloat()..unit.maxFor(maxMinutes).toFloat(),
                step = unit.step.toFloat(),
                majorEvery = unit.majorEvery,
                label = LockTimer.describe(minutes),
                onChange = { onMinutes(unit.fromDial(it).coerceAtMost(maxMinutes)) },
                modifier = Modifier.fillMaxWidth(),
                onLabelClick = { typing = true },
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            units.forEach { u ->
                FilterChip(
                    selected = u == unit,
                    onClick = {
                        buzz(Buzz.CLICK)
                        unit = u
                        onMinutes(u.clamp(minutes, maxMinutes))
                    },
                    label = { Text(u.short) },
                )
            }
        }
    }

    if (typing) {
        TimeEntryDialog(
            initialMinutes = minutes,
            maxMinutes = maxMinutes,
            onDismiss = { typing = false },
            onConfirm = {
                onMinutes(it)
                unit = LockUnit.forMinutes(it).takeIf { u -> u in units } ?: units.last()
                typing = false
            },
        )
    }
}

/** Type a length as days, hours and minutes (days only when more than a day is allowed). */
@Composable
private fun TimeEntryDialog(initialMinutes: Int, maxMinutes: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val showDays = maxMinutes > 24 * 60
    var days by remember { mutableStateOf((initialMinutes / 1440).toString()) }
    var hours by remember { mutableStateOf((if (showDays) initialMinutes % 1440 / 60 else initialMinutes / 60).toString()) }
    var mins by remember { mutableStateOf((initialMinutes % 60).toString()) }
    val total = (if (showDays) (days.toIntOrNull() ?: 0).toLong() * 1440 else 0L) +
        (hours.toIntOrNull() ?: 0).toLong() * 60 + (mins.toIntOrNull() ?: 0)
    val tooLong = total > maxMinutes
    val valid = total >= 1 && !tooLong
    val numbers = KeyboardOptions(keyboardType = KeyboardType.Number)

    @Composable
    fun field(value: String, label: String, onChange: (String) -> Unit, modifier: Modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it.filter(Char::isDigit).take(4)) },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = numbers,
            modifier = modifier,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set the time") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (showDays) field(days, "Days", { days = it }, Modifier.weight(1f))
                    field(hours, "Hours", { hours = it }, Modifier.weight(1f))
                    field(mins, "Min", { mins = it }, Modifier.weight(1f))
                }
                Text(
                    when {
                        tooLong -> "The longest is ${LockTimer.describe(maxMinutes)}."
                        valid -> LockTimer.describe(total.toInt())
                        else -> "Enter at least 1 minute."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(total.toInt()) }) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
