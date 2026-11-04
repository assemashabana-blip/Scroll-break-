package com.assem.scrollbreak.core

import com.assem.scrollbreak.core.PlayerTracker.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerTrackerTest {
    private var now = 10_000L
    private val tracker = PlayerTracker(2000) { now }

    @Test
    fun blocksOnEnteringPlayer() {
        assertEquals(Decision.BLOCK, tracker.onObservation("yt", true))
        assertEquals("PLAYER", tracker.state("yt"))
    }

    @Test
    fun doesNothingOutsidePlayer() {
        assertEquals(Decision.NONE, tracker.onObservation("yt", false))
        assertEquals("IDLE", tracker.state("yt"))
    }

    @Test
    fun doesNotRepeatInsideRetryWindow() {
        tracker.onObservation("yt", true)
        tracker.onBlocked("yt")
        now += 1999
        assertEquals(Decision.NONE, tracker.onObservation("yt", true))
    }

    @Test
    fun repeatsAfterRetryWindowWhileStillInPlayer() {
        tracker.onObservation("yt", true)
        tracker.onBlocked("yt")
        now += 2000
        assertEquals(Decision.BLOCK, tracker.onObservation("yt", true))
    }

    @Test
    fun leavingResetsSoReentryBlocksImmediately() {
        tracker.onObservation("yt", true)
        tracker.onBlocked("yt")
        tracker.onObservation("yt", false)
        now += 10
        assertEquals(Decision.BLOCK, tracker.onObservation("yt", true))
    }

    @Test
    fun packagesAreIndependent() {
        tracker.onObservation("yt", true)
        tracker.onBlocked("yt")
        assertEquals(Decision.BLOCK, tracker.onObservation("ig", true))
        assertEquals("IDLE", tracker.state("tt"))
    }
}
