package com.assem.scrollbreak.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSitesTest {
    @Test
    fun aSiteAndItsSubdomainsBelongToTheApp() {
        assertEquals(listOf("com.instagram.android", "com.instagram.lite"), WebSites.packagesFor("instagram.com"))
        assertEquals(listOf("com.instagram.android", "com.instagram.lite"), WebSites.packagesFor("www.instagram.com"))
        assertTrue("com.instagram.android" in WebSites.packagesFor("m.instagram.com"))
        assertTrue("com.google.android.youtube" in WebSites.packagesFor("youtu.be"))
        assertTrue("com.facebook.katana" in WebSites.packagesFor("m.facebook.com"))
    }

    @Test
    fun lookalikeHostsDoNotMatch() {
        assertTrue(WebSites.packagesFor("notinstagram.com").isEmpty())
        assertTrue(WebSites.packagesFor("instagram.com.evil.example").isEmpty())
        assertTrue(WebSites.packagesFor("example.com").isEmpty())
        assertTrue(WebSites.packagesFor("").isEmpty())
    }

    @Test
    fun theMatchIsCaseInsensitiveAndIgnoresATrailingDot() {
        assertTrue(WebSites.packagesFor("WWW.Instagram.COM").isNotEmpty())
        assertTrue(WebSites.packagesFor("instagram.com.").isNotEmpty())
    }

    @Test
    fun youtubeMusicIsBothSitesOfItsOwnAndOfYoutube() {
        val owners = WebSites.packagesFor("music.youtube.com")
        assertTrue("com.google.android.youtube" in owners)
        assertTrue("com.google.android.apps.youtube.music" in owners)
    }

    @Test
    fun onlyAnAppThatIsLimitedMakesItsSiteLimited() {
        val limited = setOf("com.instagram.android")
        assertTrue(WebSites.anyLimited("www.instagram.com", limited))
        assertFalse(WebSites.anyLimited("www.youtube.com", limited))
        assertFalse(WebSites.anyLimited("www.instagram.com", emptySet()))
    }

    @Test
    fun anAppWithoutAWebVersionHasNoDomains() {
        assertTrue(WebSites.domainsOf("com.example.unknown").isEmpty())
        assertEquals(listOf("tiktok.com"), WebSites.domainsOf("com.zhiliaoapp.musically"))
    }

    @Test
    fun hostOfANormalizedUrl() {
        assertEquals("www.instagram.com", WebSites.hostOf("www.instagram.com/reels/abc"))
        assertEquals("instagram.com", WebSites.hostOf("instagram.com"))
        assertEquals("localhost", WebSites.hostOf("localhost:8080/x"))
    }

    @Test
    fun timeOnASiteIsCountedBetweenLooks() {
        val t = WebTimeTracker()
        assertEquals(0L, t.tick(true, 1_000))           // first look: nothing before it
        assertEquals(5_000L, t.tick(true, 6_000))
        assertEquals(4_000L, t.tick(true, 10_000))
    }

    @Test
    fun aLongGapIsNotCountedAsTimeOnTheSite() {
        val t = WebTimeTracker(capMs = 15_000)
        t.tick(true, 0)
        assertEquals(15_000L, t.tick(true, 600_000))    // ten minutes later: at most the cap
    }

    @Test
    fun leavingTheSiteStopsAndRestartsTheClock() {
        val t = WebTimeTracker()
        t.tick(true, 0)
        assertEquals(0L, t.tick(false, 3_000))          // left the site
        assertEquals(0L, t.tick(true, 8_000))           // back: the away time is not counted
        assertEquals(2_000L, t.tick(true, 10_000))
    }
}
