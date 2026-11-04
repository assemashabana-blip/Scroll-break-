package com.assem.scrollbreak.core.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlsTest {
    @Test
    fun normalizeStripsSchemeQueryFragmentAndTrailingSlash() {
        assertEquals("youtube.com/shorts/abc", Urls.normalize("https://youtube.com/shorts/abc?feature=share#t=1"))
        assertEquals("example.com", Urls.normalize("http://example.com/"))
        assertEquals("example.com/a/b", Urls.normalize("example.com/a/b/"))
        assertEquals("example.com:8080/x", Urls.normalize("example.com:8080/x"))
    }

    @Test
    fun hostIsLowercasedButPathKeepsItsCase() {
        assertEquals("youtube.com/shorts/AbC_dEf", Urls.normalize("YouTube.COM/shorts/AbC_dEf"))
    }

    @Test
    fun nonUrlsAreRejected() {
        assertNull(Urls.normalize(null))
        assertNull(Urls.normalize(""))
        assertNull(Urls.normalize("Search or type web address"))
        assertNull(Urls.normalize("how to cook rice"))
        assertNull(Urls.normalize("localhost"))
        assertNull(Urls.normalize("tiktok"))
    }

    @Test
    fun trackerReturnsAndCachesRealUrls() {
        val t = UrlTracker()
        assertEquals("a.com/x", t.observe("chrome", "a.com/x"))
        assertEquals("b.com", t.observe("chrome", "b.com"))
    }

    @Test
    fun hiddenUrlBarKeepsTheLastUrl() {
        val t = UrlTracker()
        t.observe("chrome", "youtube.com/shorts/abc")
        assertEquals("youtube.com/shorts/abc", t.observe("chrome", null))
        assertEquals("youtube.com/shorts/abc", t.observe("chrome", null))
    }

    @Test
    fun searchBoxClearsTheCachedUrl() {
        val t = UrlTracker()
        t.observe("chrome", "tiktok.com")
        assertNull(t.observe("chrome", "Search or type web address"))
        assertNull(t.observe("chrome", null))
    }

    @Test
    fun forgetAndPerPackageIsolation() {
        val t = UrlTracker()
        t.observe("chrome", "a.com")
        t.observe("firefox", "b.com")
        t.forget("chrome")
        assertNull(t.observe("chrome", null))
        assertEquals("b.com", t.observe("firefox", null))
    }
}
