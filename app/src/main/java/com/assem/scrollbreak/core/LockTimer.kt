package com.assem.scrollbreak.core

/**
 * A commitment: blocking stays on and cannot be switched off until [endsAt]. Times are wall-clock
 * milliseconds so the lock survives the app being killed and the phone restarting.
 *
 * Moving the phone's clock backwards must not shorten the lock, so the remaining time is never
 * more than the original [durationMs] and never less than what the clock says is left.
 */
data class LockTimer(val startedAt: Long, val endsAt: Long) {
    val durationMs: Long get() = (endsAt - startedAt).coerceAtLeast(0)

    /** Milliseconds left at [now]; 0 once the lock is over. */
    fun remainingMs(now: Long): Long = (endsAt - now).coerceIn(0, durationMs)

    fun isActive(now: Long): Boolean = remainingMs(now) > 0

    companion object {
        fun start(now: Long, minutes: Int): LockTimer = LockTimer(now, now + minutes * 60_000L)

        /**
         * "4:09" below an hour, "1:05:09" below a day, "2 d 01:05:09" beyond. Rounds up so it never
         * shows 0:00 early.
         */
        fun format(remainingMs: Long): String {
            val total = (remainingMs.coerceAtLeast(0) + 999) / 1000
            val d = total / 86_400
            val h = total % 86_400 / 3600
            val m = total % 3600 / 60
            val s = total % 60
            return when {
                d > 0 -> "%d d %02d:%02d:%02d".format(d, h, m, s)
                h > 0 -> "%d:%02d:%02d".format(h, m, s)
                else -> "%d:%02d".format(m, s)
            }
        }

        /** "45 min", "1 h", "1 h 30 min", "2 d 3 h" for the dial label and the confirmation. */
        fun describe(minutes: Int): String {
            val d = minutes / 1440
            val h = minutes % 1440 / 60
            val m = minutes % 60
            return buildList {
                if (d > 0) add("$d d")
                if (h > 0) add("$h h")
                if (m > 0 || (d == 0 && h == 0)) add("$m min")
            }.joinToString(" ")
        }
    }
}
