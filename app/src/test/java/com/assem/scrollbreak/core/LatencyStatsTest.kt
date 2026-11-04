package com.assem.scrollbreak.core

import org.junit.Assert.assertEquals
import org.junit.Test

class LatencyStatsTest {
    @Test
    fun startsEmpty() {
        assertEquals(LatencyStats.Summary.EMPTY, LatencyStats().summary.value)
    }

    @Test
    fun singleSample() {
        val s = LatencyStats()
        s.add(40)
        assertEquals(LatencyStats.Summary(1, 40, 40, 40, 40), s.summary.value)
    }

    @Test
    fun medianP95MaxAndLast() {
        val s = LatencyStats()
        (1..100).forEach { s.add(it.toLong()) }
        val r = s.summary.value
        assertEquals(100, r.count)
        assertEquals(100L, r.lastMs)
        assertEquals(50L, r.medianMs)
        assertEquals(95L, r.p95Ms)
        assertEquals(100L, r.maxMs)
    }

    @Test
    fun lastIsTheMostRecentNotTheLargest() {
        val s = LatencyStats()
        s.add(500)
        s.add(20)
        assertEquals(20L, s.summary.value.lastMs)
        assertEquals(500L, s.summary.value.maxMs)
    }

    @Test
    fun keepsOnlyTheMostRecentCapacity() {
        val s = LatencyStats(capacity = 3)
        listOf(1000L, 10L, 20L, 30L).forEach { s.add(it) }
        val r = s.summary.value
        assertEquals(3, r.count)
        assertEquals(30L, r.maxMs)        // the 1000 ms outlier has been evicted
    }

    @Test
    fun negativeValuesAreClampedAndClearResets() {
        val s = LatencyStats()
        s.add(-5)
        assertEquals(0L, s.summary.value.lastMs)
        s.clear()
        assertEquals(LatencyStats.Summary.EMPTY, s.summary.value)
    }
}
