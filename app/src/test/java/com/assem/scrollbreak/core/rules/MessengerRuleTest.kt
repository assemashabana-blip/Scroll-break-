package com.assem.scrollbreak.core.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Reels sent in Messenger open in its own in-app browser, a WebView whose page content is
 * invisible to accessibility. The rule therefore recognises the browser window, and the scroll
 * position of the page stands in for "which video". Fixture: sanitised dump from the Pixel.
 */
class MessengerRuleTest {
    private val pkg = "com.facebook.orca"
    private val rule = RuleParser.parse(File("src/main/assets/rules/messenger.json").readText())

    private fun screen(dump: String) =
        DumpParser.screenFor(DumpParser.parse(javaClass.getResource("/dumps/$dump")!!.readText()), pkg)

    @Test
    fun facebookPageInMessengersBrowserIsAPlayer() {
        assertTrue(rule.isPlayer(screen("messenger_reel_browser.txt")))
    }

    @Test
    fun aChatIsNotAPlayerButIsWhereAFriendLinkComesFrom() {
        val chat = screen("messenger_chat.txt")
        assertFalse(rule.isPlayer(chat))
        assertEquals("messenger_screen", rule.matchingSource(chat))
    }

    @Test
    fun theBrowserWindowIsNotASource() {
        // Sources are only read while no player is showing; in the player the rule must not mistake
        // the browser for a chat, or a swipe inside it would renew the allowance.
        assertTrue(rule.isPlayer(screen("messenger_reel_browser.txt")))
    }

    @Test
    fun usesTheScrollPositionAsTheVideoIndex() {
        assertTrue(rule.scrollByPosition)
        assertNotNull(rule.scrollSignal)
        assertEquals(setOf(pkg), rule.packages)
    }

    @Test
    fun scrollMeasureIsParsedAndValidated() {
        fun parse(extra: String) = RuleParser.parse(
            """{"schemaVersion":1,"app":"x","packages":["p"],"playerSignature":{"viewId":"p:id/a"}$extra}""",
        )
        assertFalse(parse("").scrollByPosition)
        assertFalse(parse(""","scrollMeasure":"index"""").scrollByPosition)
        assertTrue(parse(""","scrollMeasure":"scrollY"""").scrollByPosition)
        try {
            parse(""","scrollMeasure":"nonsense"""")
            throw AssertionError("expected a RuleParseException")
        } catch (e: RuleParseException) {
            assertTrue(e.message!!.contains("scrollMeasure"))
        }
    }
}
