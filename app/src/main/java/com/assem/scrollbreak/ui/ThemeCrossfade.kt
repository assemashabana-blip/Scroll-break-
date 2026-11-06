package com.assem.scrollbreak.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/**
 * Switches between light and dark without a hitch. A theme change rebuilds every screen at once,
 * which is too much for one frame. So: let the day/night switch finish its slide first, then take
 * a picture of the screen, lay it on top, swap the theme underneath and fade the picture out. The
 * rebuild happens behind the picture. With "Remove animations" on, the theme simply swaps.
 *
 * [content] gets the theme to draw with, which lags [dark] by the length of the switch animation.
 */
@Composable
fun ThemeCrossfade(dark: Boolean, content: @Composable (dark: Boolean) -> Unit) {
    val context = LocalContext.current
    val still = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    var shown by remember { mutableStateOf(dark) }
    var snapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    var capture by remember { mutableStateOf(false) }
    val fade = remember { Animatable(1f) }
    val layer = rememberGraphicsLayer()

    LaunchedEffect(dark) {
        if (dark == shown) return@LaunchedEffect
        if (still) {
            shown = dark
            return@LaunchedEffect
        }
        delay(SWITCH_SETTLE_MS)
        // Record the next frame into the layer, then turn it into a picture.
        capture = true
        withFrameNanos { }
        withFrameNanos { }
        snapshot = runCatching { layer.toImageBitmap() }.getOrNull()
        capture = false
        fade.snapTo(1f)
        shown = dark
        if (snapshot != null) {
            fade.animateTo(0f, tween(FADE_MS, easing = FastOutSlowInEasing))
            snapshot = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().drawWithContent {
                if (capture) {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                } else {
                    drawContent()
                }
            },
        ) { content(shown) }
        snapshot?.let { picture ->
            Image(
                picture, contentDescription = null, contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = fade.value },
            )
        }
    }
}

/** About how long the day/night switch takes to settle (its spring, see DayNightToggle). */
private const val SWITCH_SETTLE_MS = 420L
private const val FADE_MS = 380
