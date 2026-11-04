package com.assem.scrollbreak.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Detection health": when each signature of each app last matched. If an app updates and its
 * rule stops matching, the signal's last-seen time stops moving, which is how a broken rule shows.
 */
class HealthBoard(private val clock: () -> Long) {
    data class Entry(val lastMs: Long, val count: Int)

    private val _entries = MutableStateFlow<Map<String, Map<String, Entry>>>(emptyMap())
    val entries: StateFlow<Map<String, Map<String, Entry>>> = _entries.asStateFlow()

    @Synchronized
    fun hit(app: String, signal: String) {
        val now = clock()
        val appSignals = _entries.value[app].orEmpty()
        val previous = appSignals[signal]
        val updated = appSignals + (signal to Entry(now, (previous?.count ?: 0) + 1))
        _entries.value = _entries.value + (app to updated)
    }

    @Synchronized
    fun clear() {
        _entries.value = emptyMap()
    }
}
