package com.assem.scrollbreak.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.assem.scrollbreak.AppUsage
import com.assem.scrollbreak.LimitStore
import com.assem.scrollbreak.core.AppLimit
import com.assem.scrollbreak.core.LimitBook
import com.assem.scrollbreak.core.LockTimer
import com.assem.scrollbreak.core.UsageTime
import com.assem.scrollbreak.core.WebSites
import com.assem.scrollbreak.ui.theme.SbAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * Per-app limits: pick any app and give it a daily time allowance or block it for a while. A
 * daily limit can be lowered but not raised or removed until tomorrow; a block cannot be touched
 * until it ends. [apps] is null while the list loads; [usedToday] is each app's time today by package.
 */
@Composable
fun LimitsScreen(
    apps: List<AppUsage.LaunchableApp>?,
    usedToday: Map<String, Long>,
    usageAccess: Boolean,
    onAllowUsage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val book by LimitStore.book.collectAsState()
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(15_000)
        }
    }
    var editing by remember { mutableStateOf<AppUsage.LaunchableApp?>(null) }

    // Apps with a limit first, then the ones used most today, then by name.
    val sorted = remember(apps, book, usedToday, now) {
        apps?.sortedWith(
            compareByDescending<AppUsage.LaunchableApp> { book.isLimited(it.pkg, now) }
                .thenByDescending { usedToday[it.pkg] ?: 0L }
                .thenBy { it.label.lowercase() },
        )
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Limit an app", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Give an app a daily time allowance, or block it for a while. A daily limit cannot be changed or removed until tomorrow, a block cannot be changed until it ends, and while either is locked ScrollBreak cannot be switched off or uninstalled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!usageAccess) {
                        Text(
                            "Daily limits count the time an app is open, which needs Usage access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SbAccent,
                        )
                        OutlinedButton(onClick = onAllowUsage) { Text("Allow Usage access") }
                    }
                }
            }
        }
        if (sorted == null) {
            item { Text("Loading apps...", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
        } else {
            items(sorted, key = { it.pkg }) { app ->
                AppLimitRow(app, book.get(app.pkg), usedToday[app.pkg] ?: 0L, now) { editing = app }
            }
        }
    }

    editing?.let { app ->
        LimitEditor(app, book.get(app.pkg), usageAccess, onClose = { editing = null })
    }
}

@Composable
private fun AppLimitRow(app: AppUsage.LaunchableApp, limit: AppLimit?, used: Long, now: Long, onClick: () -> Unit) {
    val active = limit != null && !limit.isEmpty(now)
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.pkg)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    statusOf(limit, used, now),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (active) SbAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AppIcon(pkg: String) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
    }
    val shape = RoundedCornerShape(10.dp)
    val bitmap = icon
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = Modifier.size(40.dp).clip(shape))
    } else {
        Spacer(Modifier.size(40.dp))
    }
}

private fun statusOf(limit: AppLimit?, used: Long, now: Long): String {
    val usedText = if (used > 0) "${UsageTime.format(used)} today" else "not used today"
    if (limit == null || limit.isEmpty(now)) return "No limit · $usedText"
    if (limit.blockActive(now)) return "Blocked until ${untilText(limit.blockedUntil, now)}"
    val daily = LockTimer.describe(limit.dailyMinutes ?: 0)
    return "$daily a day · $usedText" + if (limit.dailyLocked(now)) " · locked until tomorrow" else ""
}

/** "6:30 PM" for later today, with the date for another day. */
private fun untilText(untilMs: Long, now: Long): String {
    val format = if (untilMs - now < 12 * 3_600_000L) DateFormat.getTimeInstance(DateFormat.SHORT)
    else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    return format.format(Date(untilMs))
}

private enum class LimitMode { DAILY, BLOCK }

@Composable
private fun LimitEditor(app: AppUsage.LaunchableApp, limit: AppLimit?, usageAccess: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val buzz = rememberBuzz()
    val now = System.currentTimeMillis()
    val blocked = limit?.blockActive(now) == true
    val dailyLocked = limit?.dailyLocked(now) == true
    var mode by rememberSaveable { mutableStateOf(if (blocked && !dailyLocked) LimitMode.BLOCK else LimitMode.DAILY) }
    var dailyMinutes by rememberSaveable { mutableIntStateOf(limit?.dailyMinutes ?: 30) }
    var blockMinutes by rememberSaveable { mutableIntStateOf(60) }
    var confirming by remember { mutableStateOf(false) }
    // Each part is editable only while it is not locked.
    val canEdit = if (mode == LimitMode.DAILY) !dailyLocked else !blocked

    fun report(error: String?) {
        if (error != null) Toast.makeText(context, error, Toast.LENGTH_LONG).show() else onClose()
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(app.label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val sites = WebSites.domainsOf(app.pkg)
                if (sites.isNotEmpty()) {
                    Text(
                        "Its website (${sites.joinToString(", ")}) follows the same limit in Chrome. Chrome itself is not limited, and time on the site counts towards the daily limit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(mode == LimitMode.DAILY, { mode = LimitMode.DAILY }, label = { Text("Daily limit") })
                    FilterChip(mode == LimitMode.BLOCK, { mode = LimitMode.BLOCK }, label = { Text("Block for a while") })
                }
                if (mode == LimitMode.DAILY) {
                    if (dailyLocked) {
                        Text(
                            "A daily limit of ${LockTimer.describe(limit!!.dailyMinutes ?: 0)} is set. It is locked until tomorrow and cannot be changed or removed.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        Text(
                            if (limit?.dailyMinutes != null) "The limit is no longer locked. It can be changed or removed now."
                            else "How long this app may be open each day. Once set, it cannot be changed until tomorrow.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        DurationPicker(dailyMinutes, { dailyMinutes = it }, LimitBook.MAX_DAILY_MINUTES)
                        if (!usageAccess) {
                            Text(
                                "Usage access is off, so the time cannot be counted yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SbAccent,
                            )
                        }
                        if (limit?.dailyMinutes != null) {
                            TextButton(onClick = { report(LimitStore.removeDaily(app.pkg)) }) { Text("Remove the daily limit") }
                        }
                    }
                } else {
                    if (blocked) {
                        Text(
                            "Blocked until ${untilText(limit!!.blockedUntil, now)}. A block cannot be changed or removed until it ends.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        Text(
                            "The app cannot be opened until the time is up, and the block cannot be changed meanwhile.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        DurationPicker(blockMinutes, { blockMinutes = it }, LimitBook.MAX_BLOCK_MINUTES)
                    }
                }
            }
        },
        confirmButton = {
            if (canEdit) {
                Button(onClick = { confirming = true }) {
                    Text(if (mode == LimitMode.DAILY) "Set the limit" else "Block")
                }
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(if (canEdit) "Cancel" else "Close") } },
    )

    if (confirming) {
        val daily = mode == LimitMode.DAILY
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = {
                Text(
                    if (daily) "${LockTimer.describe(dailyMinutes)} a day for ${app.label}?"
                    else "Block ${app.label} for ${LockTimer.describe(blockMinutes)}?",
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Text(
                    if (daily) {
                        "You will not be able to raise or remove this until tomorrow, and ScrollBreak cannot be switched off or uninstalled until then."
                    } else {
                        "You will not be able to change or end this block until it is over, and ScrollBreak cannot be switched off or uninstalled until then."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    buzz(Buzz.HEAVY)
                    report(if (daily) LimitStore.setDaily(app.pkg, dailyMinutes) else LimitStore.startBlock(app.pkg, blockMinutes))
                }) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Not now") } },
        )
    }
}

/** The Home card that opens the limits screen. */
@Composable
fun LimitsCard(activeCount: Int, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("App limits", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (activeCount == 0) "Give any app a daily limit, or block it for a while"
                    else "$activeCount ${if (activeCount == 1) "app" else "apps"} limited",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (activeCount == 0) MaterialTheme.colorScheme.onSurfaceVariant else SbAccent,
                )
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
