package com.assem.scrollbreak.core.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Runs the shipped TikTok rule against sanitised dumps captured from a real, logged-out phone. */
class TikTokRuleTest {
    private val pkg = "com.zhiliaoapp.musically"
    private val rule = RuleParser.parse(File("src/main/assets/rules/tiktok.json").readText())

    private fun screen(dump: String) =
        DumpParser.screenFor(DumpParser.parse(javaClass.getResource("/dumps/$dump")!!.readText()), pkg)

    @Test
    fun shippedRuleParses() {
        assertEquals("tiktok", rule.app)
        assertTrue(pkg in rule.packages)
        assertNotNull(rule.scrollSignal)
    }

    @Test
    fun feedIsAPlayerWithAnAuthor() {
        val s = screen("tiktok_feed.txt")
        assertTrue(rule.isPlayer(s))
        assertEquals(listOf("home_tab_selected"), rule.matchingFeedEntries(s))
        assertTrue(rule.fingerprint(s).values[0].isNotEmpty())
    }

    @Test
    fun signUpScreensAreNotPlayers() {
        for (dump in listOf("tiktok_profile.txt", "tiktok_inbox.txt")) {
            assertFalse(dump, rule.isPlayer(screen(dump)))
        }
    }

    @Test
    fun differentAuthorsGiveDifferentFingerprints() {
        val a = rule.fingerprint(screen("tiktok_feed.txt"))
        val other = javaClass.getResource("/dumps/tiktok_feed.txt")!!.readText()
            .replace("h_8e9354", "h_other00")
        val b = rule.fingerprint(DumpParser.screenFor(DumpParser.parse(other), pkg))
        assertTrue(a.differsFrom(b))
        assertFalse(a.differsFrom(rule.fingerprint(screen("tiktok_feed.txt"))))
    }

    @Test
    fun scrollSignalMatchesTheVideoPagerOnly() {
        val signal = rule.scrollSignal!!.copy(visible = false)
        val pagers = DumpParser.parse(
            javaClass.getResource("/dumps/tiktok_feed.txt")!!.readText(),
        ).flatMap { it.nodes }.filter { it.viewId == "com.zhiliaoapp.musically:id/viewpager" }
        assertTrue(pagers.any { signal.matches(it) })
        assertTrue("outer tab pagers must not match", pagers.any { !signal.matches(it) })
    }
}
