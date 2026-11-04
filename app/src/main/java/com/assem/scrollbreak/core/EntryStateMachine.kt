package com.assem.scrollbreak.core

import com.assem.scrollbreak.core.rules.Fingerprint

/**
 * Per-app session state: IDLE -> ALLOW_ONE -> BLOCK.
 *
 * Entering a player is allowed once when the user came straight from a messaging app (or an
 * in-app chat); anything else is blocked. While allowed, a different video fingerprint means the
 * user swiped to the next video, which is blocked. Leaving the player resets to IDLE.
 */
class EntryStateMachine(
    private val isMessaging: (String) -> Boolean,
    private val clock: () -> Long,
    @Volatile var allowWindowMs: Long = 5_000,
    private val retryMs: Long = 2_000,
) {
    enum class State { IDLE, ALLOW_ONE, BLOCK }
    enum class Action { NONE, BLOCK }

    data class Observation(
        val pkg: String,
        val inPlayer: Boolean,
        /** Name of the allow-source screen showing now (e.g. "dm", "story"), if any. */
        val source: String? = null,
        val fingerprint: Fingerprint = Fingerprint.EMPTY,
        /** Current video index from a scroll event, for apps that report one. */
        val scrollIndex: Int? = null,
    )

    /** [reason] is set whenever something worth logging happened. */
    data class Result(val action: Action, val reason: String? = null)

    private class Session {
        var state = State.IDLE
        var fingerprint = Fingerprint.EMPTY
        var lastBlockAt: Long? = null
        var sourceSeenAt: Long? = null
        var sourceName: String? = null
        var scrollIndex: Int? = null
    }

    private class Foreground(val pkg: String, val enteredAt: Long)

    private val history = ArrayDeque<Foreground>()
    private val sessions = HashMap<String, Session>()

    /** Records that [pkg] now has the foreground; consecutive events of one package are one entry. */
    @Synchronized
    fun onForeground(pkg: String) {
        if (history.lastOrNull()?.pkg == pkg) return
        history.addLast(Foreground(pkg, clock()))
        while (history.size > MAX_HISTORY) history.removeFirst()
    }

    /** The app's window is gone (user left it): forget the session. */
    @Synchronized
    fun onLeft(pkg: String) {
        sessions.remove(pkg)
    }

    @Synchronized
    fun state(pkg: String): State = sessions[pkg]?.state ?: State.IDLE

    @Synchronized
    fun onObservation(o: Observation): Result {
        val s = sessions.getOrPut(o.pkg) { Session() }
        val now = clock()
        if (o.source != null) {
            s.sourceSeenAt = now
            s.sourceName = o.source
        }

        if (!o.inPlayer) {
            val was = s.state
            s.state = State.IDLE
            s.fingerprint = Fingerprint.EMPTY
            s.scrollIndex = null
            s.lastBlockAt = null
            return Result(Action.NONE, if (was != State.IDLE) "left_player" else null)
        }

        return when (s.state) {
            State.IDLE -> {
                val why = allowReason(o.pkg, s, now)
                if (why != null) {
                    s.state = State.ALLOW_ONE
                    s.fingerprint = o.fingerprint
                    s.scrollIndex = o.scrollIndex
                    Result(Action.NONE, "allow:$why")
                } else {
                    s.state = State.BLOCK
                    s.lastBlockAt = now
                    Result(Action.BLOCK, "block:no_friend_source")
                }
            }
            State.ALLOW_ONE -> {
                val baseline = s.scrollIndex
                if (o.scrollIndex != null && baseline != null && o.scrollIndex != baseline) {
                    s.state = State.BLOCK
                    s.lastBlockAt = now
                    Result(Action.BLOCK, "block:scrolled")
                } else if (o.fingerprint.differsFrom(s.fingerprint)) {
                    s.state = State.BLOCK
                    s.lastBlockAt = now
                    Result(Action.BLOCK, "block:next_video")
                } else {
                    if (baseline == null) s.scrollIndex = o.scrollIndex
                    // An empty component that has now loaded becomes part of the baseline.
                    s.fingerprint = s.fingerprint.fillGapsFrom(o.fingerprint)
                    Result(Action.NONE)
                }
            }
            State.BLOCK -> {
                val last = s.lastBlockAt
                if (last == null || now - last >= retryMs) {
                    s.lastBlockAt = now
                    Result(Action.BLOCK, "block:retry")
                } else Result(Action.NONE)
            }
        }
    }

    private fun allowReason(pkg: String, s: Session, now: Long): String? {
        // History ends [..., source, pkg]: the source's exit is when pkg first appeared.
        if (history.size >= 2 && history.last().pkg == pkg) {
            val source = history[history.size - 2]
            val exitedAt = history.last().enteredAt
            if (isMessaging(source.pkg) && now - exitedAt <= allowWindowMs) return "messaging:${source.pkg}"
        }
        val seen = s.sourceSeenAt
        if (seen != null && now - seen <= allowWindowMs) return "source:${s.sourceName}"
        return null
    }

    private companion object {
        const val MAX_HISTORY = 12
    }
}
