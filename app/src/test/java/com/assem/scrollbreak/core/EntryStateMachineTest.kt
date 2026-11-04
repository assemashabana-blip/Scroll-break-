package com.assem.scrollbreak.core

import com.assem.scrollbreak.core.EntryStateMachine.Action
import com.assem.scrollbreak.core.EntryStateMachine.Observation
import com.assem.scrollbreak.core.EntryStateMachine.State
import com.assem.scrollbreak.core.rules.Fingerprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EntryStateMachineTest {
    private val yt = "com.google.android.youtube"
    private val wa = "com.whatsapp"
    private val ig = "com.instagram.android"
    private var now = 100_000L
    private val m = EntryStateMachine(isMessaging = { it == wa || it == "org.telegram.messenger" }, clock = { now })

    private fun fp(vararg v: String) = Fingerprint(v.toList())
    private fun player(fingerprint: Fingerprint = Fingerprint.EMPTY) = Observation(yt, true, fingerprint = fingerprint)
    private val notPlayer = Observation(yt, false)

    /** WhatsApp is in front, then YouTube takes over. */
    private fun openFromWhatsApp() {
        m.onForeground(wa)
        now += 1000
        m.onForeground(yt)
    }

    @Test
    fun unknownSourceIsBlockedImmediately() {
        m.onForeground(yt)
        val r = m.onObservation(player())
        assertEquals(Action.BLOCK, r.action)
        assertEquals("block:no_friend_source", r.reason)
        assertEquals(State.BLOCK, m.state(yt))
    }

    @Test
    fun cameFromAnotherNonMessagingAppIsBlocked() {
        m.onForeground("com.android.chrome")
        m.onForeground(yt)
        assertEquals(Action.BLOCK, m.onObservation(player()).action)
    }

    @Test
    fun messagingSourceAllowsOneVideo() {
        openFromWhatsApp()
        now += 1500
        val r = m.onObservation(player())
        assertEquals(Action.NONE, r.action)
        assertEquals("allow:messaging:$wa", r.reason)
        assertEquals(State.ALLOW_ONE, m.state(yt))
    }

    @Test
    fun messagingSourceTooOldIsBlocked() {
        openFromWhatsApp()
        now += 5001
        assertEquals(Action.BLOCK, m.onObservation(player()).action)
    }

    @Test
    fun allowWindowIsConfigurable() {
        m.allowWindowMs = 10_000
        openFromWhatsApp()
        now += 8000
        assertEquals(Action.NONE, m.onObservation(player()).action)
    }

    @Test
    fun browsingInAppThenOpeningPlayerAfterWindowIsBlocked() {
        // WhatsApp, then YouTube home, then 30 s later the Shorts tab: not a friend video.
        openFromWhatsApp()
        m.onObservation(notPlayer)
        now += 30_000
        assertEquals(Action.BLOCK, m.onObservation(player()).action)
    }

    @Test
    fun differentVideoWhileAllowedIsBlocked() {
        openFromWhatsApp()
        m.onObservation(player(fp("@a", "title A")))
        m.onObservation(player(fp("@a", "title A")))
        now += 4000
        val r = m.onObservation(player(fp("@b", "title B")))
        assertEquals(Action.BLOCK, r.action)
        assertEquals("block:next_video", r.reason)
        assertEquals(State.BLOCK, m.state(yt))
    }

    @Test
    fun sameChannelDifferentTitleIsBlocked() {
        openFromWhatsApp()
        m.onObservation(player())
        m.onObservation(player(fp("@a", "one")))
        assertEquals(Action.BLOCK, m.onObservation(player(fp("@a", "two"))).action)
    }

    @Test
    fun emptyToValueIsInitialLoadNotAChange() {
        openFromWhatsApp()
        m.onObservation(player())                                                     // enters, nothing loaded
        assertEquals(Action.NONE, m.onObservation(player(fp("@a", ""))).action)
        assertEquals(Action.NONE, m.onObservation(player(fp("@a", "title"))).action)  // title loads late
        assertEquals(Action.NONE, m.onObservation(player(fp("@a", "title"))).action)
        assertEquals(State.ALLOW_ONE, m.state(yt))
    }

    @Test
    fun fingerprintDisappearingTemporarilyIsNotAChange() {
        openFromWhatsApp()
        m.onObservation(player())
        m.onObservation(player(fp("@a", "title")))
        assertEquals(Action.NONE, m.onObservation(player(fp("", ""))).action)
        assertEquals(Action.NONE, m.onObservation(player(fp("@a", "title"))).action)
    }

    @Test
    fun leavingThePlayerResetsAndReentryFromFeedIsBlocked() {
        openFromWhatsApp()
        m.onObservation(player())
        assertEquals("left_player", m.onObservation(notPlayer).reason)
        assertEquals(State.IDLE, m.state(yt))
        now += 20_000
        assertEquals(Action.BLOCK, m.onObservation(player()).action)
    }

    @Test
    fun reopeningFriendLinkAfterLeavingAllowsAgain() {
        openFromWhatsApp()
        m.onObservation(player(fp("@a", "t")))
        m.onObservation(notPlayer)
        m.onForeground(wa)
        now += 500
        m.onForeground(yt)
        assertEquals(Action.NONE, m.onObservation(player()).action)
        assertEquals(State.ALLOW_ONE, m.state(yt))
    }

    @Test
    fun blockRetriesOnlyAfterRetryWindow() {
        m.onForeground(yt)
        m.onObservation(player())                                  // block #1
        now += 1999
        assertEquals(Action.NONE, m.onObservation(player()).action)
        now += 1
        val r = m.onObservation(player())
        assertEquals(Action.BLOCK, r.action)
        assertEquals("block:retry", r.reason)
    }

    @Test
    fun onLeftForgetsSession() {
        openFromWhatsApp()
        m.onObservation(player())
        m.onLeft(yt)
        assertEquals(State.IDLE, m.state(yt))
    }

    @Test
    fun dmSeenRecentlyAllowsPlayer() {
        m.onForeground(ig)
        m.onObservation(Observation(ig, false, inDm = true))
        now += 2000
        assertEquals("allow:dm", m.onObservation(Observation(ig, true)).reason)
    }

    @Test
    fun dmTooOldDoesNotAllow() {
        m.onForeground(ig)
        m.onObservation(Observation(ig, false, inDm = true))
        now += 6000
        assertEquals(Action.BLOCK, m.onObservation(Observation(ig, true)).action)
    }

    @Test
    fun sourceIsTheEntryRightBeforeTheCurrentPackage() {
        // WhatsApp, then Chrome, then YouTube: the source is Chrome, so blocked.
        m.onForeground(wa)
        m.onForeground("com.android.chrome")
        m.onForeground(yt)
        assertEquals(Action.BLOCK, m.onObservation(player()).action)
    }

    @Test
    fun repeatedEventsOfOnePackageAreOneHistoryEntry() {
        m.onForeground(wa)
        now += 500
        m.onForeground(yt)
        m.onForeground(yt)
        m.onForeground(yt)
        now += 500
        assertEquals(Action.NONE, m.onObservation(player()).action)
    }

    @Test
    fun sessionsAreIndependentPerPackage() {
        openFromWhatsApp()
        m.onObservation(player())
        assertEquals(State.IDLE, m.state(ig))
        assertNull(m.onObservation(Observation(ig, false)).reason)
        assertEquals(State.ALLOW_ONE, m.state(yt))
    }

    private fun scroll(i: Int) = Observation(yt, true, scrollIndex = i)

    @Test
    fun scrollIndexChangeWhileAllowedIsBlocked() {
        openFromWhatsApp()
        m.onObservation(player())
        assertEquals(Action.NONE, m.onObservation(scroll(4)).action)   // baseline
        assertEquals(Action.NONE, m.onObservation(scroll(4)).action)   // settling, same page
        val r = m.onObservation(scroll(5))
        assertEquals(Action.BLOCK, r.action)
        assertEquals("block:scrolled", r.reason)
    }

    @Test
    fun scrollBackwardsIsAlsoANewVideo() {
        openFromWhatsApp()
        m.onObservation(player())
        m.onObservation(scroll(4))
        assertEquals(Action.BLOCK, m.onObservation(scroll(3)).action)
    }

    @Test
    fun scrollIndexIsIgnoredWhenNotAllowed() {
        m.onForeground(yt)
        assertEquals(Action.BLOCK, m.onObservation(scroll(2)).action)   // entry itself is blocked
    }

    @Test
    fun scrollBaselineResetsAfterLeaving() {
        openFromWhatsApp()
        m.onObservation(player())
        m.onObservation(scroll(4))
        m.onObservation(notPlayer)
        m.onForeground(wa)
        now += 500
        m.onForeground(yt)
        m.onObservation(player())
        assertEquals(Action.NONE, m.onObservation(scroll(9)).action)   // new session, new baseline
    }
}
