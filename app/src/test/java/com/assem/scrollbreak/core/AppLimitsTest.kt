package com.assem.scrollbreak.core

import com.assem.scrollbreak.core.LimitDecision.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class AppLimitsTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val min = 60_000L
    private val day = 86_400_000L

    /** 2026-11-09 14:30 UTC. */
    private val now = Calendar.getInstance(utc).run {
        clear(); set(2026, Calendar.NOVEMBER, 9, 14, 30); timeInMillis
    }
    private val midnight = Calendar.getInstance(utc).run {
        clear(); set(2026, Calendar.NOVEMBER, 10, 0, 0); timeInMillis
    }
    private val pkg = "com.example.app"

    @Test
    fun nextMidnightIsTheStartOfTomorrow() {
        assertEquals(midnight, LimitBook.nextMidnight(now, utc))
        assertEquals(midnight + day, LimitBook.nextMidnight(midnight, utc))   // exactly midnight belongs to the new day
    }

    @Test
    fun aDailyLimitLocksUntilTomorrow() {
        val c = LimitBook().setDaily(pkg, 30, now, utc)
        assertNull(c.error)
        val l = c.book.get(pkg)!!
        assertEquals(30, l.dailyMinutes)
        assertEquals(midnight, l.dailyLockedUntil)
        assertTrue(l.dailyLocked(now))
        assertFalse(l.dailyLocked(midnight))
    }

    @Test
    fun whileLockedADailyLimitCanOnlyBeLowered() {
        val book = LimitBook().setDaily(pkg, 30, now, utc).book
        assertNotNull(book.setDaily(pkg, 30, now + 5 * min, utc).error)    // same
        assertNotNull(book.setDaily(pkg, 45, now + 5 * min, utc).error)    // raised
        assertNotNull(book.removeDaily(pkg, now + 5 * min).error)          // removed
        val lowered = book.setDaily(pkg, 20, now + 5 * min, utc)
        assertNull(lowered.error)
        assertEquals(20, lowered.book.get(pkg)!!.dailyMinutes)
        assertEquals(midnight, lowered.book.get(pkg)!!.dailyLockedUntil)   // lowering does not extend the lock
    }

    @Test
    fun theNextDayItCanBeChangedOrRemoved() {
        val book = LimitBook().setDaily(pkg, 30, now, utc).book
        val tomorrow = midnight + 10 * min
        val raised = book.setDaily(pkg, 60, tomorrow, utc)
        assertNull(raised.error)
        assertEquals(midnight + day, raised.book.get(pkg)!!.dailyLockedUntil)
        val removed = book.removeDaily(pkg, tomorrow)
        assertNull(removed.error)
        assertNull(removed.book.get(pkg))
    }

    @Test
    fun dailyLimitMustBeInRange() {
        assertNotNull(LimitBook().setDaily(pkg, 0, now, utc).error)
        assertNotNull(LimitBook().setDaily(pkg, 24 * 60 + 1, now, utc).error)
        assertNull(LimitBook().setDaily(pkg, 24 * 60, now, utc).error)
    }

    @Test
    fun aBlockCannotBeChangedUntilItEnds() {
        val book = LimitBook().startBlock(pkg, 60, now).book
        assertTrue(book.get(pkg)!!.blockActive(now + 59 * min))
        assertNotNull(book.startBlock(pkg, 5, now + 10 * min).error)
        assertNotNull(book.startBlock(pkg, 600, now + 10 * min).error)     // not even longer
        assertNull(book.startBlock(pkg, 5, now + 61 * min).error)          // after it ends, a new one is fine
    }

    @Test
    fun decisions() {
        val daily = LimitBook().setDaily(pkg, 30, now, utc).book
        assertEquals(LimitDecision.Allowed(10 * min), daily.decide(pkg, 20 * min, now, utc))
        assertEquals(
            LimitDecision.Blocked(Reason.DAILY, midnight),
            daily.decide(pkg, 30 * min, now, utc),
        )
        val blocked = daily.startBlock(pkg, 60, now).book
        assertEquals(
            LimitDecision.Blocked(Reason.BLOCK, now + 60 * min),
            blocked.decide(pkg, 0, now, utc),
        )
        assertEquals(LimitDecision.Allowed(null), LimitBook().decide(pkg, 99 * min, now, utc))
        // A finished block no longer stops the app, and the daily limit goes on counting.
        assertEquals(LimitDecision.Allowed(30 * min), blocked.decide(pkg, 0, now + 61 * min, utc))
    }

    @Test
    fun protectionLastsAsLongAsTheLongestLockedLimit() {
        assertEquals(0L, LimitBook().protectionRemainingMs(now))
        val book = LimitBook()
            .setDaily("a", 30, now, utc).book
            .startBlock("b", 3 * 24 * 60, now).book
        assertEquals(3 * day, book.protectionRemainingMs(now))
        assertEquals(2 * day, book.protectionRemainingMs(now + day))
        assertEquals(0L, book.protectionRemainingMs(now + 4 * day))
    }

    @Test
    fun anUnlockedDailyLimitNoLongerProtects() {
        val book = LimitBook().setDaily(pkg, 30, now, utc).book
        assertEquals(0L, book.protectionRemainingMs(midnight + min))
        assertTrue(book.isLimited(pkg, midnight + min))                    // still enforced, just editable
    }

    @Test
    fun finishedBlocksDropOutOfTheCounts() {
        val book = LimitBook().startBlock(pkg, 10, now).book
        assertEquals(1, book.activeCount(now))
        assertEquals(0, book.activeCount(now + 11 * min))
        assertEquals(setOf(pkg), book.limitedPackages(now))
        assertTrue(book.limitedPackages(now + 11 * min).isEmpty())
    }

    @Test
    fun roundTripsThroughJson() {
        val book = LimitBook().setDaily("a", 30, now, utc).book.startBlock("b", 90, now).book
        assertEquals(book, LimitBook.fromJson(book.toJson()))
    }

    @Test
    fun brokenJsonGivesAnEmptyBook() {
        assertEquals(LimitBook(), LimitBook.fromJson("not json"))
        assertEquals(LimitBook(), LimitBook.fromJson(null))
        assertEquals(LimitBook(), LimitBook.fromJson(""))
    }
}
