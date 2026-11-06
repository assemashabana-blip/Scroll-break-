package com.assem.scrollbreak.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.assem.scrollbreak.ui.theme.SbAccent
import com.assem.scrollbreak.ui.theme.SbOn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One row of a mock settings screen. [toggle] null = plain row; [radio] marks a choice row. */
private data class MockRow(val label: String, val toggle: Boolean? = null, val radio: Boolean = false, val chevron: Boolean = false)

/** One screen of a walkthrough: a settings list, an overflow menu, or a confirmation dialog. */
private sealed interface Scene {
    val caption: String

    data class ListScene(
        val title: String,
        val rows: List<MockRow>,
        /** Row the finger taps; -1 = the ⋮ menu in the title bar. */
        val tap: Int,
        override val caption: String,
        val menu: Boolean = false,
    ) : Scene

    data class MenuScene(val title: String, val items: List<String>, val tap: Int, override val caption: String) : Scene

    data class DialogScene(val text: String, val buttons: List<String>, val tap: Int, override val caption: String) : Scene
}

/**
 * The route through Android's settings for each setup step, with the wording most phones use and
 * the common alternatives named (Pixel, Samsung, Xiaomi and others label some screens differently).
 */
private fun scenesFor(stepId: String): List<Scene> = when (stepId) {
    "service" -> listOf(
        Scene.ListScene(
            "Settings",
            listOf(MockRow("Network & internet"), MockRow("Apps"), MockRow("Display"), MockRow("Accessibility", chevron = true)),
            tap = 3, caption = "Open Settings, then Accessibility",
        ),
        Scene.ListScene(
            "Accessibility",
            listOf(MockRow("Display size and text"), MockRow("Color and motion"), MockRow("Installed / Downloaded apps", chevron = true)),
            tap = 2, caption = "Tap Installed apps (Downloaded apps on some phones)",
        ),
        Scene.ListScene(
            "Installed apps",
            listOf(MockRow("ScrollBreak", chevron = true), MockRow("Other app", chevron = true)),
            tap = 0, caption = "Tap ScrollBreak",
        ),
        Scene.ListScene(
            "ScrollBreak",
            listOf(MockRow("Use ScrollBreak", toggle = false), MockRow("Shortcut", toggle = false)),
            tap = 0, caption = "Turn on Use ScrollBreak",
        ),
        Scene.DialogScene(
            "Allow ScrollBreak to have full control of your device?",
            listOf("Deny", "Allow"), tap = 1, caption = "Tap Allow (some phones make you wait a few seconds)",
        ),
    )
    "restricted" -> listOf(
        Scene.ListScene(
            "App info",
            listOf(MockRow("ScrollBreak"), MockRow("Notifications", chevron = true), MockRow("Permissions", chevron = true), MockRow("Battery", chevron = true)),
            tap = -1, menu = true, caption = "Open ScrollBreak's App info and tap ⋮ in the top corner",
        ),
        Scene.MenuScene(
            "App info", listOf("Allow restricted settings"), tap = 0,
            caption = "Tap Allow restricted settings, confirm, then turn the service on",
        ),
    )
    "notifications" -> listOf(
        Scene.ListScene(
            "Notifications",
            listOf(MockRow("All ScrollBreak notifications", toggle = false), MockRow("Alerts", toggle = true)),
            tap = 0, caption = "Turn on All ScrollBreak notifications",
        ),
    )
    "battery" -> listOf(
        Scene.DialogScene(
            "Let app always run in background?", listOf("Deny", "Allow"), tap = 1, caption = "Tap Allow",
        ),
        Scene.ListScene(
            "Battery",
            listOf(MockRow("Unrestricted / No restrictions", radio = true), MockRow("Optimized", radio = true), MockRow("Restricted", radio = true)),
            tap = 0, caption = "If a list opens instead, choose Unrestricted (No restrictions on Xiaomi)",
        ),
    )
    "oem" -> listOf(
        Scene.ListScene(
            "Autostart",
            listOf(MockRow("ScrollBreak", toggle = false), MockRow("Other app", toggle = true)),
            tap = 0, caption = "Allow ScrollBreak to start on its own (Autostart)",
        ),
        Scene.ListScene(
            "Battery saver",
            listOf(MockRow("No restrictions", radio = true), MockRow("Battery saver", radio = true)),
            tap = 0, caption = "Set its battery saver to No restrictions",
        ),
    )
    else -> emptyList()
}

private val HEADER = 44.dp
private val ROW = 40.dp

/**
 * A looping animated walkthrough of the settings screens for one setup step: a finger moves to
 * the right row, taps, and the switch or choice flips. Drawn in Compose, so it is sharp at any size.
 */
@Composable
fun SetupWalkthrough(stepId: String, modifier: Modifier = Modifier) {
    val scenes = remember(stepId) { scenesFor(stepId) }
    if (scenes.isEmpty()) return
    var index by remember(stepId) { mutableIntStateOf(0) }
    // Progress inside a scene: finger travel, press, then hold.
    val finger = remember { Animatable(0f) }
    val press = remember { Animatable(0f) }
    var done by remember { mutableStateOf(false) }

    LaunchedEffect(stepId) {
        while (true) {
            done = false
            finger.snapTo(0f)
            press.snapTo(0f)
            delay(350)
            finger.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
            launch { press.animateTo(1f, tween(180)) }.join()
            done = true
            press.animateTo(0f, tween(260))
            delay(1300)
            index = (index + 1) % scenes.size
        }
    }

    val scene = scenes[index]
    Column(
        modifier.semantics { contentDescription = scenes.joinToString(". ") { it.caption } },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(HEADER + ROW * 5)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(18.dp)),
        ) {
            val width = maxWidth
            AnimatedContent(
                scene, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) }, label = "scene",
            ) { s -> MockScreen(s, if (s == scene) done else false) }

            // Finger: starts low on the right, glides to the target, presses (shrinks + ripple).
            val (tx, ty) = targetOf(scene, width)
            val sx = width * 0.8f
            val sy = HEADER + ROW * 4.6f
            val x = sx + (tx - sx) * finger.value
            val y = sy + (ty - sy) * finger.value
            Box(
                Modifier.offset(x - 22.dp, y - 22.dp).size(44.dp)
                    .scale(1f + press.value * 0.9f)
                    .alpha(press.value * 0.5f)
                    .clip(CircleShape).background(SbAccent),
            )
            Box(
                Modifier.offset(x - 12.dp, y - 12.dp).size(24.dp).scale(1f - press.value * 0.2f)
                    .clip(CircleShape).background(Color.White.copy(alpha = 0.9f))
                    .border(2.dp, SbAccent, CircleShape),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "${index + 1}/${scenes.size}", style = MaterialTheme.typography.labelMedium,
                color = SbAccent, fontWeight = FontWeight.Bold,
            )
            Text(scene.caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Where the finger lands for [scene], in the mock's own coordinates. */
private fun targetOf(scene: Scene, width: Dp): Pair<Dp, Dp> = when (scene) {
    is Scene.ListScene -> when {
        scene.tap < 0 -> width - 22.dp to HEADER / 2
        scene.rows[scene.tap].toggle != null -> width - 34.dp to HEADER + ROW * scene.tap + ROW / 2
        scene.rows[scene.tap].radio -> 26.dp to HEADER + ROW * scene.tap + ROW / 2
        else -> width * 0.45f to HEADER + ROW * scene.tap + ROW / 2
    }
    is Scene.MenuScene -> width - 80.dp to HEADER + 8.dp + 20.dp + 40.dp * scene.tap
    is Scene.DialogScene -> {
        // Buttons sit right-aligned along the dialog's bottom edge.
        val fromRight = (scene.buttons.size - 1 - scene.tap) * 72 + 46
        width - 24.dp - fromRight.dp to HEADER + ROW * 5 - 66.dp
    }
}

@Composable
private fun MockScreen(scene: Scene, done: Boolean) {
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val title = when (scene) {
        is Scene.ListScene -> scene.title
        is Scene.MenuScene -> scene.title
        is Scene.DialogScene -> "Settings"
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(HEADER).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("←", color = muted)
                Text(title, Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if ((scene as? Scene.ListScene)?.menu == true || scene is Scene.MenuScene) Text("⋮", color = ink, fontWeight = FontWeight.Bold)
            }
            val rows = when (scene) {
                is Scene.ListScene -> scene.rows
                is Scene.MenuScene -> listOf(MockRow("ScrollBreak"), MockRow("Notifications"), MockRow("Permissions"))
                is Scene.DialogScene -> listOf(MockRow("Display"), MockRow("Accessibility"), MockRow("Apps"))
            }
            rows.forEachIndexed { i, row ->
                val tapped = scene is Scene.ListScene && scene.tap == i
                MockRowView(row, tapped && done)
            }
        }
        when (scene) {
            is Scene.MenuScene -> Column(
                Modifier.align(Alignment.TopEnd).padding(top = HEADER - 4.dp, end = 10.dp)
                    .clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(vertical = 8.dp),
            ) {
                scene.items.forEach { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 10.dp), style = MaterialTheme.typography.bodySmall, color = ink) }
            }
            is Scene.DialogScene -> Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))) {
                Column(
                    Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(scene.text, style = MaterialTheme.typography.bodyMedium, color = ink)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        scene.buttons.forEachIndexed { i, b ->
                            Text(
                                b, Modifier.width(72.dp), style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold, color = if (i == scene.tap) SbAccent else muted,
                            )
                        }
                    }
                }
            }
            else -> {}
        }
    }
}

@Composable
private fun MockRowView(row: MockRow, flipped: Boolean) {
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // A plain row that was just tapped shows the pressed highlight a real settings list would.
    val pressed = flipped && row.toggle == null && !row.radio
    Row(
        Modifier.fillMaxWidth().height(ROW)
            .background(if (pressed) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (row.radio) {
            val chosen = flipped
            Box(
                Modifier.size(18.dp).clip(CircleShape).border(2.dp, if (chosen) SbOn else muted, CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (chosen) Box(Modifier.size(9.dp).clip(CircleShape).background(SbOn)) }
            Box(Modifier.width(12.dp))
        }
        Text(row.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        when {
            row.toggle != null -> MockSwitch(if (flipped) !row.toggle else row.toggle)
            row.chevron -> Text("›", color = muted)
        }
    }
}

@Composable
private fun MockSwitch(on: Boolean) {
    val knob by androidx.compose.animation.core.animateDpAsState(if (on) 16.dp else 0.dp, tween(200), label = "knob")
    Box(
        Modifier.size(width = 36.dp, height = 20.dp).clip(CircleShape)
            .background(if (on) SbOn else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            .padding(2.dp),
    ) {
        Box(Modifier.offset(x = knob).size(16.dp).clip(CircleShape).background(Color.White))
    }
}
