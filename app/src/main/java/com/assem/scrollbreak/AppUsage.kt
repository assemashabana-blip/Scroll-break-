package com.assem.scrollbreak

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.assem.scrollbreak.core.BlockStats
import com.assem.scrollbreak.core.UsageTime
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.TimeZone

/**
 * Reads how long each watched app was on screen from Android's own usage history. Needs the
 * "Usage access" permission, which only the user can grant in system settings. Nothing leaves
 * the phone.
 */
object AppUsage {
    fun hasAccess(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Opens the Usage access page, on ScrollBreak's own entry where the phone supports it. */
    fun accessIntent(context: Context): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}"))
            .takeIf { it.resolveActivity(context.packageManager) != null }
            ?: Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    /** Time per package, plus each package's name as the phone shows it. */
    class Result(val summary: UsageTime.Summary, val labels: Map<String, String>)

    /**
     * Every app on the home screen except launchers and ScrollBreak itself. Null without
     * access. Call off the main thread.
     */
    fun summarize(context: Context): Result? {
        if (!hasAccess(context)) return null
        val pm = context.packageManager
        val homes = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo.packageName }.toSet()
        val labels = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName !in homes && it.activityInfo.packageName != context.packageName }
            .associate { it.activityInfo.packageName to it.activityInfo.applicationInfo.loadLabel(pm).toString() }
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val from = BlockStats.startOfDay(now, TimeZone.getDefault(), 6)
        val list = ArrayList<UsageTime.Event>()
        val raw = usm.queryEvents(from, now)
        val e = UsageEvents.Event()
        while (raw.hasNextEvent()) {
            raw.getNextEvent(e)
            when (e.eventType) {
                // ACTIVITY_RESUMED / ACTIVITY_PAUSED (same values as the older MOVE_TO_* names).
                1 -> list += UsageTime.Event(e.packageName, true, e.timeStamp)
                2 -> list += UsageTime.Event(e.packageName, false, e.timeStamp)
                SCREEN_NON_INTERACTIVE -> list += UsageTime.Event(null, false, e.timeStamp)
            }
        }
        return Result(UsageTime.summarize(list, { it.takeIf { p -> p in labels } }, now), labels)
            .also { runCatching { save(context, it, from) } }
    }

    /**
     * The last result, saved by [summarize], so Home can show the card on its very first frame
     * while a fresh one is read (that read takes about half a second). A copy from an earlier day
     * is moved along by the days in between, so "today" never shows yesterday's time. Small
     * enough to read on the main thread; null if there is none or it cannot be read.
     */
    fun cached(context: Context): Result? = runCatching {
        val file = cacheFile(context)
        if (!file.exists()) return null
        val json = JSONObject(file.readText())
        val now = System.currentTimeMillis()
        val tz = TimeZone.getDefault()
        val weekStart = BlockStats.startOfDay(now, tz, 6)
        val days = Math.round((weekStart - json.getLong("weekStart")) / 86_400_000.0).toInt()
        if (days < 0) return null // the clock went back; do not guess
        val byDay = json.getJSONArray("perDayByApp").let { a -> List(a.length()) { readMap(a.getJSONObject(it)) } }
        if (byDay.isEmpty()) return null
        val shifted = (byDay.drop(days) + List(minOf(days, byDay.size)) { emptyMap() }).takeLast(byDay.size)
        val week = HashMap<String, Long>()
        shifted.forEach { day -> day.forEach { (k, v) -> week[k] = (week[k] ?: 0) + v } }
        val today = shifted.lastOrNull().orEmpty()
        val summary = UsageTime.Summary(
            todayMs = today.values.sum(),
            todayByApp = today,
            weekByApp = week.entries.sortedByDescending { it.value }.associate { it.key to it.value },
            perDay = shifted.map { it.values.sum() },
            perDayByApp = shifted,
        )
        val names = json.getJSONObject("labels")
        Result(summary, names.keys().asSequence().associateWith { names.getString(it) })
    }.getOrNull()

    private fun save(context: Context, result: Result, weekStart: Long) {
        val s = result.summary
        // Only the names of apps that appear, not every app on the phone.
        val used = s.perDayByApp.flatMap { it.keys }.toSet()
        val json = JSONObject()
            .put("weekStart", weekStart)
            .put("perDayByApp", JSONArray(s.perDayByApp.map { JSONObject(it as Map<*, *>) }))
            .put("labels", JSONObject(result.labels.filterKeys { it in used } as Map<*, *>))
        val file = cacheFile(context)
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.toString())
        tmp.renameTo(file)
    }

    private fun readMap(o: JSONObject): Map<String, Long> =
        o.keys().asSequence().map { it to o.getLong(it) }.sortedByDescending { it.second }.toMap()

    private fun cacheFile(context: Context) = File(context.filesDir, "usage_cache.json")

    /** UsageEvents.Event.SCREEN_NON_INTERACTIVE (API 28); older phones never send it. */
    private const val SCREEN_NON_INTERACTIVE = 16
}
