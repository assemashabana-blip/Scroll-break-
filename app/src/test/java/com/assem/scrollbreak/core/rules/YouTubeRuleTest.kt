package com.assem.scrollbreak.core.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Runs the shipped YouTube rule against dumps captured from a real phone. */
class YouTubeRuleTest {
    private val pkg = "com.google.android.youtube"
    private val rule = RuleParser.parse(File("src/main/assets/rules/youtube.json").readText())

    private fun screen(dump: String): ScreenView {
        val text = javaClass.getResource("/dumps/$dump")!!.readText()
        return DumpParser.screenFor(DumpParser.parse(text), pkg)
    }

    @Test
    fun shippedRuleParses() {
        assertEquals("youtube", rule.app)
        assertEquals(setOf(pkg), rule.packages)
    }

    @Test
    fun detectsShortsPlayer() {
        val s = screen("youtube_short.txt")
        assertTrue(rule.isPlayer(s))
        assertEquals(listOf("shorts_tab_selected"), rule.matchingFeedEntries(s))
    }

    @Test
    fun fingerprintIdentifiesEachRealShort() {
        val a = rule.fingerprint(screen("youtube_short.txt"))
        val b = rule.fingerprint(screen("youtube_short2.txt"))
        assertEquals(listOf("@athletor", "Bo Bassett Puts His Own Teammate on His Wrestling Mount Rushmore"), a.values)
        assertEquals("@otgun2024", b.values[0])
        assertTrue(b.values[1].startsWith("When Acting in the UFC Backfires"))
        assertTrue(a.differsFrom(b))
        assertFalse(a.differsFrom(rule.fingerprint(screen("youtube_short.txt"))))
    }

    @Test
    fun sponsoredShortHasNoFingerprintButIsStillAPlayer() {
        val ad = screen("youtube_short_ad.txt")
        assertTrue(rule.isPlayer(ad))
        assertTrue(rule.fingerprint(ad).isEmpty)
    }

    @Test
    fun homeFeedHasNoFingerprint() {
        assertTrue(rule.fingerprint(screen("youtube_home.txt")).isEmpty)
    }

    @Test
    fun homeFeedIsNotAPlayer() {
        val s = screen("youtube_home.txt")
        assertFalse(rule.isPlayer(s))
        // The Shorts tab exists on Home but is not the selected tab.
        assertTrue(rule.matchingFeedEntries(s).isEmpty())
    }

    @Test
    fun descriptionFallbackAloneDetectsPlayer() {
        // Simulates YouTube renaming every reel_* id: only content descriptions remain.
        val s = screen("youtube_short.txt")
        val fallback = (rule.playerSignature as AnyOf).items.last()
        assertTrue(fallback.evaluate(s))
    }

    @Test
    fun renamedIdsStillDetectedByFallback() {
        val text = javaClass.getResource("/dumps/youtube_short.txt")!!.readText().replace(":id/reel_", ":id/renamed_")
        val s = DumpParser.screenFor(DumpParser.parse(text), pkg)
        assertTrue(rule.isPlayer(s))
    }

    @Test
    fun ignoresOtherPackages() {
        val text = javaClass.getResource("/dumps/youtube_short.txt")!!.readText()
        val s = DumpParser.screenFor(DumpParser.parse(text), "com.other.app")
        assertFalse(rule.isPlayer(s))
    }
}
