package com.assem.scrollbreak.core.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The shipped browser rule: URL shapes, plus real Chrome dumps (normal tab and Custom Tab). */
class BrowserRuleTest {
    private val pkg = "com.android.chrome"
    private val rule = RuleParser.parse(File("src/main/assets/rules/browsers.json").readText())

    private fun screen(dump: String): ScreenView {
        val text = javaClass.getResource("/dumps/$dump")!!.readText()
        return DumpParser.screenFor(DumpParser.parse(text), pkg)
    }

    private fun name(url: String) = rule.matchUrl(Urls.normalize(url)!!)?.name

    @Test
    fun shippedRuleParsesAsABrowserRule() {
        assertEquals("browsers", rule.app)
        assertTrue(rule.isBrowser)
        assertNull(rule.playerSignature)
        assertTrue(pkg in rule.packages)
    }

    @Test
    fun shortVideoUrlsAreRecognised() {
        assertEquals("youtube_shorts", name("m.youtube.com/shorts/uNDxxAm86W4"))
        assertEquals("youtube_shorts", name("https://www.youtube.com/shorts/abc?feature=share"))
        assertEquals("youtube_shorts", name("youtube.com/shorts/"))
        assertEquals("instagram_reels", name("instagram.com/reels/DfSKzL2s5fl/"))
        assertEquals("instagram_reels", name("www.instagram.com/reel/xyz"))
        assertEquals("tiktok", name("tiktok.com"))
        assertEquals("tiktok", name("www.tiktok.com/@scout2015/video/6718335390845095173"))
        assertEquals("tiktok", name("vm.tiktok.com/ZMabc"))
        assertEquals("facebook_reels", name("m.facebook.com/reel/1234567890"))
        assertEquals("facebook_reels", name("facebook.com/reels"))
        assertEquals("snapchat_spotlight", name("snapchat.com/spotlight"))
        assertEquals("snapchat_spotlight", name("snapchat.com/@someone/spotlight/W7_EDlXW"))
    }

    @Test
    fun ordinaryPagesAreNotBlocked() {
        for (url in listOf(
            "youtube.com", "youtube.com/watch?v=abc", "m.youtube.com/results?search_query=shorts",
            "youtube.com/feed/subscriptions", "youtu.be/abc",
            "instagram.com", "instagram.com/p/abc", "instagram.com/stories/someone", "instagram.com/someone",
            "facebook.com", "facebook.com/watch", "facebook.com/groups/reel/123",
            "snapchat.com", "snapchat.com/add/someone",
            "en.wikipedia.org/wiki/Android_%28operating_system%29",
            "google.com/search?q=tiktok.com", "example.com/redirect/tiktok.com/x",
        )) {
            assertNull(url, name(url))
        }
    }

    @Test
    fun lookalikeHostsAreNotBlocked() {
        assertNull(name("nottiktok.com"))
        assertNull(name("tiktok.com.evil.example/video"))
        assertNull(name("fakeyoutube.com/shorts/abc"))
        assertNull(name("evil.example/youtube.com/shorts/abc"))
    }

    @Test
    fun normalTabExposesTheFullUrl() {
        val url = Urls.normalize(rule.urlText(screen("chrome_tab_short.txt")))
        assertEquals("m.youtube.com/shorts/hGZj8FaVas4", url)
        assertEquals("youtube_shorts", rule.matchUrl(url!!)?.name)
    }

    @Test
    fun ordinaryArticleTabIsNotAPlayer() {
        val url = Urls.normalize(rule.urlText(screen("chrome_tab_article.txt")))
        assertNotNull(url)
        assertNull(rule.matchUrl(url!!))
    }

    @Test
    fun customTabShowsOnlyTheDomainSoWholeDomainRulesStillWork() {
        val url = Urls.normalize(rule.urlText(screen("chrome_cct_tiktok.txt")))
        assertEquals("tiktok.com", url)
        assertEquals("tiktok", rule.matchUrl(url!!)?.name)
    }

    @Test
    fun noUrlBarMeansNoUrl() {
        val empty = ScreenView { false }
        assertNull(rule.urlText(empty))
    }
}
