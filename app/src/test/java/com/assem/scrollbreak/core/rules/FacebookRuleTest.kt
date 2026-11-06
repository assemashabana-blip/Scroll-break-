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
        val nodes = DumpParser.parse(text("facebook_video1.txt")).flatMap { it.nodes }
        assertEquals(1, nodes.count { n -> rule.redirectTo.any { it.matches(n) } })
    }

    // Arabic phone (Xiaomi, HyperOS, ar-EG): the tab bar has six tabs and Arabic labels such as
    // "ريلز، علامة التبويب ٢ من ٦" (Arabic comma and digits), so the English labels never match.

    @Test
    fun arabicReelsTabIsAPlayer() {
        val s = screen("facebook_ar_reels.txt")
        assertTrue(rule.isPlayer(s))
        assertEquals(listOf("reels_tab_selected"), rule.matchingFeedEntries(s))
    }

    @Test
    fun arabicHomeFeedIsNotAPlayer() {
        val s = screen("facebook_ar_home.txt")
        assertFalse(rule.isPlayer(s))
        assertTrue(rule.matchingFeedEntries(s).isEmpty())
    }

    @Test
    fun arabicRedirectFindsExactlyTheHomeTab() {
        val nodes = DumpParser.parse(text("facebook_ar_reels.txt")).flatMap { it.nodes }
        val hits = nodes.filter { n -> rule.redirectTo.any { it.matches(n) } }
        assertEquals(1, hits.size)
        assertTrue(hits.single().contentDescription!!.startsWith("الصفحة الرئيسية"))
    }

    @Test
    fun scrollSignalMatchesTheListUnderTheTabs() {
        val signal = rule.scrollSignal!!.copy(visible = false)
        val nodes = DumpParser.parse(text("facebook_video1.txt")).flatMap { it.nodes }
        assertTrue(nodes.any { signal.matches(it) })
        assertFalse(nodes.all { signal.matches(it) })
    }
}
