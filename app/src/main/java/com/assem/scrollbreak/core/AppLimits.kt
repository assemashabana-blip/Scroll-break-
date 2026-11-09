package com.assem.scrollbreak.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.util.TimeZone

/**
 * What one app is held to: a daily time allowance, a block for a stretch of time, or both.
 *
 * Both are commitments. A daily limit cannot be changed or removed until the next midnight after
 * it was set ([dailyLockedUntil]); a block cannot be touched until it ends ([blockedUntil]). Times
 * are wall-clock milliseconds so they survive restarts.
 */
data class AppLimit(
    val pkg: String,
    val dailyMinutes: Int? = null,
    val dailyLockedUntil: Long = 0,
    val blockedUntil: Long = 0,
) {
    fun dailyLocked(now: Long) = dailyMinutes != null && now < dailyLockedUntil
    fun blockActive(now: Long) = now < blockedUntil
    fun isEmpty(now: Long) = dailyMinutes == null && !blockActive(now)
}

sealed interface LimitDecision {
    /** [remainingMs] is how long the app may still run today, or null when only a block could stop it. */
    data class Allowed(val remainingMs: Long?) : LimitDecision

    /** [untilMs] is when the app may be used again. */
    data class Blocked(val reason: Reason, val untilMs: Long) : LimitDecision

    enum class Reason { BLOCK, DAILY }
}

/** Every app's limit, as an immutable value: each change returns a new book or an error message. */
data class LimitBook(val limits: Map<String, AppLimit> = emptyMap()) {

    class Change(val book: LimitBook, val error: String? = null)

    fun get(pkg: String): AppLimit? = limits[pkg]

    /** True when [pkg] has a limit that may still need enforcing. */
    fun isLimited(pkg: String, now: Long): Boolean = limits[pkg]?.isEmpty(now) == false

    /** Packages that currently have a daily limit or an active block. */
    fun limitedPackages(now: Long): Set<String> =
        limits.values.filter { !it.isEmpty(now) }.map { it.pkg }.toSet()

    fun activeCount(now: Long): Int = limits.values.count { !it.isEmpty(now) }

    /** Should [pkg] be stopped right now, given how long it has run today? */
    fun decide(pkg: String, usedTodayMs: Long, now: Long, zone: TimeZone = TimeZone.getDefault()): LimitDecision {
        val limit = limits[pkg] ?: return LimitDecision.Allowed(null)
        if (limit.blockActive(now)) return LimitDecision.Blocked(LimitDecision.Reason.BLOCK, limit.blockedUntil)
        val daily = limit.dailyMinutes ?: return LimitDecision.Allowed(null)
        val left = daily * 60_000L - usedTodayMs
        return if (left <= 0) {
            LimitDecision.Blocked(LimitDecision.Reason.DAILY, nextMidnight(now, zone))
        } else {
            LimitDecision.Allowed(left)
        }
    }

    fun setDaily(pkg: String, minutes: Int, now: Long, zone: TimeZone = TimeZone.getDefault()): Change {
        if (minutes !in MIN_DAILY_MINUTES..MAX_DAILY_MINUTES) {
            return Change(this, "A daily limit is between 1 minute and 24 hours.")
        }
        val existing = limits[pkg]
        if (existing?.dailyLocked(now) == true) {
            return Change(this, "This limit is locked until tomorrow and cannot be changed.")
        }
        val until = nextMidnight(now, zone)
        return Change(with((existing ?: AppLimit(pkg)).copy(dailyMinutes = minutes, dailyLockedUntil = until), now))
    }

    fun removeDaily(pkg: String, now: Long): Change {
        val existing = limits[pkg] ?: return Change(this)
        if (existing.dailyLocked(now)) return Change(this, "This limit is locked until tomorrow.")
        return Change(with(existing.copy(dailyMinutes = null, dailyLockedUntil = 0), now))
    }

    fun startBlock(pkg: String, minutes: Int, now: Long): Change {
        if (minutes !in 1..MAX_BLOCK_MINUTES) return Change(this, "A block is between 1 minute and 30 days.")
        val existing = limits[pkg] ?: AppLimit(pkg)
        if (existing.blockActive(now)) {
            return Change(this, "This app is already blocked. It cannot be changed until the block ends.")
        }
        return Change(with(existing.copy(blockedUntil = now + minutes * 60_000L), now))
    }

    /** How long ScrollBreak must refuse to be uninstalled or switched off because of these limits. */
    fun protectionRemainingMs(now: Long): Long = limits.values.maxOfOrNull { l ->
        maxOf(
            if (l.dailyLocked(now)) l.dailyLockedUntil - now else 0L,
            if (l.blockActive(now)) l.blockedUntil - now else 0L,
        )
    } ?: 0L

    private fun with(limit: AppLimit, now: Long): LimitBook {
        val next = LinkedHashMap(limits)
        if (limit.isEmpty(now)) next.remove(limit.pkg) else next[limit.pkg] = limit
        return LimitBook(next)
    }

    fun toJson(): String = buildJsonObject {
        limits.values.forEach { l ->
            put(
                l.pkg,
                buildJsonObject {
                    l.dailyMinutes?.let { put("d", it) }
                    put("dl", l.dailyLockedUntil)
                    put("b", l.blockedUntil)
                },
            )
        }
    }.toString()

    companion object {
        const val MIN_DAILY_MINUTES = 1
        const val MAX_DAILY_MINUTES = 24 * 60
        const val MAX_BLOCK_MINUTES = 30 * 24 * 60

        /** The start of the next calendar day after [now] in [zone]. */
        fun nextMidnight(now: Long, zone: TimeZone = TimeZone.getDefault()): Long =
            BlockStats.startOfDay(now, zone, -1)

        /** Reads what [toJson] wrote; anything unreadable gives an empty book rather than a crash. */
        fun fromJson(text: String?): LimitBook = try {
            if (text.isNullOrBlank()) {
                LimitBook()
            } else {
                val root: JsonObject = Json.parseToJsonElement(text).jsonObject
                LimitBook(
                    root.entries.associate { (pkg, v) ->
                        val o = v.jsonObject
                        pkg to AppLimit(
                            pkg = pkg,
                            dailyMinutes = (o["d"] as? JsonPrimitive)?.int,
                            dailyLockedUntil = o["dl"]?.jsonPrimitive?.long ?: 0L,
                            blockedUntil = o["b"]?.jsonPrimitive?.long ?: 0L,
                        )
                    },
                )
            }
        } catch (_: Exception) {
            LimitBook()
        }
    }
}
