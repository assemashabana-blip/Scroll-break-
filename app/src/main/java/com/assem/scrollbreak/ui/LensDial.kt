package com.assem.scrollbreak.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.assem.scrollbreak.ui.theme.SbAccent
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * A camera-zoom style dial: a ruler of ticks slides under a fixed pointer, curving away and fading
 * towards the edges like a lens ring. Dragging moves it; letting go (or flinging) settles on the
 * nearest [step]. [onChange] fires only when the snapped value changes, with a haptic tick.
 */
@Composable
fun LensDial(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    majorEvery: Int,
    label: String,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val buzz = rememberBuzz()
    val spacing = with(LocalDensity.current) { 12.dp.toPx() }
    val maxIndex = ((range.endInclusive - range.start) / step).roundToInt()
    fun indexOf(v: Float) = ((v - range.start) / step).roundToInt().coerceIn(0, maxIndex)

    // Fractional position in steps while moving; the snapped index is what gets reported.
    var pos by remember { mutableFloatStateOf(indexOf(value).toFloat()) }
    var emitted by remember { mutableIntStateOf(indexOf(value)) }
    var moving by remember { mutableStateOf(false) }
    // Follow outside changes (e.g. "reset to defaults") but never fight the user's finger.
    LaunchedEffect(value) {
        if (!moving) {
            pos = indexOf(value).toFloat()
            emitted = indexOf(value)
        }
    }

    fun moveTo(p: Float) {
        pos = p.coerceIn(0f, maxIndex.toFloat())
        val snapped = pos.roundToInt()
        if (snapped != emitted) {
            emitted = snapped
            buzz(Buzz.TICK)
            onChange(range.start + snapped * step)
        }
    }

    val ink = MaterialTheme.colorScheme.onSurface
    val measurer = rememberTextMeasurer()
    // No colour in the measured style (it is given at draw time), so a theme swap reuses the
    // cached label layouts instead of measuring every tick label again.
    val tickLabelStyle = MaterialTheme.typography.labelSmall

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = SbAccent)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(68.dp)
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(value, range)
                    setProgress { target ->
                        moveTo(indexOf(target).toFloat())
                        true
                    }
                }
                .draggable(
                    state = rememberDraggableState { delta -> moveTo(pos - delta / spacing) },
                    orientation = Orientation.Horizontal,
                    onDragStarted = { moving = true },
                    onDragStopped = { velocity ->
                        // Coast a little in the fling direction, then settle exactly on a step.
                        val target = (pos - velocity / spacing * 0.12f).roundToInt().coerceIn(0, maxIndex).toFloat()
                        animate(pos, target, animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessLow)) { v, _ ->
                            moveTo(v)
                        }
                        moving = false
                    },
                ),
        ) {
            val cx = size.width / 2
            val baseY = 8.dp.toPx()
            val bend = 14.dp.toPx()
            val first = floor(pos - cx / spacing).toInt().coerceAtLeast(0)
            val last = ceil(pos + cx / spacing).toInt().coerceAtMost(maxIndex)
            for (i in first..last) {
                val x = cx + (i - pos) * spacing
                val t = (abs(x - cx) / cx).coerceIn(0f, 1f)
                // Lens ring: ticks drop away and fade as they curve out of view.
                val y = baseY + bend * t * t
                val alpha = (1f - t * t) * 0.9f + 0.05f
                // Major ticks sit on round values (5, 10, 15...), not on multiples counted from the range start.
                val major = (range.start / step).roundToInt().plus(i) % majorEvery == 0
                val h = if (major) 22.dp.toPx() else 11.dp.toPx()
                drawLine(
                    ink.copy(alpha = alpha), Offset(x, y), Offset(x, y + h),
                    strokeWidth = if (major) 2.dp.toPx() else 1.2.dp.toPx(), cap = StrokeCap.Round,
                )
                if (major) {
                    val text = (range.start + i * step).roundToInt().toString()
                    val layout = measurer.measure(text, tickLabelStyle)
                    drawText(
                        layout, color = ink, alpha = alpha * 0.8f,
                        topLeft = Offset(x - layout.size.width / 2f, y + h + 4.dp.toPx()),
                    )
                }
            }
            // Fixed pointer: a small notch and a line through the ruler.
            val notch = Path().apply {
                moveTo(cx - 6.dp.toPx(), 0f)
                lineTo(cx + 6.dp.toPx(), 0f)
                lineTo(cx, 7.dp.toPx())
                close()
            }
            drawPath(notch, SbAccent)
            drawLine(SbAccent, Offset(cx, 0f), Offset(cx, baseY + 30.dp.toPx()), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}
