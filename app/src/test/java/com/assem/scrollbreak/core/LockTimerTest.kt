package com.assem.scrollbreak.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockTimerTest {
    private val t0 = 1_000_000_000L
    private val lock = LockTimer.start(t0, 30)

    @Test
    fun countsDownAndEnds() {
        assertEquals(30 * 60_000L, lock.remainingMs(t0))
        assertEquals(10 * 60_000L, lock.remainingMs(t0 + 20 * 60_000L))
        assertTrue(lock.isActive(t0 + 30 * 60_000L - 1))
        assertFalse(lock.isActive(t0 + 30 * 60_000L))
        assertEquals(0L, lock.remainingMs(t0 + 99 * 60_000L))
    }

    @Test
    fun clockMovedBackwardsNeverAddsTimeBeyondTheOriginalDuration() {
        assertEquals(30 * 60_000L, lock.remainingMs(t0 - 5 * 3_600_000L))
    }

    @Test
    fun clockMovedForwardEndsItEarlyOnlyByWhatTheClockSays() {
        assertFalse(lock.isActive(t0 + 31 * 60_000L))
    }

    @Test
    fun formatsHoursMinutesSeconds() {
        assertEquals("4:09", LockTimer.format(249_000))
        assertEquals("1:05:09", LockTimer.format(3_909_000))
        assertEquals("0:01", LockTimer.format(1))          // rounds up, never 0:00 while locked
        assertEquals("0:00", LockTimer.format(0))
        assertEquals("0:00", LockTimer.format(-5))
        assertEquals("2 d 01:05:09", LockTimer.format(2 * 86_400_000L + 3_909_000))
        assertEquals("1 d 00:00:00", LockTimer.format(86_400_000L))
    }

    @Test
    fun describesDialValues() {
        assertEquals("45 min", LockTimer.describe(45))
        assertEquals("1 h", LockTimer.describe(60))
        assertEquals("1 h 30 min", LockTimer.describe(90))
        assertEquals("1 min", LockTimer.describe(1))
        assertEquals("1 d", LockTimer.describe(1440))
        assertEquals("2 d 3 h", LockTimer.describe(2 * 1440 + 180))
        assertEquals("30 d", LockTimer.describe(30 * 24 * 60))
    }
}
