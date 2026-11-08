package com.assem.scrollbreak.ui

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.hypot
import kotlin.math.max

/**
 * Light/dark switch as a circular reveal: the new theme grows out of the day/night switch until it
 * fills the screen.
 *
 * How: a picture of the screen in the old theme is laid on top, the theme swaps underneath (that
 * rebuild is the slow part, and it happens while the screen still looks unchanged), then a growing
 * circle is cut out of the picture. The animation only clips a picture, so it never waits on the
 * app. The switch itself is always cut out, so its knob slides live in step with the circle.
 * With "Remove animations" on, the theme simply swaps.
 */
@Stable
class ThemeReveal(private val window: Window, private val still: Boolean, initialDark: Boolean) {
    /** The theme the app draws with. */
    var dark by mutableStateOf(initialDark)
        private set

    /** The theme the status and navigation bar icons should follow; flips once the circle covers the top. */
    var barsDark by mutableStateOf(initialDark)
        private set

    /** True while a reveal is on screen. */
    var running by mutableStateOf(false)
        private set

    internal var picture by mutableStateOf<ImageBitmap?>(null)
    internal var center = Offset.Zero
    internal var keepLive = Rect.Zero
    internal val radius = Animatable(0f)
    private var job: Job? = null
    private var generation = 0

    /** Switches to [target], growing the new theme out of [from] (window coordinates, e.g. the switch's track). */
    fun switchTo(target: Boolean, from: Rect, scope: CoroutineScope) {
        if (target == dark && job?.isActive != true) return
        job?.cancel()
        val mine = ++generation
        job = scope.launch {
            try {
                val shot = if (still) null else capture()
                if (shot == null) {
                    dark = target
                    barsDark = target
                    return@launch
                }
                val w = window.decorView.width.toFloat()
                val h = window.decorView.height.toFloat()
                val c = from.center
                val farthest = max(max(hypot(c.x, c.y), hypot(w - c.x, c.y)), max(hypot(c.x, h - c.y), hypot(w - c.x, h - c.y)))
                val coversTop = max(hypot(c.x, c.y), hypot(w - c.x, c.y))
                center = c
                keepLive = from
                radius.snapTo(0f)
                running = true
                picture = shot
                withFrameNanos { } // the picture is on screen
                dark = target // the rebuild happens under it
                withFrameNanos { }
                withFrameNanos { }
                radius.animateTo(farthest, tween(REVEAL_MS, easing = FastOutSlowInEasing)) {
                    if (value >= coversTop) barsDark = target
                }
                barsDark = target
            } finally {
                // A newer switch took over; it owns the picture now.
                if (generation == mine) {
                    picture = null
                    running = false
                }
            }
        }
    }

    /** The window as it is on screen now, copied on the GPU (no app redraw needed). */
    private suspend fun capture(): ImageBitmap? {
        val view = window.decorView
        if (view.width == 0 || view.height == 0) return null
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        return suspendCancellableCoroutine { cont ->
            runCatching {
                PixelCopy.request(window, bitmap, { result ->
                    if (cont.isActive) cont.resume(if (result == PixelCopy.SUCCESS) bitmap.asImageBitmap() else null)
                }, Handler(Looper.getMainLooper()))
            }.onFailure { if (cont.isActive) cont.resume(null) }
        }
    }
}

/** Draws the old-theme picture with the growing hole. Put it on top of everything, full screen. */
@Composable
fun ThemeRevealOverlay(reveal: ThemeReveal) {
    val picture = reveal.picture ?: return
    var origin by remember { mutableStateOf(Offset.Zero) }
    Canvas(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        val r = reveal.radius.value
        val c = reveal.center
        val live = reveal.keepLive
        val hole = Path().apply {
            addOval(Rect(c, r))
            addRoundRect(RoundRect(live, CornerRadius(live.height / 2)))
        }
        // Everything is in window coordinates, like the picture.
        translate(-origin.x, -origin.y) {
            clipPath(hole, ClipOp.Difference) { drawImage(picture) }
        }
    }
}

private const val REVEAL_MS = 650
