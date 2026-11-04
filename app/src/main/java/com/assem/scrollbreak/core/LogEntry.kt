package com.assem.scrollbreak.core

/** One accessibility event as seen by the service. */
data class LogEntry(
    val id: Long,
    val timeMs: Long,
    val packageName: String,
    val eventType: String,
    val className: String,
    val detail: String = "",
    /** State machine state; always "n/a" until the state machine exists (Phase 4). */
    val state: String = "n/a",
)
