package com.assem.scrollbreak.core

import com.assem.scrollbreak.core.rules.Purpose
import com.assem.scrollbreak.core.rules.RuleParseException
import com.assem.scrollbreak.core.rules.RuleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class FrictionGateTest {
    private var now = 1_000_000L
    private val gate = FrictionGate(durationMs = 60_000, graceMs = 3_000) { now }

    @Test
    fun lockedFromTheMomentThePageOpens() {
        val s = gate.update(pageVisible = true)
        assertTrue(s.locked)
        assertEquals(60_000L, s.remainingMs)
    }

    @Test
    fun countdownShrinksAndThenOpens() {
        gate.update(true)
        now += 59_000
        assertEquals(1_000L, gate.update(true).remainingMs)
        now += 1_000
        assertFalse(gate.update(true).locked)
    }

    @Test
    fun staysOpenWhilePageStaysOpen() {
        gate.update(true)
        now += 61_000
        assertFalse(gate.update(true).locked)
        now += 120_000
        assertFalse(gate.update(true).locked)
    }

    @Test
    fun leavingBrieflyDoesNotResetTheCountdown() {
        gate.update(true)
        now += 20_000
        gate.update(true)
        now += 2_000
        assertFalse(gate.update(false).locked)       // page not visible for a moment
        now += 1_000
        val s = gate.update(true)
        assertTrue(s.locked)
        assertEquals(37_000L, s.remainingMs)         // still counting from the first visit
    }

    @Test
    fun leavingForLongerThanTheGraceResets() {
        gate.update(true)
        now += 30_000
        gate.update(true)
        now += 10_000
        gate.update(false)                            // gone for 10 s: re-armed
        now += 1_000
        assertEquals(60_000L, gate.update(true).remainingMs)
    }

    @Test
    fun backingOutAfterTheWaitStillRearms() {
        gate.update(true)
        now += 61_000
        gate.update(true)                             // unlocked
        now += 4_000
        gate.update(false)
        now += 10_000
        assertTrue(gate.update(true).locked)
    }

    @Test
    fun zeroDurationDisablesFriction() {
        gate.durationMs = 0
        assertFalse(gate.update(true).locked)
    }

    @Test
    fun resetRearms() {
        gate.update(true)
        now += 61_000
        gate.update(true)
        gate.reset()
        assertTrue(gate.update(true).locked)
    }
}

class HealthBoardTest {
    private var now = 5_000L
    private val board = HealthBoard { now }

    @Test
    fun recordsLastSeenAndCountPerSignal() {
        board.hit("youtube", "player")
        now += 1_000
        board.hit("youtube", "player")
        board.hit("youtube", "fingerprint")
        val y = board.entries.value.getValue("youtube")
        assertEquals(HealthBoard.Entry(6_000, 2), y.getValue("player"))
        assertEquals(HealthBoard.Entry(6_000, 1), y.getValue("fingerprint"))
    }

    @Test
    fun appsAreIndependentAndClearEmpties() {
        board.hit("a", "player")
        board.hit("b", "player")
        assertEquals(setOf("a", "b"), board.entries.value.keys)
        board.clear()
        assertTrue(board.entries.value.isEmpty())
    }
}

class BlockStatsTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private fun at(day: Int, hour: Int): Long = Calendar.getInstance(utc).run {
        clear(); set(2026, Calendar.NOVEMBER, day, hour, 0, 0); timeInMillis
    }
    private val now = at(10, 15)

    @Test
    fun countsTodayAndTheWeekPerApp() {
        val lines = listOf(
            "${at(10, 9)},youtube,block:no_friend_source,120",
            "${at(10, 12)},instagram,block:scrolled,20",
            "${at(10, 13)},youtube,block:next_video,200",
            "${at(9, 23)},youtube,block:retry",              // yesterday: week only, old format
            "${at(4, 10)},tiktok,block:x,5",                  // exactly 6 days back: still in the week
            "${at(3, 23)},tiktok,block:x,5",                  // 7 days back: out
        )
        val s = BlockStats.summarize(lines, now, utc)
        assertEquals(3, s.today)
        assertEquals(5, s.last7Days)
        assertEquals(mapOf("youtube" to 2, "instagram" to 1), s.todayByApp)
        assertEquals(listOf("youtube", "instagram"), s.todayByApp.keys.toList())   // busiest first
    }

    @Test
    fun ignoresGarbageAndFutureLines() {
        val lines = listOf("", "abc,youtube,x", "${now + 60_000},youtube,x,1", "${at(10, 8)},,x,1", "${at(10, 8)}")
        assertEquals(BlockStats.Summary.EMPTY, BlockStats.summarize(lines, now, utc))
    }

    @Test
    fun midnightBelongsToTheNewDay() {
        val lines = listOf("${at(10, 0)},youtube,x,1", "${at(9, 23) + 59 * 60_000},youtube,x,1")
        assertEquals(1, BlockStats.summarize(lines, now, utc).today)
    }

    @Test
    fun countsEachOfTheLastSevenDaysOldestFirst() {
        val lines = listOf(
            "${at(10, 9)},youtube,x,1",
            "${at(10, 14)},instagram,x,1",
            "${at(9, 23) + 59 * 60_000},youtube,x,1",   // 23:59 yesterday
            "${at(7, 8)},tiktok,x,1",
            "${at(4, 0)},tiktok,x,1",                   // first minute of the oldest day
            "${at(3, 23)},tiktok,x,1",                  // before the week: dropped
        )
        val s = BlockStats.summarize(lines, now, utc)
        assertEquals(listOf(1, 0, 0, 1, 0, 1, 2), s.perDay)
        assertEquals(s.last7Days, s.perDay.sum())
        assertEquals(mapOf("tiktok" to 2, "youtube" to 2, "instagram" to 1), s.weekByApp)
    }

    @Test
    fun anEmptyWeekStillHasSevenDays() {
        assertEquals(List(7) { 0 }, BlockStats.summarize(emptyList(), now, utc).perDay)
    }
}

class RulePurposeTest {
    private fun rule(extra: String) =
        """{"schemaVersion":1,"app":"t","packages":["p.q"],"playerSignature":{"viewId":"a:id/x"} $extra}"""

    @Test
    fun defaultsToShortVideo() {
        assertEquals(Purpose.SHORT_VIDEO, RuleParser.parse(rule("")).purpose)
    }

    @Test
    fun parsesFriction() {
        assertEquals(Purpose.FRICTION, RuleParser.parse(rule(""","purpose":"friction"""")).purpose)
    }

    @Test
    fun rejectsUnknownPurpose() {
        try {
            RuleParser.parse(rule(""","purpose":"nonsense""""))
            fail()
        } catch (e: RuleParseException) {
            assertTrue(e.message!!.contains("purpose"))
        }
    }

    @Test
    fun signalNamesListEverythingARuleCanReport() {
        val r = RuleParser.parse(
            rule(""","feedEntrySignatures":[{"name":"tab","signature":{"viewId":"a:id/t"}}],"allowSources":[{"name":"dm","signature":{"viewId":"a:id/d"}}],"fingerprintNodes":[{"name":"a","viewId":"a:id/f"}]"""),
        )
        assertEquals(listOf("player", "feed:tab", "source:dm", "fingerprint"), r.signalNames())
        assertNull(null)
    }
}
