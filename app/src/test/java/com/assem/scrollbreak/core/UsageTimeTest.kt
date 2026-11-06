package com.assem.scrollbreak.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class UsageTimeTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val day = 86_400_000L
    private val min = 60_000L
    // Noon on day 10 (UTC); today starts at 10 * day.
    private val now = 10 * day + 12 * 60 * min
    private val apps = mapOf("com.tiktok" to "tiktok", "com.yt" to "youtube")
    private fun fg(p: String, at: Long) = UsageTime.Event(p, true, at)
    private fun bg(p: String, at: Long) = UsageTime.Event(p, false, at)

    private fun run(vararg e: UsageTime.Event) = UsageTime.summarize(e.toList(), { apps[it] }, now, utc)

    @Test fun countsOneSession() {
        val t = 10 * day
        val s = run(fg("com.tiktok", t + 60 * min), bg("com.tiktok", t + 90 * min))
        assertEquals(30 * min, s.todayMs)
        assertEquals(30 * min, s.todayByApp["tiktok"])
    }

    @Test fun anotherAppComingForwardEndsTheSession() {
        val t = 10 * day
        val s = run(fg("com.tiktok", t), fg("com.other", t + 10 * min), bg("com.tiktok", t + 11 * min))
        assertEquals(10 * min, s.todayByApp["tiktok"])
        assertEquals(10 * min, s.todayMs)
    }

    @Test fun screenOffEndsTheSession() {
        val t = 10 * day
        val s = run(fg("com.yt", t), UsageTime.Event(null, false, t + 5 * min))
        assertEquals(5 * min, s.todayByApp["youtube"])
    }

    @Test fun stillOpenCountsUntilNow() {
        val s = run(fg("com.yt", now - 7 * min))
        assertEquals(7 * min, s.todayByApp["youtube"])
    }

    @Test fun sessionOverMidnightIsSplitAcrossDays() {
        val s = run(fg("com.tiktok", 10 * day - 20 * min), bg("com.tiktok", 10 * day + 15 * min))
        assertEquals(15 * min, s.todayMs)
        assertEquals(20 * min, s.perDay[5])
        assertEquals(35 * min, s.weekByApp["tiktok"])
    }

    @Test fun secondActivityOfTheSameAppDoesNotRestart() {
        val t = 10 * day
        val s = run(fg("com.yt", t), fg("com.yt", t + 3 * min), bg("com.yt", t + 8 * min))
        assertEquals(8 * min, s.todayByApp["youtube"])
    }

    @Test fun formats() {
        assertEquals("0 m", UsageTime.format(0))
        assertEquals("12 m", UsageTime.format(12 * min))
        assertEquals("1 h 05 m", UsageTime.format(65 * min))
    }
}
