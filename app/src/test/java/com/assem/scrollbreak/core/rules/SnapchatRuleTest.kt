package com.assem.scrollbreak.core.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Runs the shipped Snapchat rule against sanitised dumps captured from a real, logged-in phone. */
class SnapchatRuleTest {
    private val pkg = "com.snapchat.android"
    private val rule = RuleParser.parse(File("src/main/assets/rules/snapchat.json").readText())

    private fun screen(dump: String) =
        DumpParser.screenFor(DumpParser.parse(javaClass.getResource("/dumps/$dump")!!.readText()), pkg)

    @Test
    fun spotlightIsAPlayer() {
        for (dump in listOf("snapchat_spotlight1.txt", "snapchat_spotlight2.txt")) {
            val s = screen(dump)
            assertTrue(dump, rule.isPlayer(s))
            assertEquals(dump, listOf("spotlight_tab"), rule.matchingFeedEntries(s))
        }
    }

    @Test
    fun chatListIsNotAPlayer() {
        val s = screen("snapchat_chat.txt")
        assertFalse(rule.isPlayer(s))
        assertTrue(rule.matchingFeedEntries(s).isEmpty())
    }

    @Test
    fun fingerprintTellsTheTwoVideosApart() {
        val a = rule.fingerprint(screen("snapchat_spotlight1.txt"))
        val b = rule.fingerprint(screen("snapchat_spotlight2.txt"))
        assertTrue("sound line present", a.values[0].isNotEmpty() && b.values[0].isNotEmpty())
        assertTrue(a.differsFrom(b))
        assertFalse(a.differsFrom(rule.fingerprint(screen("snapchat_spotlight1.txt"))))
    }

    @Test
    fun chatListHasNoFingerprint() {
        assertEquals("", rule.fingerprint(screen("snapchat_chat.txt")).values[0])
    }

    @Test
    fun blockedVideoRedirectsToTheChatTab() {
        val target = rule.redirectTo!!
        val nodes = DumpParser.parse(
            javaClass.getResource("/dumps/snapchat_spotlight1.txt")!!.readText(),
        ).flatMap { it.nodes }
        assertEquals(1, nodes.count { target.matches(it) })
    }
}
