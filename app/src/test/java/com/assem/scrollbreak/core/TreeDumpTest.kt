package com.assem.scrollbreak.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeDumpTest {
    private class Fake(
        val snap: NodeSnapshot,
        val kids: List<Fake> = emptyList(),
    ) : NodeSource {
        var released = false
        override fun snapshot() = snap
        override fun child(index: Int): NodeSource? = kids.getOrNull(index)
        override fun release() { released = true }
    }

    private fun snap(
        id: String? = null, text: String? = null, desc: String? = null,
        children: Int = 0, flags: String = "",
    ) = NodeSnapshot(id, "android.view.View", text, desc, "[0,0][10,10]", children, flags)

    @Test
    fun writesOneIndentedLinePerNodeWithDepth() {
        val tree = Fake(snap("a:id/root", children = 1), listOf(Fake(snap("a:id/leaf", text = "hi"))))
        val sb = StringBuilder()
        val n = TreeDump.dump(tree, sb)
        assertEquals(2, n)
        val lines = sb.lines().filter { it.isNotEmpty() }
        assertTrue(lines[0].startsWith("[0] id=a:id/root"))
        assertTrue(lines[1].startsWith("  [1] id=a:id/leaf"))
        assertTrue(lines[1].contains("text=\"hi\""))
    }

    @Test
    fun releasesEveryNode() {
        val leaf = Fake(snap())
        val root = Fake(snap(children = 1), listOf(leaf))
        TreeDump.dump(root, StringBuilder())
        assertTrue(root.released && leaf.released)
    }

    @Test
    fun skipsNullChildren() {
        val root = Fake(snap(children = 2), listOf(Fake(snap())))
        assertEquals(2, TreeDump.dump(root, StringBuilder()))
    }

    @Test
    fun respectsNodeLimit() {
        val kids = List(10) { Fake(snap()) }
        val root = Fake(snap(children = 10), kids)
        val sb = StringBuilder()
        assertEquals(3, TreeDump.dump(root, sb, maxNodes = 3))
        assertTrue(sb.contains("node limit"))
    }

    @Test
    fun respectsDepthLimit() {
        val deep = Fake(snap(children = 1), listOf(Fake(snap())))
        val root = Fake(snap(children = 1), listOf(deep))
        val sb = StringBuilder()
        assertEquals(2, TreeDump.dump(root, sb, maxDepth = 1))
        assertTrue(sb.contains("depth limit"))
    }

    @Test
    fun escapesNewlinesAndTruncatesLongText() {
        val long = "x".repeat(TreeDump.MAX_TEXT + 50)
        val line = TreeDump.format(snap(text = "a\nb", desc = long))
        assertTrue(line.contains("text=\"a\\nb\""))
        assertTrue(line.contains("…"))
        assertFalse(line.contains('\n'))
    }
}
