package com.assem.scrollbreak.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.assem.scrollbreak.R
import com.assem.scrollbreak.core.EmergencyPassword
import com.assem.scrollbreak.ui.theme.SbAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val LENGTH = EmergencyPassword.CODE_LENGTH

private enum class Phase { ENTER, SUCCESS }

/**
 * A six-digit code entry with its own number pad. Dots fill with a springy pop as digits go in, a
 * wrong code shakes the dots red, and a right one sends a wave of orange through them.
 *
 * Each of [steps] is one round of six digits (for example "Enter the code", then "Repeat the
 * code"). When the last round is complete, [onSubmit] gets every round's digits and returns an
 * error text to show (the dots shake and everything starts again), or null for success.
 */
@Composable
fun CodeDialog(
    title: String,
    steps: List<String>,
    doneText: String,
    onDismiss: () -> Unit,
    onSubmit: (List<String>) -> String?,
) {
    val buzz = rememberBuzz()
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    val finished = remember { mutableStateListOf<String>() }
    var entry by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(Phase.ENTER) }
    var busy by remember { mutableStateOf(false) }

    val shake = remember { Animatable(0f) }
    val wave = remember { List(LENGTH) { Animatable(1f) } }
    val intro = remember { Animatable(0.88f) }
    LaunchedEffect(Unit) { intro.animateTo(1f, spring(0.55f, Spring.StiffnessMediumLow)) }

    fun complete(code: String) {
        busy = true
        scope.launch {
            delay(180) // let the last dot land before anything else happens
            if (step < steps.lastIndex) {
                finished += code
                entry = ""
                step++
                busy = false
                return@launch
            }
            val error = onSubmit(finished.toList() + code)
            if (error == null) {
                phase = Phase.SUCCESS
                buzz(Buzz.CLICK)
                // A wave runs through the six dots, then the dialog closes.
                wave.forEachIndexed { i, a ->
                    launch {
                        delay(i * 70L)
                        a.animateTo(1.55f, tween(150))
                        a.animateTo(1f, spring(0.4f, Spring.StiffnessMedium))
                    }
                }
                delay(1100)
                onDismiss()
            } else {
                message = error
                failed = true
                buzz(Buzz.HEAVY)
                shake.animateTo(
                    0f,
                    keyframes {
                        durationMillis = 440
                        -22f at 50
                        22f at 130
                        -15f at 200
                        15f at 270
                        -7f at 340
                        0f at 440
                    },
                )
                delay(120)
                entry = ""
                finished.clear()
                step = 0
                failed = false
                busy = false
            }
        }
    }

    fun press(digit: Char) {
        if (busy || phase != Phase.ENTER || entry.length >= LENGTH) return
        buzz(Buzz.TICK)
        message = null
        entry += digit
        if (entry.length == LENGTH) complete(entry)
    }

    fun erase() {
        if (busy || phase != Phase.ENTER || entry.isEmpty()) return
        buzz(Buzz.TICK)
        entry = entry.dropLast(1)
    }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.graphicsLayer {
                scaleX = intro.value
                scaleY = intro.value
                alpha = ((intro.value - 0.88f) / 0.12f).coerceIn(0f, 1f)
            },
        ) {
            Column(
                Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                // The question changes with each round: it slides across.
                AnimatedContent(
                    targetState = if (phase == Phase.SUCCESS) doneText else steps[step],
                    transitionSpec = {
                        (slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(260)))
                            .togetherWith(slideOutHorizontally(tween(200)) { -it / 3 } + fadeOut(tween(160)))
                    },
                    label = "prompt",
                ) { text ->
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (phase == Phase.SUCCESS) SbAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(22.dp))

                Row(
                    Modifier
                        .offset { IntOffset(shake.value.dp.toPx().roundToInt(), 0) }
                        .semantics { contentDescription = "${entry.length} of $LENGTH digits entered" },
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    repeat(LENGTH) { i ->
                        CodeDot(filled = i < entry.length, failed = failed, success = phase == Phase.SUCCESS, wave = wave[i].value)
                    }
                }

                // Reserved height, so the pad does not jump when a message appears.
                Box(Modifier.height(40.dp).padding(top = 10.dp), contentAlignment = Alignment.Center) {
                    Crossfade(message, label = "message") { m ->
                        if (m != null) {
                            Text(m, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        }
                    }
                }

                Keypad(onDigit = ::press, onErase = ::erase)
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun CodeDot(filled: Boolean, failed: Boolean, success: Boolean, wave: Float) {
    val scale by animateFloatAsState(
        if (filled) 1f else 0.62f,
        spring(dampingRatio = 0.38f, stiffness = Spring.StiffnessMedium),
        label = "dotScale",
    )
    val empty = MaterialTheme.colorScheme.outline
    val color by animateColorAsState(
        when {
            failed -> MaterialTheme.colorScheme.error
            success -> SbAccent
            filled -> MaterialTheme.colorScheme.onSurface
            else -> empty
        },
        tween(160),
        label = "dotColor",
    )
    Box(
        Modifier
            .size(20.dp)
            .graphicsLayer {
                scaleX = scale * wave
                scaleY = scale * wave
            }
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun Keypad(onDigit: (Char) -> Unit, onErase: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                row.forEach { d -> Key(label = d.toString()) { onDigit(d) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Spacer(Modifier.size(KEY_SIZE))
            Key(label = "0") { onDigit('0') }
            Key(label = null, onClick = onErase)
        }
    }
}

private val KEY_SIZE = 68.dp

@Composable
private fun Key(label: String?, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    // The key dips and the orange flashes in while a finger is on it.
    val scale by animateFloatAsState(if (pressed) 0.86f else 1f, spring(0.5f, Spring.StiffnessMedium), label = "keyScale")
    val fill by animateColorAsState(
        if (pressed) SbAccent.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
        tween(90),
        label = "keyFill",
    )
    Box(
        Modifier
            .size(KEY_SIZE)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(fill)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) {
            Text(label, fontSize = 26.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        } else {
            Icon(
                painterResource(R.drawable.ic_backspace),
                contentDescription = "Delete the last digit",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
