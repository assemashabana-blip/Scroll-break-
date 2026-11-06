package com.assem.scrollbreak.core

import java.util.TimeZone

/**
 * Foreground time per watched app, worked out from the phone's own "app moved to the
 * foreground / background" events (UsageStatsManager on Android).
 */
object UsageTime {
    /** [pkg] came to the foreground (true) or left it (false) at [at]. A null [pkg] means the screen went off. */
    data class Event(val pkg: String?, val foreground: Boolean, val at: Long)

    data class Summary(
        val todayMs: Long,
        /** Foreground time today per app name, largest first. */
        val todayByApp: Map<String, Long>,
        /** Foreground time over today and the six days before it, per app name, largest first. */
        val weekByApp: Map<String, Long>,
        /** All counted apps together per calendar day, oldest first; the last entry is today. */
        val perDay: List<Long>,
        /** Time per app for each of the same days, largest first; the last entry is today. */
        val perDayByApp: List<Map<String, Long>> = emptyList(),
    )

    /**
     * [events] must be in time order and start at or before the first day of the week.
     * [appOf] maps a package to its app name (a rule's `app`); other packages are not counted
     * but still end the previous app's session when they come to the front.
     */
    fun summarize(
        events: List<Event>,
        appOf: (String) -> String?,
        nowMs: Long,
        zone: TimeZone = TimeZone.getDefault(),
    ): Summary {
        val dayStarts = (DAYS - 1 downTo 0).map { BlockStats.startOfDay(nowMs, zone, it) }
        val weekStart = dayStarts.first()
        val perDay = LongArray(DAYS)
        val days = List(DAYS) { LinkedHashMap<String, Long>() }
        val week = LinkedHashMap<String, Long>()

        // Splits one session across the days it covers.
        fun add(app: String, from: Long, to: Long) {
            val start = maxOf(from, weekStart)
            val end = minOf(to, nowMs)
            if (end <= start) return
            week[app] = (week[app] ?: 0L) + (end - start)
            for (d in 0 until DAYS) {
                val dayEnd = if (d + 1 < DAYS) dayStarts[d + 1] else Long.MAX_VALUE
                val overlap = minOf(end, dayEnd) - maxOf(start, dayStarts[d])
                if (overlap > 0) {
                    perDay[d] += overlap
                    days[d][app] = (days[d][app] ?: 0L) + overlap
                }
            }
        }

        var current: String? = null
        var since = 0L
        val seen = HashSet<String>()
        for (e in events) {
            val pkg = e.pkg
            when {
                e.foreground && pkg != null -> {
                    if (pkg == current) continue        // a second activity of the same app
                    current?.let { prev -> appOf(prev)?.let { app -> add(app, since, e.at) } }
                    current = pkg
                    since = e.at
                    seen += pkg
                }
                pkg == null || pkg == current -> {      // screen off, or the current app left
                    current?.let { appOf(it)?.let { app -> add(app, since, e.at) } }
                    current = null
                }
                pkg !in seen -> {
                    // Left the foreground before the events start: it was open since the window began.
                    seen += pkg
                    if (current == null) appOf(pkg)?.let { add(it, weekStart, e.at) }
                }
            }
        }
        current?.let { appOf(it)?.let { app -> add(app, since, nowMs) } }

        return Summary(
            todayMs = perDay.last(),
            todayByApp = days.last().sortedByTime(),
            weekByApp = week.sortedByTime(),
            perDay = perDay.toList(),
            perDayByApp = days.map { it.sortedByTime() },
        )
    }

    /** "1 h 05 m", "12 m", "0 m". */
    fun format(ms: Long): String {
        val minutes = ms / 60_000
        return if (minutes >= 60) "${minutes / 60} h ${"%02d".format(minutes % 60)} m" else "$minutes m"
    }

    private fun Map<String, Long>.sortedByTime() =
        toList().sortedByDescending { it.second }.toMap(LinkedHashMap())

    private const val DAYS = 7
}
