package com.assem.scrollbreak.core.rules

import com.assem.scrollbreak.core.NodeSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignatureTest {
    private fun node(
        id: String? = null, cls: String? = "android.view.View",
        text: String? = null, desc: String? = null, flags: String = "",
    ) = NodeSnapshot(id, cls, text, desc, "[0,0][1,1]", 0, flags)

    private fun screen(vararg nodes: NodeSnapshot) = ScreenView { leaf -> nodes.any { leaf.matches(it) } }

    @Test
    fun viewIdMatchesExactly() {
        val s = screen(node(id = "a:id/x"))
        assertTrue(Leaf(viewId = "a:id/x").evaluate(s))
        assertFalse(Leaf(viewId = "a:id/xy").evaluate(s))
    }

    @Test
    fun textAndDescDefaultToContains() {
        val s = screen(node(text = "Hello world", desc = "View 12 comments"))
        assertTrue(Leaf(text = "world").evaluate(s))
        assertTrue(Leaf(desc = " comments").evaluate(s))
        assertFalse(Leaf(text = "worlds").evaluate(s))
    }

    @Test
    fun equalsAndStartsWithModes() {
        val s = screen(node(desc = "Shorts tab"))
        assertFalse(Leaf(desc = "Shorts", match = MatchMode.EQUALS).evaluate(s))
        assertTrue(Leaf(desc = "Shorts", match = MatchMode.STARTS_WITH).evaluate(s))
    }

    @Test
    fun allConstraintsMustHoldOnTheSameNode() {
        val s = screen(node(id = "a:id/x", cls = "A"), node(id = "a:id/y", cls = "B"))
        assertTrue(Leaf(viewId = "a:id/x", className = "A").evaluate(s))
        assertFalse(Leaf(viewId = "a:id/x", className = "B").evaluate(s))
    }

    @Test
    fun invisibleNodesIgnoredUnlessAsked() {
        val s = screen(node(id = "a:id/x", flags = "clickable,invisible"))
        assertFalse(Leaf(viewId = "a:id/x").evaluate(s))
        assertTrue(Leaf(viewId = "a:id/x", visible = false).evaluate(s))
    }

    @Test
    fun selectedFlag() {
        val s = screen(node(desc = "Shorts", flags = "clickable,selected"))
        assertTrue(Leaf(desc = "Shorts", selected = true).evaluate(s))
        assertFalse(Leaf(desc = "Shorts", selected = false).evaluate(s))
    }

    @Test
    fun viewIdOnlyDetection() {
        assertTrue(Leaf(viewId = "a:id/x").isViewIdOnly)
        assertFalse(Leaf(viewId = "a:id/x", text = "t").isViewIdOnly)
        assertFalse(Leaf(text = "t").isViewIdOnly)
    }

    @Test
    fun allOfAnyOfNot() {
        val s = screen(node(text = "a"), node(text = "b"))
        val a = Leaf(text = "a")
        val b = Leaf(text = "b")
        val c = Leaf(text = "c")
        assertTrue(AllOf(listOf(a, b)).evaluate(s))
        assertFalse(AllOf(listOf(a, c)).evaluate(s))
        assertTrue(AnyOf(listOf(c, b)).evaluate(s))
        assertFalse(AnyOf(listOf(c, c)).evaluate(s))
        assertTrue(Not(c).evaluate(s))
        assertFalse(Not(a).evaluate(s))
    }

    @Test
    fun hasFlagIsExact() {
        assertEquals(false, node(flags = "invisibleish").hasFlag("invisible"))
    }
}
