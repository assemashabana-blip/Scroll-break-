package com.assem.scrollbreak.core

/**
 * Lets at most one call per [intervalMs] through for each key (e.g. package name).
 * The clock is injected so this stays unit-testable.
 */
class Throttle(
    @Volatile var intervalMs: Long = 200,
    private val clock: () -> Long,
) {
    private val lastPassed = HashMap<String, Long>()

    @Synchronized
    fun shouldPass(key: String): Boolean {
        val now = clock()
        val last = lastPassed[key]
        if (last != null && now - last < intervalMs) return false
        lastPassed[key] = now
        return true
    }
}
