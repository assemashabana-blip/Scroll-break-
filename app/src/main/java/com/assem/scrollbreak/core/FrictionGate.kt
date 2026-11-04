package com.assem.scrollbreak.core

/**
 * Delays switching ScrollBreak off. While the "turn the service off" page is open the gate is
 * locked for [durationMs]; once that has passed it stays open for as long as the user keeps the
 * page open. Leaving the page for longer than [graceMs] re-arms it, so the wait cannot be skipped
 * by backing out and coming straight back.
 */
class FrictionGate(
    @Volatile var durationMs: Long = 60_000,
    private val graceMs: Long = 3_000,
    private val clock: () -> Long,
) {
    data class Status(val locked: Boolean, val remainingMs: Long)

    private var startedAt: Long? = null
    private var lastSeenAt: Long = 0

    @Synchronized
    fun update(pageVisible: Boolean): Status {
        val now = clock()
        if (durationMs <= 0) {
            startedAt = null
            return OPEN
        }
        if (!pageVisible) {
            // A brief flicker (a dialog, a transition) must not reset the countdown.
            if (startedAt != null && now - lastSeenAt > graceMs) startedAt = null
            return OPEN
        }
        lastSeenAt = now
        val start = startedAt ?: now.also { startedAt = it }
        val remaining = durationMs - (now - start)
        return if (remaining > 0) Status(true, remaining) else OPEN
    }

    @Synchronized
    fun reset() {
        startedAt = null
    }

    private companion object {
        val OPEN = Status(false, 0)
    }
}
