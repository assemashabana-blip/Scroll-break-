package com.assem.scrollbreak.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assem.scrollbreak.R
import com.assem.scrollbreak.ui.theme.SbAccent
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

// The mock is the phone's own Settings app in dark mode (the grouped-card look of Samsung One UI,
// Xiaomi HyperOS and recent Pixels), not ScrollBreak's theme, so people recognise the real screens.
private object Os {
    val bg = Color(0xFF000000)
    val card = Color(0xFF1C1C1E)
    val text = Color(0xFFF2F2F4)
    val sub = Color(0xFF9B9BA1)
    val divider = Color(0xFF2E2E31)
    val blue = Color(0xFF3E82F7)
    val blueText = Color(0xFF8AB4F8)
    val offTrack = Color(0xFF3A3A3C)
    val offThumb = Color(0xFF9A9AA0)
    val raised = Color(0xFF2C2C2F)
    val frame = Color(0xFF3A3A3D)
}

/** Settings-style row icons: a coloured circle with a white glyph (24-unit path data). */
private enum class Glyph(val color: Color, d: String) {
    NETWORK(Color(0xFF4C8DF6), "M12,20.5L1,8C4,5.3 7.8,3.8 12,3.8S20,5.3 23,8z"),
    DEVICES(Color(0xFF34A853), "M4,5h16v11H4z M2,18h20v2H2z"),
    APPS(Color(0xFFF29900), "M4,4h7v7h-7z M13,4h7v7h-7z M4,13h7v7h-7z M13,13h7v7h-7z"),
    NOTIFICATIONS(Color(0xFFE8710A), "M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.89,2 2,2zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32V4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.63,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2z"),
    BATTERY(Color(0xFF1E8E3E), "M15.67,4H14V2h-4v2H8.33C7.6,4 7,4.6 7,5.33v15.33C7,21.4 7.6,22 8.33,22h7.33c0.74,0 1.34,-0.6 1.34,-1.33V5.33C17,4.6 16.4,4 15.67,4z"),
    SOUND(Color(0xFF9C6ADE), "M3,9v6h4l5,5V4L7,9H3z M16.5,12c0,-1.8 -1,-3.3 -2.5,-4v8c1.5,-0.7 2.5,-2.2 2.5,-4z"),
    DISPLAY(Color(0xFFE3A008), "M12,6.5a5.5,5.5 0 1,1 0,11a5.5,5.5 0 1,1 0,-11z"),
    ACCESSIBILITY(Color(0xFF7B61FF), "M20.5,6c-2.61,0.7 -5.67,1 -8.5,1S6.11,6.7 3.5,6L3,8c1.86,0.5 4,0.83 6,1v13h2v-6h2v6h2V9c2,-0.17 4.14,-0.5 6,-1l-0.5,-2zM12,6c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2z"),
    SECURITY(Color(0xFF12A4AF), "M12,1L3,5v6c0,5.55 3.84,10.74 9,12 5.16,-1.26 9,-6.45 9,-12V5l-9,-4z"),
    ;

    val path: Path by lazy { PathParser().parsePathString(d).toPath() }
}

private sealed interface Trail {
    data object None : Trail
    data object Chevron : Trail
    data class Switch(val on: Boolean) : Trail
    data class Radio(val on: Boolean) : Trail
}

private data class SRow(
    val title: String,
    val summary: String? = null,
    val glyph: Glyph? = null,
    val brand: Boolean = false,
    val trail: Trail = Trail.Chevron,
    /** A section heading inside the card (e.g. "Downloaded apps"). */
    val label: Boolean = false,
)

private sealed interface Shot { val caption: String }

private data class Page(
    val title: String,
    val rows: List<SRow>,
    /** Row the finger taps; -1 = the ⋮ menu in the top bar. */
    val tap: Int,
    override val caption: String,
    val main: Boolean = false,
    val appHeader: Boolean = false,
    val dots: Boolean = false,
    /** Rows to scroll before tapping (the target is further down the real screen). */
    val scrollRows: Float = 0f,
    /** False when Android asks first (the accessibility switch only turns on after "Allow"). */
    val flipsOnTap: Boolean = true,
) : Shot

private data class DialogShot(
    val behind: Page,
    val title: String,
    val body: String,
    val buttons: List<String>,
    val stacked: Boolean,
    val tap: Int,
    override val caption: String,
    /** Row of [behind] whose switch shows on once the dialog is accepted. */
    val turnsOn: Int? = null,
) : Shot

private data class MenuShot(val behind: Page, val items: List<String>, val tap: Int, override val caption: String) : Shot

private val settingsHome = Page(
    "Settings",
    listOf(
        SRow("Network & internet", "Wi-Fi, mobile data", Glyph.NETWORK, trail = Trail.None),
        SRow("Connected devices", "Bluetooth, pairing", Glyph.DEVICES, trail = Trail.None),
        SRow("Apps", "Default apps, permissions", Glyph.APPS, trail = Trail.None),
        SRow("Notifications", "History, conversations", Glyph.NOTIFICATIONS, trail = Trail.None),
        SRow("Battery", "86%", Glyph.BATTERY, trail = Trail.None),
        SRow("Sound & vibration", "Volume, haptics", Glyph.SOUND, trail = Trail.None),
        SRow("Display", "Dark theme, font size", Glyph.DISPLAY, trail = Trail.None),
        SRow("Accessibility", "Display, interaction, audio", Glyph.ACCESSIBILITY, trail = Trail.None),
        SRow("Security & privacy", "App security, device lock", Glyph.SECURITY, trail = Trail.None),
    ),
    tap = 7, main = true, scrollRows = 4.2f,
    caption = "Open Settings and scroll to Accessibility (Xiaomi: Additional settings → Accessibility)",
)

private fun appInfo(dots: Boolean, tap: Int, caption: String) = Page(
    "App info",
    listOf(
        SRow("Notifications", "Allowed"),
        SRow("Permissions", "No permissions requested"),
        SRow("Storage & cache", "24 MB used"),
        SRow("Battery", "Optimized"),
    ),
    tap = tap, caption = caption, appHeader = true, dots = dots,
)

private val servicePage = Page(
    "ScrollBreak",
    listOf(
        SRow("Use ScrollBreak", trail = Trail.Switch(false)),
        SRow("ScrollBreak shortcut", trail = Trail.Switch(false)),
        SRow("Settings"),
    ),
    tap = 0, flipsOnTap = false, caption = "Turn on Use ScrollBreak",
)

/** The route through Android's own settings for each setup step, worded the way most phones label it. */
private fun shotsFor(stepId: String): List<Shot> = when (stepId) {
    "service" -> listOf(
        settingsHome,
        Page(
            "Accessibility",
            listOf(
                SRow("Display size and text"),
                SRow("Color and motion"),
                SRow("Extra dim", trail = Trail.Switch(false)),
                SRow("Downloaded apps", label = true),
                SRow("ScrollBreak", "Off", brand = true),
            ),
            tap = 4, caption = "Under Downloaded apps (Installed apps on Samsung), tap ScrollBreak",
        ),
        servicePage,
        DialogShot(
            servicePage,
            "Allow ScrollBreak to have full control of your device?",
            "It can view the screen and act on your behalf. ScrollBreak only uses this to spot short-video feeds.",
            listOf("Allow", "Deny"), stacked = true, tap = 0, turnsOn = 0,
            caption = "Tap Allow. Some phones make you wait about 10 seconds first",
        ),
    )
    "restricted" -> {
        val info = appInfo(dots = true, tap = -1, caption = "Settings → Apps → ScrollBreak, then tap ⋮ in the top corner")
        listOf(
            info,
            MenuShot(info, listOf("Allow restricted settings"), tap = 0, caption = "Tap Allow restricted settings and confirm with your PIN"),
        )
    }
    "notifications" -> listOf(
        appInfo(dots = false, tap = 0, caption = "Settings → Apps → ScrollBreak → Notifications"),
        Page(
            "Notifications",
            listOf(
                SRow("All ScrollBreak notifications", trail = Trail.Switch(false)),
                SRow("Categories", label = true),
                SRow("Protection alerts", "When ScrollBreak is switched off", trail = Trail.Switch(true)),
            ),
            tap = 0, caption = "Turn on All ScrollBreak notifications",
        ),
    )
    "battery" -> listOf(
        DialogShot(
            appInfo(dots = false, tap = 0, caption = ""),
            "Let app always run in background?",
            "Allowing ScrollBreak to always run in the background may reduce battery life.",
            listOf("Deny", "Allow"), stacked = false, tap = 1,
            caption = "When Android asks, tap Allow",
        ),
        Page(
            "App battery usage",
            listOf(
                SRow("Unrestricted", "No limits in the background", trail = Trail.Radio(false)),
                SRow("Optimized", "Based on your usage", trail = Trail.Radio(true)),
                SRow("Restricted", "Limited in the background", trail = Trail.Radio(false)),
            ),
            tap = 0, caption = "If a list opens instead, pick Unrestricted (Xiaomi: Battery saver → No restrictions)",
        ),
    )
    "oem" -> listOf(
        Page(
            "Autostart",
            listOf(
                SRow("ScrollBreak", brand = true, trail = Trail.Switch(false)),
                SRow("Gallery", glyph = Glyph.DISPLAY, trail = Trail.Switch(true)),
                SRow("Music", glyph = Glyph.SOUND, trail = Trail.Switch(false)),
            ),
            tap = 0, caption = "Settings → Apps → Permissions → Autostart, then turn on ScrollBreak",
        ),
        Page(
            "Battery saver",
            listOf(
                SRow("No restrictions", "Don't limit background activity", trail = Trail.Radio(false)),
                SRow("Battery saver (recommended)", trail = Trail.Radio(true)),
                SRow("Restrict background apps", trail = Trail.Radio(false)),
            ),
            tap = 0, caption = "Then App info → Battery saver → No restrictions",
        ),
    )
    else -> emptyList()
}

/** Where things are on the mock screen, read live so slides and scrolling are always accounted for. */
private class Targets {
    var root: LayoutCoordinates? = null
    /** The element the finger presses. */
    var point: LayoutCoordinates? = null
    /** The row the ripple fills. */
    var area: LayoutCoordinates? = null

    fun rect(c: LayoutCoordinates?): Rect? {
        val r = root ?: return null
        if (c == null || !c.isAttached || !r.isAttached) return null
        return r.localBoundingBoxOf(c, clipBounds = false)
    }
}

private fun Modifier.reportPoint(t: Targets, on: Boolean, alsoArea: Boolean = false) =
    if (on) onGloballyPositioned { t.point = it; if (alsoArea) t.area = it } else this

private val ROW_H = 60.dp

/**
 * A looping, phone-shaped walkthrough of the settings screens for one setup step. A finger
 * scrolls the list, glides along a curve to the right control, presses it with a ripple, and the
 * switch or choice springs over; screens slide like real Android navigation and dialogs pop in.
 */
@Composable
fun SetupWalkthrough(stepId: String, modifier: Modifier = Modifier) {
    val shots = remember(stepId) { shotsFor(stepId) }
    if (shots.isEmpty()) return
    val targets = remember { Targets() }
    val rowPx = with(LocalDensity.current) { ROW_H.toPx() }

    var index by remember(stepId) { mutableIntStateOf(0) }
    var flipped by remember { mutableStateOf(false) }
    var scroll by remember { mutableFloatStateOf(0f) }
    var fx by remember { mutableFloatStateOf(0f) }
    var fy by remember { mutableFloatStateOf(0f) }
    var fa by remember { mutableFloatStateOf(0f) }
    var press by remember { mutableFloatStateOf(0f) }
    var pulse by remember { mutableFloatStateOf(0f) }
    var ripple by remember { mutableFloatStateOf(0f) }
    var done by remember { mutableStateOf(false) }

    LaunchedEffect(stepId) {
        suspend fun glide(tx: Float, ty: Float, ms: Int) {
            // A gentle arc, like a thumb moving across the screen, not a straight robotic line.
            val sx = fx
            val sy = fy
            val cx = (sx + tx) / 2 + (ty - sy) * 0.22f
            val cy = (sy + ty) / 2 - (tx - sx) * 0.22f
            animate(0f, 1f, animationSpec = tween(ms, easing = FastOutSlowInEasing)) { u, _ ->
                val a = 1 - u
                fx = a * a * sx + 2 * a * u * cx + u * u * tx
                fy = a * a * sy + 2 * a * u * cy + u * u * ty
            }
        }

        while (true) {
            for (i in shots.indices) {
                targets.point = null
                targets.area = null
                flipped = false
                ripple = 0f
                pulse = 0f
                scroll = 0f
                index = i
                delay(if (i == 0) 650L else 480L)
                val shot = shots[i]
                val size = targets.root?.size ?: continue
                val w = size.width.toFloat()
                val h = size.height.toFloat()

                if (shot is Page && shot.scrollRows > 0f) {
                    // Touch down low, drag the list up, let go, and the list coasts to a stop.
                    val sx = w * 0.62f
                    val sy = h * 0.86f
                    if (fa < 0.5f) {
                        fx = sx; fy = sy
                        animate(0f, 1f, animationSpec = tween(220)) { v, _ -> fa = v }
                    } else glide(sx, sy, 450)
                    animate(0f, 0.5f, animationSpec = tween(120)) { v, _ -> press = v }
                    val dist = shot.scrollRows * rowPx
                    val drag = minOf(dist, h * 0.42f)
                    val y0 = fy
                    animate(0f, drag, animationSpec = tween(430, easing = FastOutSlowInEasing)) { v, _ ->
                        scroll = v
                        fy = y0 - v
                    }
                    coroutineScope {
                        launch { animate(press, 0f, animationSpec = tween(140)) { v, _ -> press = v } }
                        if (dist > drag) {
                            animate(drag, dist, animationSpec = tween(650, easing = LinearOutSlowInEasing)) { v, _ -> scroll = v }
                        }
                    }
                    delay(150)
                }

                var rect: Rect? = null
                repeat(25) {
                    if (rect == null) {
                        rect = targets.rect(targets.point)
                        if (rect == null) delay(16)
                    }
                }
                val r = rect ?: continue
                if (fa < 0.5f) {
                    fx = w * 0.84f
                    fy = h * 1.04f
                    launch { animate(0f, 1f, animationSpec = tween(260)) { v, _ -> fa = v } }
                }
                glide(r.center.x, r.center.y, 700)
                delay(80)
                animate(0f, 1f, animationSpec = tween(110)) { v, _ -> press = v }
                flipped = true
                launch { animate(0f, 1f, animationSpec = tween(600, easing = FastOutSlowInEasing)) { v, _ -> ripple = v } }
                launch { animate(0f, 1f, animationSpec = tween(520)) { v, _ -> pulse = v } }
                animate(1f, 0f, animationSpec = tween(220)) { v, _ -> press = v }
                delay(1050)
            }
            animate(fa, 0f, animationSpec = tween(260)) { v, _ -> fa = v }
            done = true
            delay(1700)
            done = false
            delay(250)
        }
    }

    Column(
        modifier.semantics { contentDescription = shots.joinToString(". ") { it.caption } },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val phoneW = if (maxWidth < 290.dp) maxWidth else 290.dp
            Column(
                Modifier.width(phoneW).aspectRatio(0.56f)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Os.bg)
                    .border(3.dp, Os.frame, RoundedCornerShape(32.dp))
                    .padding(3.dp),
            ) {
                StatusBar()
                Box(Modifier.fillMaxWidth().weight(1f).clipToBounds().onGloballyPositioned { targets.root = it }) {
                    AnimatedContent(
                        targetState = index,
                        transitionSpec = {
                            val to = shots[targetState]
                            when {
                                // The page behind a dialog or menu is the same screen, so a short crossfade is
                                // invisible except for the popup, which animates in on its own.
                                (to is DialogShot || to is MenuShot) && targetState > initialState ->
                                    fadeIn(tween(120)) togetherWith fadeOut(tween(120))
                                targetState < initialState -> fadeIn(tween(350)) togetherWith fadeOut(tween(350))
                                else -> slideInHorizontally(tween(400, easing = FastOutSlowInEasing)) { it } togetherWith
                                    (slideOutHorizontally(tween(400, easing = FastOutSlowInEasing)) { -it / 4 } + fadeOut(tween(400)))
                            }
                        },
                        label = "screen",
                    ) { i ->
                        val active = i == index
                        val shot = shots[i]
                        // A screen that has slid out keeps its final look (switch on, list scrolled).
                        ShotView(
                            shot = shot,
                            active = active,
                            flipped = if (active) flipped else true,
                            scroll = if (active) scroll else ((shot as? Page)?.scrollRows ?: 0f) * rowPx,
                            targets = targets,
                        )
                    }

                    Canvas(Modifier.fillMaxSize()) {
                        val area = targets.rect(targets.area)
                        val pt = targets.rect(targets.point)
                        if (ripple > 0f && ripple < 1f && area != null && pt != null) {
                            clipRect(area.left, area.top, area.right, area.bottom) {
                                drawRect(Color.White.copy(alpha = sin(PI * ripple).toFloat() * 0.12f), area.topLeft, area.size)
                                drawCircle(
                                    Color.White.copy(alpha = 0.24f * (1 - ripple)),
                                    radius = hypot(area.width, area.height) * ripple,
                                    center = pt.center,
                                )
                            }
                        }
                        if (fa > 0f) {
                            val c = Offset(fx, fy)
                            val s = 1f - 0.2f * press
                            val r = 15.dp.toPx() * s
                            drawCircle(Color.Black.copy(alpha = 0.35f * fa), r * 1.15f, c + Offset(0f, 3.dp.toPx()))
                            if (pulse > 0f && pulse < 1f) {
                                drawCircle(
                                    Color.White.copy(alpha = 0.55f * (1 - pulse) * fa),
                                    radius = r + 20.dp.toPx() * pulse, center = c, style = Stroke(2.dp.toPx()),
                                )
                            }
                            drawCircle(Color.White.copy(alpha = (0.32f + 0.3f * press) * fa), r, c)
                            drawCircle(Color.White.copy(alpha = 0.92f * fa), r, c, style = Stroke(2.dp.toPx()))
                        }
                    }

                    DoneBadge(done, Modifier.align(Alignment.Center))
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            shots.indices.forEach { i ->
                val w by animateDpAsState(if (i == index) 20.dp else 6.dp, tween(300), label = "dot")
                Box(
                    Modifier.size(w, 6.dp).clip(CircleShape)
                        .background(if (i == index) SbAccent else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                )
            }
        }
        AnimatedContent(
            targetState = index,
            transitionSpec = { (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 3 }) togetherWith fadeOut(tween(150)) },
            label = "caption",
        ) { i ->
            Text(
                shots[i].caption, Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ShotView(shot: Shot, active: Boolean, flipped: Boolean, scroll: Float, targets: Targets) {
    when (shot) {
        is Page -> PageView(shot, active, flipped, scroll, forceOn = null, targets)
        is DialogShot -> Box(Modifier.fillMaxSize()) {
            PageView(shot.behind, active = false, flipped = false, scroll = 0f, forceOn = if (flipped) shot.turnsOn else null, targets)
            DialogView(shot, active, flipped, targets)
        }
        is MenuShot -> Box(Modifier.fillMaxSize()) {
            PageView(shot.behind, active = false, flipped = false, scroll = 0f, forceOn = null, targets)
            MenuView(shot, active, flipped, targets)
        }
    }
}

@Composable
private fun PageView(page: Page, active: Boolean, flipped: Boolean, scroll: Float, forceOn: Int?, targets: Targets) {
    Column(
        Modifier.fillMaxWidth()
            .wrapContentHeight(Alignment.Top, unbounded = true)
            .offset { IntOffset(0, -scroll.roundToInt()) }
            .background(Os.bg)
            .padding(horizontal = 10.dp),
    ) {
        if (page.main) {
            Text(
                page.title, Modifier.padding(start = 8.dp, top = 22.dp, bottom = 16.dp),
                color = Os.text, fontSize = 28.sp, fontWeight = FontWeight.Normal,
            )
            SearchPill()
            Spacer(Modifier.height(12.dp))
        } else {
            TopBar(page, active, targets)
        }
        if (page.appHeader) AppHeader()
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Os.card)) {
            page.rows.forEachIndexed { i, row ->
                RowView(page, i, row, active, flipped, forceOn, targets)
                val next = page.rows.getOrNull(i + 1)
                if (next != null && !row.label && !next.label) {
                    Box(Modifier.padding(start = if (row.glyph != null || row.brand) 58.dp else 16.dp).fillMaxWidth().height(1.dp).background(Os.divider))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TopBar(page: Page, active: Boolean, targets: Targets) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("←", color = Os.text, fontSize = 20.sp)
        Text(page.title, Modifier.weight(1f).padding(start = 18.dp), color = Os.text, fontSize = 19.sp, maxLines = 1)
        if (page.dots) {
            Box(
                Modifier.size(40.dp).reportPoint(targets, active && page.tap == -1, alsoArea = true),
                contentAlignment = Alignment.Center,
            ) { Text("⋮", color = Os.text, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun SearchPill() {
    Row(
        Modifier.fillMaxWidth().height(46.dp).clip(CircleShape).background(Os.card).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(16.dp)) {
            val s = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(Os.sub, size.width * 0.34f, Offset(size.width * 0.42f, size.height * 0.42f), style = s)
            drawLine(Os.sub, Offset(size.width * 0.66f, size.height * 0.66f), Offset(size.width * 0.95f, size.height * 0.95f), s.width, StrokeCap.Round)
        }
        Text("Search Settings", Modifier.padding(start = 12.dp), color = Os.sub, fontSize = 14.sp)
    }
}

@Composable
private fun AppHeader() {
    Column(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BrandTile(54.dp)
        Text("ScrollBreak", color = Os.text, fontSize = 18.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Uninstall", "Force stop").forEach {
                Text(
                    it, Modifier.clip(CircleShape).border(1.dp, Os.frame, CircleShape).padding(horizontal = 16.dp, vertical = 7.dp),
                    color = Os.blueText, fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun BrandTile(size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.27f)).background(Color.Black).border(1.dp, Os.frame, RoundedCornerShape(size * 0.27f)),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_brand_mark), null, Modifier.size(width = size * 0.55f, height = size * 0.66f))
    }
}

@Composable
private fun RowView(page: Page, i: Int, row: SRow, active: Boolean, flipped: Boolean, forceOn: Int?, targets: Targets) {
    val isTap = active && i == page.tap
    if (row.label) {
        Text(
            row.title, Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, bottom = 6.dp),
            color = Os.blueText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        )
        return
    }
    Row(
        Modifier.fillMaxWidth().height(ROW_H)
            .then(if (isTap) Modifier.onGloballyPositioned { targets.area = it } else Modifier)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val trail = row.trail
        if (trail is Trail.Radio) {
            val tapped = page.rows.getOrNull(page.tap)?.trail is Trail.Radio
            val on = if (flipped && tapped) i == page.tap else trail.on
            RadioView(on, Modifier.reportPoint(targets, isTap))
            Spacer(Modifier.width(14.dp))
        }
        when {
            row.brand -> { BrandTile(32.dp); Spacer(Modifier.width(14.dp)) }
            row.glyph != null -> { GlyphIcon(row.glyph); Spacer(Modifier.width(14.dp)) }
        }
        Column(Modifier.weight(1f)) {
            val pointOnTitle = isTap && trail !is Trail.Switch && trail !is Trail.Radio
            Text(
                row.title, Modifier.reportPoint(targets, pointOnTitle),
                color = Os.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (row.summary != null) {
                val summary = if (row.brand && row.summary == "Off" && forceOn != null) "On" else row.summary
                Text(summary, color = Os.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        when (trail) {
            Trail.Chevron -> Text("›", color = Os.sub, fontSize = 20.sp)
            is Trail.Switch -> {
                val on = forceOn == i || (if (flipped && i == page.tap && page.flipsOnTap) !trail.on else trail.on)
                SwitchView(on, Modifier.reportPoint(targets, isTap))
            }
            else -> {}
        }
    }
}

@Composable
private fun GlyphIcon(glyph: Glyph) {
    Canvas(Modifier.size(32.dp)) {
        drawCircle(glyph.color)
        val inset = size.width * 0.22f
        val k = (size.width - 2 * inset) / 24f
        withTransform({ translate(inset, inset); scale(k, k, Offset.Zero) }) { drawPath(glyph.path, Color.White) }
    }
}

@Composable
private fun SwitchView(on: Boolean, modifier: Modifier) {
    val t by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.62f, stiffness = 420f), label = "switch")
    val thumb = (16f + 6f * t).dp
    Box(
        modifier.size(46.dp, 28.dp).clip(CircleShape)
            .background(lerp(Os.offTrack, Os.blue, t.coerceIn(0f, 1f))),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier.offset(x = 3.dp + (46.dp - 6.dp - thumb) * t)
                .size(thumb).clip(CircleShape)
                .background(lerp(Os.offThumb, Color.White, t.coerceIn(0f, 1f))),
        )
    }
}

@Composable
private fun RadioView(on: Boolean, modifier: Modifier) {
    val t by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "radio")
    Box(
        modifier.size(22.dp).clip(CircleShape).border(2.dp, lerp(Os.sub, Os.blue, t.coerceIn(0f, 1f)), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Box(Modifier.size((11f * t).coerceAtLeast(0f).dp).clip(CircleShape).background(Os.blue)) }
}

@Composable
private fun DialogView(shot: DialogShot, active: Boolean, flipped: Boolean, targets: Targets) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 300f)) }
    val gone by animateFloatAsState(if (flipped) 1f else 0f, tween(260, delayMillis = 180), label = "gone")
    val vis = (enter.value * (1 - gone)).coerceIn(0f, 1f)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f * vis))) {
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 16.dp)
                .graphicsLayer {
                    alpha = vis
                    val s = 0.86f + 0.14f * enter.value - 0.06f * gone
                    scaleX = s
                    scaleY = s
                }
                .clip(RoundedCornerShape(26.dp)).background(Os.raised).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (shot.stacked) Box(Modifier.align(Alignment.CenterHorizontally)) { BrandTile(36.dp) }
            Text(shot.title, color = Os.text, fontSize = 16.sp, lineHeight = 21.sp)
            Text(shot.body, color = Os.sub, fontSize = 12.sp, lineHeight = 16.sp)
            if (shot.stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    shot.buttons.forEachIndexed { i, b ->
                        Box(
                            Modifier.fillMaxWidth().height(40.dp).clip(CircleShape)
                                .background(if (i == 0) Os.blue else Os.offTrack)
                                .reportPoint(targets, active && i == shot.tap, alsoArea = true),
                            contentAlignment = Alignment.Center,
                        ) { Text(b, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    shot.buttons.forEachIndexed { i, b ->
                        Box(
                            Modifier.clip(CircleShape).reportPoint(targets, active && i == shot.tap, alsoArea = true)
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                        ) { Text(b, color = Os.blueText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuView(shot: MenuShot, active: Boolean, flipped: Boolean, targets: Targets) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 380f)) }
    val gone by animateFloatAsState(if (flipped) 1f else 0f, tween(220, delayMillis = 220), label = "gone")
    val vis = (enter.value * (1 - gone)).coerceIn(0f, 1f)
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp)
                .graphicsLayer {
                    alpha = vis
                    transformOrigin = TransformOrigin(1f, 0f)
                    scaleX = 0.7f + 0.3f * enter.value
                    scaleY = 0.7f + 0.3f * enter.value
                }
                .width(210.dp).clip(RoundedCornerShape(16.dp)).background(Os.raised).padding(vertical = 6.dp),
        ) {
            listOf("Uninstall updates").plus(shot.items).forEachIndexed { i, item ->
                val isItem = i - 1 == shot.tap
                Text(
                    item,
                    Modifier.fillMaxWidth().reportPoint(targets, active && isItem, alsoArea = true)
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    color = if (i == 0) Os.sub else Os.text, fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun StatusBar() {
    Row(Modifier.fillMaxWidth().height(26.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("9:41", color = Os.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Canvas(Modifier.size(width = 34.dp, height = 11.dp)) {
            // Wi-Fi wedge and a battery, enough to read as a phone's status bar.
            val wifi = Path().apply {
                moveTo(size.height * 0.75f, size.height)
                lineTo(0f, size.height * 0.25f)
                quadraticTo(size.height * 0.75f, -size.height * 0.2f, size.height * 1.5f, size.height * 0.25f)
                close()
            }
            drawPath(wifi, Os.text)
            val bx = size.width - 18.dp.toPx()
            drawRoundRect(
                Os.text, Offset(bx, 1.dp.toPx()), androidx.compose.ui.geometry.Size(16.dp.toPx(), size.height - 2.dp.toPx()),
                androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
            )
        }
    }
}

@Composable
private fun DoneBadge(done: Boolean, modifier: Modifier) {
    val s by animateFloatAsState(if (done) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 320f), label = "done")
    if (s <= 0.01f) return
    Column(modifier.graphicsLayer { scaleX = s; scaleY = s; alpha = s.coerceIn(0f, 1f) }, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(76.dp)) {
            drawCircle(Color(0xFF34A853))
            val p = Path().apply {
                moveTo(size.width * 0.29f, size.height * 0.52f)
                lineTo(size.width * 0.44f, size.height * 0.66f)
                lineTo(size.width * 0.72f, size.height * 0.37f)
            }
            drawPath(p, Color.White, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Text("Done", Modifier.padding(top = 10.dp), color = Os.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
