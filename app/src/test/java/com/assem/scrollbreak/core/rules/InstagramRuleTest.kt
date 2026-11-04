package com.assem.scrollbreak.core.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Runs the shipped Instagram rule against sanitised dumps captured from a real phone. */
class InstagramRuleTest {
    private val pkg = "com.instagram.android"
    private val rule = RuleParser.parse(File("src/main/assets/rules/instagram.json").readText())

    private fun text(dump: String) = javaClass.getResource("/dumps/$dump")!!.readText()
    private fun screen(dump: String) = DumpParser.screenFor(DumpParser.parse(text(dump)), pkg)

    @Test
    fun shippedRuleParses() {
        assertEquals("instagram", rule.app)
        assertTrue(pkg in rule.packages)
        assertNotNull(rule.scrollSignal)
    }

    @Test
    fun detectsReelsPlayer() {
        for (dump in listOf("instagram_reel1.txt", "instagram_reel2.txt")) {
            val s = screen(dump)
            assertTrue(dump, rule.isPlayer(s))
            assertEquals(dump, listOf("reels_tab_selected"), rule.matchingFeedEntries(s))
        }
    }

    @Test
    fun homeFeedAndInboxAreNotPlayers() {
        for (dump in listOf("instagram_home.txt", "instagram_inbox.txt")) {
            val s = screen(dump)
            assertFalse(dump, rule.isPlayer(s))
            assertTrue(dump, rule.matchingFeedEntries(s).isEmpty())
        }
    }

    @Test
    fun fingerprintTellsTheTwoReelsApart() {
        val a = rule.fingerprint(screen("instagram_reel1.txt"))
        val b = rule.fingerprint(screen("instagram_reel2.txt"))
        assertTrue("author present", a.values[0].isNotEmpty() && b.values[0].isNotEmpty())
        assertTrue(a.differsFrom(b))
        assertFalse(a.differsFrom(rule.fingerprint(screen("instagram_reel1.txt"))))
    }

    @Test
    fun homeAndInboxHaveNoFingerprintAuthor() {
        assertEquals("", rule.fingerprint(screen("instagram_home.txt")).values[0])
        assertEquals("", rule.fingerprint(screen("instagram_inbox.txt")).values[0])
    }

    @Test
    fun descriptionFallbackNeedsTheReelsTabToo() {
        // Simulates Instagram renaming every clips_* id: the fallback can no longer see the tab.
        val renamed = text("instagram_reel1.txt").replace(":id/clips_", ":id/renamed_")
        val s = DumpParser.screenFor(DumpParser.parse(renamed), pkg)
        assertFalse(rule.isPlayer(s))
        // Same dump with only the player ids renamed but the tab kept: the fallback still fires.
        val tabKept = text("instagram_reel1.txt")
            .replace("clips_viewer_view_pager", "renamed_pager")
            .replace("clips_viewer_container", "renamed_container")
        assertTrue(rule.isPlayer(DumpParser.screenFor(DumpParser.parse(tabKept), pkg)))
    }

    @Test
    fun scrollSignalMatchesTheViewerPagerOnly() {
        val signal = rule.scrollSignal!!.copy(visible = false)
        val nodes = DumpParser.parse(text("instagram_reel1.txt")).flatMap { it.nodes }
        val pagers = nodes.filter { it.className == "androidx.viewpager.widget.ViewPager" }
        assertTrue(pagers.any { signal.matches(it) })
        assertTrue("the tab pager must not match", pagers.any { !signal.matches(it) })
    }

    @Test
    fun dmThreadIsAnAllowSourceNotAPlayer() {
        val s = screen("instagram_dm_thread.txt")
        assertFalse(rule.isPlayer(s))
        assertEquals("dm", rule.matchingSource(s))
    }

    @Test
    fun storyViewerIsAnAllowSourceNotAPlayer() {
        val s = screen("instagram_story.txt")
        assertFalse(rule.isPlayer(s))
        assertEquals("story", rule.matchingSource(s))
    }

    @Test
    fun readingStoriesAndDmsIsNeverBlocked() {
        // Neither screen may look like a player, or just viewing them would be blocked.
        for (dump in listOf("instagram_dm_thread.txt", "instagram_story.txt")) {
            assertFalse(dump, rule.isPlayer(screen(dump)))
        }
    }

    @Test
    fun reelOpenedFromDmIsAPlayerWithAFingerprint() {
        val s = screen("instagram_dm_reel.txt")
        assertTrue(rule.isPlayer(s))
        assertTrue(rule.fingerprint(s).values[0].isNotEmpty())
        assertEquals(null, rule.matchingSource(s))   // the player is not itself a source
    }

    @Test
    fun reelOpenedFromStoryIsAPlayer() {
        val s = screen("instagram_story_ad_reel.txt")
        assertTrue(rule.isPlayer(s))
        assertEquals(null, rule.matchingSource(s))
    }

    @Test
    fun otherScreensAreNotSources() {
        for (dump in listOf("instagram_home.txt", "instagram_inbox.txt", "instagram_reel1.txt")) {
            assertEquals(dump, null, rule.matchingSource(screen(dump)))
        }
    }
}
