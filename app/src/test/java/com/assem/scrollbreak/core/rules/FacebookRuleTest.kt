package com.assem.scrollbreak.core.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Runs the shipped Facebook rule against sanitised dumps captured from a real, logged-in phone. */
class FacebookRuleTest {
    private val pkg = "com.facebook.katana"
    private val rule = RuleParser.parse(File("src/main/assets/rules/facebook.json").readText())

    private fun text(dump: String) = javaClass.getResource("/dumps/$dump")!!.readText()
    private fun screen(dump: String) = DumpParser.screenFor(DumpParser.parse(text(dump)), pkg)

    @Test
    fun reelsTabIsAPlayer() {
        for (dump in listOf("facebook_video1.txt", "facebook_video2.txt")) {
            val s = screen(dump)
            assertTrue(dump, rule.isPlayer(s))
            assertEquals(dump, listOf("reels_tab_selected"), rule.matchingFeedEntries(s))
        }
    }

    @Test
    fun homeFeedIsNotAPlayerEvenWithAnUnreadReelsBadge() {
        // The Reels tab is present on Home too ("Reels, tab 2 of 4, 10 or more new"), unselected.
        val s = screen("facebook_home.txt")
        assertFalse(rule.isPlayer(s))
        assertTrue(rule.matchingFeedEntries(s).isEmpty())
    }

    @Test
    fun redirectGoesToTheHomeTab() {
        val target = rule.redirectTo!!
        val nodes = DumpParser.parse(text("facebook_video1.txt")).flatMap { it.nodes }
        assertEquals(1, nodes.count { target.matches(it) })
    }

    @Test
    fun scrollSignalMatchesTheListUnderTheTabs() {
        val signal = rule.scrollSignal!!.copy(visible = false)
        val nodes = DumpParser.parse(text("facebook_video1.txt")).flatMap { it.nodes }
        assertTrue(nodes.any { signal.matches(it) })
        assertFalse(nodes.all { signal.matches(it) })
    }
}
