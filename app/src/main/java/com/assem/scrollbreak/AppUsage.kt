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

    /** Null without access. [appOf] maps a package to a rule's app name. Call off the main thread. */
    fun summarize(context: Context, appOf: (String) -> String?): UsageTime.Summary? {
        if (!hasAccess(context)) return null
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
        return UsageTime.summarize(list, appOf, now)
    }

    /** UsageEvents.Event.SCREEN_NON_INTERACTIVE (API 28); older phones never send it. */
    private const val SCREEN_NON_INTERACTIVE = 16
}
