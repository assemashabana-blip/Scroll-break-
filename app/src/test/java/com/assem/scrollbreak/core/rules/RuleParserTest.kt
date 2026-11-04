package com.assem.scrollbreak.core.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RuleParserTest {
    private fun rule(player: String, extra: String = "") = """
        {"schemaVersion":1,"app":"t","packages":["p.q"],"playerSignature":$player $extra}
    """.trimIndent()

    private fun assertRejects(json: String, messagePart: String) {
        try {
            RuleParser.parse(json)
            fail("expected RuleParseException")
        } catch (e: RuleParseException) {
            assertTrue("'${e.message}' should contain '$messagePart'", e.message!!.contains(messagePart))
        }
    }

    @Test
    fun parsesLeafAndComposites() {
        val r = RuleParser.parse(
            rule("""{"anyOf":[{"viewId":"a:id/x"},{"allOf":[{"desc":"d"},{"not":{"text":"t","match":"equals"}}]}]}""")
        )
        assertEquals("t", r.app)
        assertEquals(setOf("p.q"), r.packages)
        val any = r.playerSignature as AnyOf
        assertEquals(Leaf(viewId = "a:id/x"), any.items[0])
        val all = any.items[1] as AllOf
        assertEquals(Not(Leaf(text = "t", match = MatchMode.EQUALS)), all.items[1])
    }

    @Test
    fun parsesFeedEntriesAndDm() {
        val r = RuleParser.parse(
            rule(
                """{"viewId":"a:id/x"}""",
                ""","feedEntrySignatures":[{"name":"tab","signature":{"desc":"Reels","selected":true}}],"dmSignature":{"text":"Message"}""",
            )
        )
        assertEquals("tab", r.feedEntrySignatures.single().name)
        assertNotNull(r.dmSignature)
    }

    @Test
    fun parsesFingerprintNodes() {
        val r = RuleParser.parse(
            rule(
                """{"viewId":"a:id/x"}""",
                ""","fingerprintNodes":[{"name":"channel","className":"Button","text":"@","match":"startsWith"},{"name":"title","className":"VG","text":"","clickable":true}]""",
            )
        )
        assertEquals(listOf("channel", "title"), r.fingerprintNodes.map { it.name })
        assertEquals(Leaf(className = "Button", text = "@", match = MatchMode.STARTS_WITH), r.fingerprintNodes[0].leaf)
        assertEquals(true, r.fingerprintNodes[1].leaf.clickable)
    }

    @Test
    fun rejectsBadFingerprintNode() {
        assertRejects(rule("""{"viewId":"a:id/x"}""", ""","fingerprintNodes":[{"className":"B"}]"""), "fingerprintNodes[0].name")
        assertRejects(rule("""{"viewId":"a:id/x"}""", ""","fingerprintNodes":[{"name":"n"}]"""), "fingerprintNodes[0]")
    }

    @Test
    fun parsesScrollSignal() {
        val r = RuleParser.parse(rule("""{"viewId":"a:id/x"}""", ""","scrollSignal":{"viewId":"a:id/pager"}"""))
        assertEquals(Leaf(viewId = "a:id/pager"), r.scrollSignal)
        assertNull(RuleParser.parse(rule("""{"viewId":"a:id/x"}""")).scrollSignal)
    }

    @Test
    fun rejectsBadScrollSignal() {
        assertRejects(rule("""{"viewId":"a:id/x"}""", ""","scrollSignal":{}"""), "scrollSignal")
        assertRejects(rule("""{"viewId":"a:id/x"}""", ""","scrollSignal":"x""""), "scrollSignal")
    }

    @Test
    fun dmIsOptional() {
        assertNull(RuleParser.parse(rule("""{"viewId":"a:id/x"}""")).dmSignature)
    }

    @Test
    fun ignoresUnknownTopLevelKeys() {
        RuleParser.parse(rule("""{"viewId":"a:id/x"}""", ""","futureThing":123"""))
    }

    @Test
    fun rejectsBrokenInput() {
        assertRejects("{not json", "invalid JSON")
        assertRejects("[]", "object")
        assertRejects("""{"schemaVersion":2,"app":"t","packages":["p"],"playerSignature":{"text":"x"}}""", "schemaVersion")
        assertRejects("""{"schemaVersion":1,"app":"t","packages":[],"playerSignature":{"text":"x"}}""", "packages")
        assertRejects("""{"schemaVersion":1,"app":"t","packages":["p"]}""", "playerSignature missing")
    }

    @Test
    fun rejectsBadSignatures() {
        assertRejects(rule("{}"), "at least one")
        assertRejects(rule("""{"viewId":"x","bogus":1}"""), "unknown key bogus")
        assertRejects(rule("""{"allOf":[]}"""), "must not be empty")
        assertRejects(rule("""{"allOf":[{"text":"a"}],"anyOf":[{"text":"b"}]}"""), "exactly one")
        assertRejects(rule("""{"text":"a","match":"fuzzy"}"""), "match must be")
        assertRejects(rule("""{"selected":"yes"}"""), "true or false")
    }

    @Test
    fun errorsNameThePath() {
        assertRejects(rule("""{"anyOf":[{"text":"a"},{"nope":1}]}"""), "playerSignature.anyOf[1]")
    }
}
