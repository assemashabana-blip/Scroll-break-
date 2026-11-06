package com.assem.scrollbreak.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.assem.scrollbreak.core.LatencyStats
import com.assem.scrollbreak.ui.theme.SbAccent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

/** Card with a small title above a chart. */
@Composable
private fun ChartCard(title: String, trailing: String? = null, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (trailing != null) {
                    Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

/**
 * Blocks per day for the last 7 days: one series, so one ink; today is picked out in the accent.
 * Only the busiest day and today carry a number; tapping any bar shows its count.
 */
@Composable
fun WeekChart(perDay: List<Int>, nowMs: Long = System.currentTimeMillis()) {
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = muted)
    val valueStyle = MaterialTheme.typography.labelMedium.copy(color = ink, fontWeight = FontWeight.Bold)
    val dayNames = remember(nowMs) {
        val fmt = SimpleDateFormat("EEE", Locale.ENGLISH)
        perDay.indices.map { i ->
            Calendar.getInstance().apply { timeInMillis = nowMs; add(Calendar.DAY_OF_MONTH, i - perDay.lastIndex) }
                .let { fmt.format(it.time) }
        }
    }
    var picked by remember { mutableStateOf<Int?>(null) }
    val grow by animateFloatAsState(1f, tween(600), label = "grow")
    val top = max(perDay.maxOrNull() ?: 0, 1)
    val busiest = perDay.indexOf(perDay.maxOrNull() ?: 0)

    ChartCard("Last 7 days", "${perDay.sum()} blocked") {
        Canvas(
            Modifier.fillMaxWidth().height(150.dp)
                .semantics { contentDescription = "Blocks per day: " + dayNames.zip(perDay).joinToString { "${it.first} ${it.second}" } }
                .pointerInput(perDay) {
                    detectTapGestures { p ->
                        val slot = size.width / perDay.size
                        picked = (p.x / slot).toInt().coerceIn(0, perDay.lastIndex).takeIf { it != picked }
                    }
                },
        ) {
            val labelH = 18.dp.toPx()
            val valueH = 18.dp.toPx()
            val base = size.height - labelH
            val plotH = base - valueH
            val slot = size.width / perDay.size
            val barW = minOf(22.dp.toPx(), slot * 0.5f)
            drawLine(grid, Offset(0f, base), Offset(size.width, base), strokeWidth = 1.dp.toPx())
            perDay.forEachIndexed { i, n ->
                val cx = slot * i + slot / 2
                val h = if (n == 0) 0f else max(plotH * n / top * grow, 4.dp.toPx())
                val today = i == perDay.lastIndex
                val color = when {
                    today -> SbAccent
                    picked != null && picked != i -> ink.copy(alpha = 0.35f)
                    else -> ink
                }
                if (h > 0f) {
                    // Rounded data end, square where the bar meets the baseline.
                    val r = minOf(4.dp.toPx(), barW / 2, h / 2)
                    drawRoundRect(color, Offset(cx - barW / 2, base - h), Size(barW, h), CornerRadius(r, r))
                    drawRect(color, Offset(cx - barW / 2, base - r), Size(barW, r))
                }
                if (n > 0 && (i == picked || (picked == null && (today || i == busiest)))) {
                    val t = measurer.measure(n.toString(), valueStyle)
                    drawText(t, topLeft = Offset(cx - t.size.width / 2f, base - h - t.size.height - 2.dp.toPx()))
                }
                val d = measurer.measure(if (today) "Today" else dayNames[i], labelStyle.copy(color = if (today) ink else muted))
                drawText(d, topLeft = Offset(cx - d.size.width / 2f, base + 4.dp.toPx()))
            }
        }
    }
}

/** Blocks per app this week, largest first: glyph + name + bar + count in one row per app. */
@Composable
fun AppsChart(weekByApp: Map<String, Int>) {
    val ink = MaterialTheme.colorScheme.onSurface
    val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
    val top = max(weekByApp.values.maxOrNull() ?: 0, 1)
    ChartCard("Blocked by app", "7 days") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            weekByApp.entries.take(6).forEach { (app, n) ->
                val fraction by animateFloatAsState(n.toFloat() / top, tween(600), label = "bar")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val glyph = appGlyph(app)
                    Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                        if (glyph != null) Icon(painterResource(glyph), null, Modifier.size(20.dp), tint = ink)
                    }
                    Text(appDisplayName(app), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(84.dp))
                    Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(track)) {
                        Box(Modifier.fillMaxWidth(fraction).height(10.dp).clip(RoundedCornerShape(5.dp)).background(ink))
                    }
                    Text(
                        n.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(36.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    )
                }
            }
        }
    }
}

/**
 * Reaction time as a bullet chart: the median bar against the 300 ms target line, with a tick
 * where 95% of blocks fall under. Replaces the old line of numbers.
 */
@Composable
fun ReactionChart(latency: LatencyStats.Summary, targetMs: Long = 300) {
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
    val measurer = rememberTextMeasurer()
    val small = MaterialTheme.typography.labelSmall.copy(color = muted)
    val strong = MaterialTheme.typography.labelMedium.copy(color = ink, fontWeight = FontWeight.Bold)
    // Scale to the 95% mark (plus room), at least 2x the target; the slowest outlier would squash it.
    val scaleMax = max(targetMs * 2, (latency.p95Ms * 1.15).toLong()).toFloat()
    val grow by animateFloatAsState(1f, tween(600), label = "grow")

    ChartCard("Reaction time", "${latency.count} blocks") {
        Canvas(
            Modifier.fillMaxWidth().height(76.dp).semantics {
                contentDescription = "Median ${latency.medianMs} ms, 95% under ${latency.p95Ms} ms, target $targetMs ms"
            },
        ) {
            val top = 22.dp.toPx()
            val barH = 14.dp.toPx()
            val w = size.width
            fun x(ms: Long) = (ms / scaleMax).coerceIn(0f, 1f) * w
            // Track, with the part under the target shaded a little darker.
            drawRoundRect(track, Offset(0f, top), Size(w, barH), CornerRadius(barH / 2))
            drawRoundRect(track, Offset(0f, top), Size(x(targetMs), barH), CornerRadius(barH / 2))
            // Median bar.
            val medianW = max(x(latency.medianMs) * grow, barH)
            drawRoundRect(if (latency.medianMs <= targetMs) ink else SbAccent, Offset(0f, top), Size(medianW, barH), CornerRadius(barH / 2))
            // 95% tick.
            val p = x(latency.p95Ms)
            drawLine(ink, Offset(p, top - 4.dp.toPx()), Offset(p, top + barH + 4.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
            // Target line, dashed.
            val t = x(targetMs)
            drawLine(
                muted, Offset(t, top - 8.dp.toPx()), Offset(t, top + barH + 8.dp.toPx()), 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
            val medianLabel = measurer.measure("${latency.medianMs} ms median", strong)
            drawText(medianLabel, topLeft = Offset(0f, 0f))
            val targetLabel = measurer.measure("target $targetMs", small)
            val tx = (t - targetLabel.size.width / 2f).coerceIn(medianLabel.size.width + 8.dp.toPx(), w - targetLabel.size.width)
            drawText(targetLabel, topLeft = Offset(tx, 2.dp.toPx()))
            val pLabel = measurer.measure("95% under ${latency.p95Ms}", small)
            drawText(pLabel, topLeft = Offset((p - pLabel.size.width / 2f).coerceIn(0f, w - pLabel.size.width), top + barH + 10.dp.toPx()))
        }
    }
}
