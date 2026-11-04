package com.assem.scrollbreak.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThrottleTest {
    private var now = 1_000L
    private val throttle = Throttle(200) { now }

    @Test
    fun firstCallPasses() {
        assertTrue(throttle.shouldPass("a"))
    }

    @Test
    fun callsInsideIntervalAreDropped() {
        assertTrue(throttle.shouldPass("a"))
        now += 199
        assertFalse(throttle.shouldPass("a"))
    }

    @Test
    fun callPassesAfterInterval() {
        assertTrue(throttle.shouldPass("a"))
        now += 200
        assertTrue(throttle.shouldPass("a"))
    }

    @Test
    fun keysAreIndependent() {
        assertTrue(throttle.shouldPass("a"))
        assertTrue(throttle.shouldPass("b"))
    }

    @Test
    fun droppedCallDoesNotExtendWindow() {
        assertTrue(throttle.shouldPass("a"))
        now += 150
        assertFalse(throttle.shouldPass("a"))
        now += 60
        assertTrue(throttle.shouldPass("a"))
    }
}
