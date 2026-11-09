package com.assem.scrollbreak.core

import kotlin.math.roundToInt

/**
 * What one tick of the lock dial means. The dial counts minutes up to 4 hours, then hours up to
 * 3 days, then days up to the 30-day limit, so a long lock is still a few turns of the wheel.
 */
enum class LockUnit(
    val minutes: Int,
    val min: Int,
    val max: Int,
    val step: Int,
    val majorEvery: Int,
    val short: String,
) {
    MINUTES(1, 5, 240, 5, 6, "min"),
    HOURS(60, 1, 72, 1, 6, "h"),
    DAYS(1440, 1, 30, 1, 5, "d");

    val minMinutes: Int get() = min * minutes
    val maxMinutes: Int get() = max * minutes

    /** Where the dial pointer sits for [totalMinutes] (it snaps to the nearest tick). */
    fun toDial(totalMinutes: Int): Float = totalMinutes / minutes.toFloat()

    fun fromDial(value: Float): Int = (value * minutes).roundToInt()

    /** The same length, kept inside what this unit's dial can show. */
    fun clamp(totalMinutes: Int): Int = totalMinutes.coerceIn(minMinutes, maxMinutes)

    /** The highest dial value in this unit when no length above [limitMinutes] is allowed. */
    fun maxFor(limitMinutes: Int): Int = minOf(max, limitMinutes / minutes)

    /** [clamp] for a dial that stops at [limitMinutes]. */
    fun clamp(totalMinutes: Int, limitMinutes: Int): Int =
        totalMinutes.coerceIn(minMinutes, maxOf(minMinutes, maxFor(limitMinutes) * minutes))

    companion object {
        /** The units whose dial has at least two ticks below [limitMinutes] (a one-tick dial is no dial). */
        fun available(limitMinutes: Int): List<LockUnit> = entries.filter { it.minMinutes < limitMinutes }

        /** The unit a length of [totalMinutes] is best shown in: past 4 hours the wheel goes to hours, past 3 days to days. */
        fun forMinutes(totalMinutes: Int): LockUnit = when {
            totalMinutes <= MINUTES.maxMinutes -> MINUTES
            totalMinutes <= HOURS.maxMinutes -> HOURS
            else -> DAYS
        }
    }
}
