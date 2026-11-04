package com.assem.scrollbreak.core

import java.util.Calendar
import java.util.TimeZone

/** Counts blocks from the lines of blocks.csv (`epochMs,app,reason[,latencyMs]`). */
object BlockStats {
    data class Summary(
        val today: Int,
        val last7Days: Int,
        val todayByApp: Map<String, Int>,
    ) {
        companion object {
            val EMPTY = Summary(0, 0, emptyMap())
        }
    }

    fun summarize(lines: List<String>, nowMs: Long, zone: TimeZone = TimeZone.getDefault()): Summary {
        val startOfToday = startOfDay(nowMs, zone)
        val weekStart = startOfToday - 6 * DAY_MS   // today plus the six days before it
        var today = 0
        var week = 0
        val byApp = LinkedHashMap<String, Int>()
        for (line in lines) {
            val parts = line.split(',')
            val at = parts.getOrNull(0)?.toLongOrNull() ?: continue
            val app = parts.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: continue
            if (at < weekStart || at > nowMs) continue
            week++
            if (at >= startOfToday) {
                today++
                byApp[app] = (byApp[app] ?: 0) + 1
            }
        }
        return Summary(today, week, byApp.toList().sortedByDescending { it.second }.toMap(LinkedHashMap()))
    }

    private fun startOfDay(ms: Long, zone: TimeZone): Long = Calendar.getInstance(zone).run {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        timeInMillis
    }

    private const val DAY_MS = 24L * 60 * 60 * 1000
}
