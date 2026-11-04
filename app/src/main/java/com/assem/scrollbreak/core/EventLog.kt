package com.assem.scrollbreak.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Thread-safe in-memory ring buffer of the most recent events, newest first. */
class EventLog(private val capacity: Int = 500) {
    private val lock = Any()
    private var nextId = 0L
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun add(
        timeMs: Long,
        packageName: String,
        eventType: String,
        className: String,
        detail: String = "",
        state: String = "n/a",
    ) {
        synchronized(lock) {
            val entry = LogEntry(nextId++, timeMs, packageName, eventType, className, detail, state)
            _entries.value = (listOf(entry) + _entries.value).take(capacity)
        }
    }

    fun clear() {
        synchronized(lock) { _entries.value = emptyList() }
    }
}
