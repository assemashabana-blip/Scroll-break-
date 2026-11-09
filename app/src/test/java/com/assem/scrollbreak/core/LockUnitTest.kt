package com.assem.scrollbreak.core

import org.junit.Assert.assertEquals
import org.junit.Test

class LockUnitTest {
    @Test
    fun pickingTheUnitForALength() {
        assertEquals(LockUnit.MINUTES, LockUnit.forMinutes(1))
        assertEquals(LockUnit.MINUTES, LockUnit.forMinutes(240))      // 4 h is still minutes
        assertEquals(LockUnit.HOURS, LockUnit.forMinutes(241))        // more than 4 h
        assertEquals(LockUnit.HOURS, LockUnit.forMinutes(72 * 60))
        assertEquals(LockUnit.DAYS, LockUnit.forMinutes(72 * 60 + 1))
        assertEquals(LockUnit.DAYS, LockUnit.forMinutes(30 * 24 * 60))
    }

    @Test
    fun dialRangesCoverTheWholeAllowedSpan() {
        assertEquals(240, LockUnit.MINUTES.maxMinutes)
        assertEquals(30 * 24 * 60, LockUnit.DAYS.maxMinutes)
    }

    @Test
    fun convertsToAndFromTheDial() {
        assertEquals(36f, LockUnit.HOURS.toDial(36 * 60), 0.001f)
        assertEquals(36 * 60, LockUnit.HOURS.fromDial(36f))
        assertEquals(3 * 1440, LockUnit.DAYS.fromDial(3f))
        assertEquals(1.5f, LockUnit.HOURS.toDial(90), 0.001f)
    }

    @Test
    fun switchingUnitKeepsTheLengthInRange() {
        assertEquals(240, LockUnit.MINUTES.clamp(2880))   // 2 days cannot show on the minutes dial
        assertEquals(1440, LockUnit.DAYS.clamp(30))       // 30 min becomes 1 day
        assertEquals(60, LockUnit.HOURS.clamp(30))
        assertEquals(90, LockUnit.HOURS.clamp(90))
    }

    @Test
    fun aDialCanStopBelowTheUnitsOwnMaximum() {
        val day = 24 * 60
        assertEquals(24, LockUnit.HOURS.maxFor(day))                  // a daily limit tops out at 24 h
        assertEquals(240, LockUnit.MINUTES.maxFor(day))
        assertEquals(listOf(LockUnit.MINUTES, LockUnit.HOURS), LockUnit.available(day))
        assertEquals(LockUnit.entries.toList(), LockUnit.available(30 * day))
        assertEquals(day, LockUnit.HOURS.clamp(5000, day))
        assertEquals(60, LockUnit.HOURS.clamp(10, day))
    }
}
