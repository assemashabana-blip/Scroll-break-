package com.assem.scrollbreak.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Reaction time (accessibility event received to block action) over the most recent blocks. */
class LatencyStats(private val capacity: Int = 200) {
    data class Summary(val count: Int, val lastMs: Long, val medianMs: Long, val p95Ms: Long, val maxMs: Long) {
        companion object {
            val EMPTY = Summary(0, 0, 0, 0, 0)
        }
    }

    private val samples = ArrayDeque<Long>()
    private val _summary = MutableStateFlow(Summary.EMPTY)
    val summary: StateFlow<Summary> = _summary.asStateFlow()

    @Synchronized
    fun add(ms: Long) {
        samples.addLast(ms.coerceAtLeast(0))
        while (samples.size > capacity) samples.removeFirst()
        _summary.value = compute()
    }

    @Synchronized
    fun clear() {
        samples.clear()
        _summary.value = Summary.EMPTY
    }

    private fun compute(): Summary {
        if (samples.isEmpty()) return Summary.EMPTY
        val sorted = samples.sorted()
        // Nearest-rank percentile: the smallest value with at least p% of samples at or below it.
        fun percentile(p: Int) = sorted[((p * sorted.size + 99) / 100 - 1).coerceIn(0, sorted.size - 1)]
        return Summary(sorted.size, samples.last(), percentile(50), percentile(95), sorted.last())
    }
}
