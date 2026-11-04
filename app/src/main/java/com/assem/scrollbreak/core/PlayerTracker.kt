package com.assem.scrollbreak.core

/**
 * Per-package player state. Decides when to fire the block action: immediately on entering a
 * player, and again every [retryMs] while the user is still in it.
 */
class PlayerTracker(
    private val retryMs: Long = 2000,
    private val clock: () -> Long,
) {
    enum class Decision { NONE, BLOCK }

    private val inPlayer = HashSet<String>()
    private val lastBlock = HashMap<String, Long>()

    @Synchronized
    fun onObservation(key: String, inPlayerNow: Boolean): Decision {
        if (!inPlayerNow) {
            inPlayer.remove(key)
            lastBlock.remove(key)
            return Decision.NONE
        }
        inPlayer.add(key)
        val last = lastBlock[key]
        return if (last == null || clock() - last >= retryMs) Decision.BLOCK else Decision.NONE
    }

    @Synchronized
    fun onBlocked(key: String) {
        lastBlock[key] = clock()
    }

    @Synchronized
    fun state(key: String): String = if (key in inPlayer) "PLAYER" else "IDLE"
}
