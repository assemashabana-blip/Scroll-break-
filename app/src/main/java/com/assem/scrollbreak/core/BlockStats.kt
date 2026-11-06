package com.assem.scrollbreak.core

import java.util.Calendar
import java.util.TimeZone

/** Counts blocks from the lines of blocks.csv (`epochMs,app,reason[,latencyMs]`). */
object BlockStats {
    data class Summary(
        val today: Int,
        val last7Days: Int,
        val todayByApp: Map<String, Int>,
        /** Blocks per calendar day, oldest first; the last entry is today. Always 7 entries. */
        val perDay: List<Int> = List(DAYS) { 0 },
        /** Blocks per app over the same 7 days, largest first. */
        val weekByApp: Map<String, Int> = emptyMap(),
    ) {
        companion object {
            val EMPTY = Summary(0, 0, emptyMap())
        }
    }

    fun summarize(lines: List<String>, nowMs: Long, zone: TimeZone = TimeZone.getDefault()): Summary {
        // Day starts from the calendar, not "minus 24 h", so a daylight-saving day still lines up.
        val dayStarts = (DAYS - 1 downTo 0).map { back -> startOfDay(nowMs, zone, back) }
        val startOfToday = dayStarts.last()
        val weekStart = dayStarts.first()   // today plus the six days before it
        var today = 0
        var week = 0
        val byApp = LinkedHashMap<String, Int>()
        val weekApps = LinkedHashMap<String, Int>()
        val perDay = IntArray(DAYS)
        for (line in lines) {
            val parts = line.split(',')
            val at = parts.getOrNull(0)?.toLongOrNull() ?: continue
            val app = parts.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: continue
            if (at < weekStart || at > nowMs) continue
            week++
            weekApps[app] = (weekApps[app] ?: 0) + 1
            perDay[dayStarts.indexOfLast { at >= it }]++
            if (at >= startOfToday) {
                today++
                byApp[app] = (byApp[app] ?: 0) + 1
            }
        }
        return Summary(today, week, byApp.sortedByCount(), perDay.toList(), weekApps.sortedByCount())
    }

    private fun Map<String, Int>.sortedByCount() =
        toList().sortedByDescending { it.second }.toMap(LinkedHashMap())

    /** Midnight [daysBack] calendar days before the day containing [ms]. */
    internal fun startOfDay(ms: Long, zone: TimeZone, daysBack: Int): Long = Calendar.getInstance(zone).run {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_MONTH, -daysBack)
        timeInMillis
    }

    const val DAYS = 7
}
