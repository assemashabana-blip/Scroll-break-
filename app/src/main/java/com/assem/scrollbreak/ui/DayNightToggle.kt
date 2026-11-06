package com.assem.scrollbreak.ui

import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.assem.scrollbreak.ui.theme.SbCloud
import com.assem.scrollbreak.ui.theme.SbMoon
import com.assem.scrollbreak.ui.theme.SbMoonCrater
import com.assem.scrollbreak.ui.theme.SbSkyDay
import com.assem.scrollbreak.ui.theme.SbSkyNight
import com.assem.scrollbreak.ui.theme.SbStar
import com.assem.scrollbreak.ui.theme.SbSun

/** Star positions as fractions of the track (x, y) and radius in dp; they sit left of the night knob. */
private val stars = listOf(
    Triple(0.16f, 0.30f, 1.6f), Triple(0.30f, 0.62f, 1.1f), Triple(0.42f, 0.24f, 1.3f),
    Triple(0.24f, 0.80f, 0.9f), Triple(0.54f, 0.52f, 1.0f), Triple(0.09f, 0.60f, 0.8f),
)

/** Cloud puffs as (x fraction, y fraction, radius fraction of track height); back layer first. */
private val cloudBack = listOf(Triple(0.50f, 0.86f, 0.30f), Triple(0.68f, 0.70f, 0.34f), Triple(0.88f, 0.62f, 0.36f), Triple(1.02f, 0.70f, 0.34f))
private val cloudFront = listOf(Triple(0.44f, 1.02f, 0.30f), Triple(0.60f, 0.92f, 0.32f), Triple(0.78f, 0.84f, 0.34f), Triple(0.96f, 0.86f, 0.36f))

/**
 * Light/dark switch drawn as a day sky with a sun (off) or a night sky with a moon (on).
 * A real Switch for accessibility: "Dark mode", focusable, 48 dp touch target. With "Remove
 * animations" on, it swaps states without moving.
 */
@Composable
fun DayNightToggle(dark: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val still = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val buzz = rememberBuzz()
    // Same springy feel as the app tiles, a little softer so the trip takes about half a second.
    val p by animateFloatAsState(
        if (dark) 1f else 0f,
        if (still) snap() else spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow),
        label = "dayNight",
    )
    val twinkle = if (still) 1f else {
        val t by rememberInfiniteTransition(label = "twinkle").animateFloat(
            0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "twinkle",
        )
        t
    }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val ring = MaterialTheme.colorScheme.onSurface
    val pill = RoundedCornerShape(50)

    Box(
        modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .toggleable(value = dark, interactionSource = interaction, indication = null, role = Role.Switch) {
                buzz(Buzz.CLICK)
                onChange(it)
            }
            .semantics { contentDescription = "Dark mode" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.border(2.dp, if (focused) ring else Color.Transparent, pill).padding(3.dp)) {
            Canvas(Modifier.size(width = 76.dp, height = 36.dp)) { drawToggle(p, twinkle) }
        }
    }
}

private fun DrawScope.drawToggle(p: Float, twinkle: Float) {
    val night = p.coerceIn(0f, 1f)
    val w = size.width
    val h = size.height
    val pad = 3.dp.toPx()
    val knobR = h / 2 - pad
    // The knob may overshoot a little with the spring; that is the bounce.
    val cx = (pad + knobR) + (w - 2 * (pad + knobR)) * p
    val cy = h / 2
    val track = Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(h / 2))) }

    clipPath(track) {
        drawRect(lerp(SbSkyDay, SbSkyNight, night))

        // Glow rings around the sun.
        for (i in 3 downTo 1) {
            drawCircle(Color.White.copy(alpha = 0.20f * (1 - night)), knobR + i * 6.dp.toPx(), Offset(cx, cy))
        }

        // Stars fade and grow in, each twinkling on its own phase.
        stars.forEachIndexed { i, (fx, fy, r) ->
            val phase = if (i % 2 == 0) twinkle else 1 - twinkle
            drawCircle(
                SbStar.copy(alpha = night * (0.5f + 0.5f * phase)),
                r.dp.toPx() * night,
                Offset(fx * w, fy * h),
            )
        }

        // Clouds sink below the bottom edge and fade at night.
        val drop = night * h * 0.8f
        cloudBack.forEach { (fx, fy, fr) ->
            drawCircle(SbCloud.copy(alpha = 0.55f * (1 - night)), fr * h, Offset(fx * w, fy * h + drop))
        }
        cloudFront.forEach { (fx, fy, fr) ->
            drawCircle(SbCloud.copy(alpha = 1 - night), fr * h, Offset(fx * w, fy * h + drop))
        }

        // Inner shadow along the top edge for depth.
        drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.22f), 0.35f to Color.Transparent))
    }

    // Soft drop shadow under the knob.
    drawCircle(Color.Black.copy(alpha = 0.10f), knobR + 2.5.dp.toPx(), Offset(cx, cy + 2.dp.toPx()))
    drawCircle(Color.Black.copy(alpha = 0.18f), knobR + 0.8.dp.toPx(), Offset(cx, cy + 1.dp.toPx()))

    // Sun turns into moon; craters fade in.
    drawCircle(lerp(SbSun, SbMoon, night), knobR, Offset(cx, cy))
    val crater = SbMoonCrater.copy(alpha = night)
    drawCircle(crater, knobR * 0.24f, Offset(cx - knobR * 0.30f, cy - knobR * 0.28f))
    drawCircle(crater, knobR * 0.30f, Offset(cx + knobR * 0.28f, cy + knobR * 0.18f))
    drawCircle(crater, knobR * 0.14f, Offset(cx - knobR * 0.18f, cy + knobR * 0.48f))
}
