package com.assem.scrollbreak.core.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FingerprintTest {
    private fun fp(vararg v: String) = Fingerprint(v.toList())

    @Test
    fun differsOnlyWhenBothComponentsPresentAndDifferent() {
        assertTrue(fp("a", "x").differsFrom(fp("b", "x")))
        assertFalse(fp("a", "x").differsFrom(fp("a", "x")))
        assertFalse(fp("a", "").differsFrom(fp("a", "x")))
        assertFalse(fp("", "").differsFrom(fp("a", "x")))
        assertFalse(fp("a", "x").differsFrom(Fingerprint.EMPTY))
    }

    @Test
    fun fillGapsKeepsExistingAndAddsMissing() {
        assertEquals(fp("a", "x"), fp("a", "").fillGapsFrom(fp("zz", "x")))
        assertEquals(fp("a", "x"), Fingerprint.EMPTY.fillGapsFrom(fp("a", "x")))
    }

    @Test
    fun emptyDetection() {
        assertTrue(fp("", "").isEmpty)
        assertTrue(Fingerprint.EMPTY.isEmpty)
        assertFalse(fp("", "x").isEmpty)
    }

    @Test
    fun hashIsStableAndDiscriminates() {
        assertEquals(fp("a", "b").hash(), fp("a", "b").hash())
        assertNotEquals(fp("a", "b").hash(), fp("a", "c").hash())
        assertNotEquals(fp("ab", "").hash(), fp("a", "b").hash())
    }

    @Test
    fun computeReadsTextOfEachNode() {
        val screen = object : ScreenView {
            override fun has(leaf: Leaf) = true
            override fun textOf(leaf: Leaf) = if (leaf.className == "Button") "@chan" else null
        }
        val nodes = listOf(
            FingerprintNode("channel", Leaf(className = "Button")),
            FingerprintNode("title", Leaf(className = "Title")),
        )
        assertEquals(fp("@chan", ""), Fingerprint.compute(nodes, screen))
    }
}
