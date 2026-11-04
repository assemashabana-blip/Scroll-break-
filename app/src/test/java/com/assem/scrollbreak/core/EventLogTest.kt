package com.assem.scrollbreak.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventLogTest {
    @Test
    fun newestEntryFirst() {
        val log = EventLog(10)
        log.add(1, "a", "T", "C")
        log.add(2, "b", "T", "C")
        assertEquals(listOf("b", "a"), log.entries.value.map { it.packageName })
    }

    @Test
    fun dropsOldestBeyondCapacity() {
        val log = EventLog(3)
        repeat(5) { log.add(it.toLong(), "p$it", "T", "C") }
        assertEquals(listOf("p4", "p3", "p2"), log.entries.value.map { it.packageName })
    }

    @Test
    fun idsAreUniqueAndIncreasing() {
        val log = EventLog(10)
        repeat(3) { log.add(0, "p", "T", "C") }
        assertEquals(listOf(2L, 1L, 0L), log.entries.value.map { it.id })
    }

    @Test
    fun clearEmptiesLog() {
        val log = EventLog(10)
        log.add(0, "p", "T", "C")
        log.clear()
        assertTrue(log.entries.value.isEmpty())
    }
}
