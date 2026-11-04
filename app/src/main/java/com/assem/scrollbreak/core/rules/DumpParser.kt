package com.assem.scrollbreak.core.rules

import com.assem.scrollbreak.core.NodeSnapshot

class DumpWindow(val pkg: String, val nodes: List<NodeSnapshot>)

/** Reads files written by the screen dump tool back into nodes, so rules can be tested on real dumps. */
object DumpParser {
    private val windowRe = Regex("""^=== WINDOW .*? pkg=(\S+) """)
    private val nodeRe = Regex(
        """^\s*\[\d+] id=(\S+) cls=(\S+)(?: text="([^"]*)")?(?: desc="([^"]*)")? bounds=(\S+) children=(\d+)(?: flags=(\S+))?$"""
    )

    fun parse(text: String): List<DumpWindow> {
        val windows = ArrayList<DumpWindow>()
        var pkg: String? = null
        var nodes = ArrayList<NodeSnapshot>()
        fun flush() {
            pkg?.let { windows += DumpWindow(it, nodes) }
            nodes = ArrayList()
        }
        for (line in text.lineSequence()) {
            val window = windowRe.find(line)
            if (window != null) {
                flush()
                pkg = window.groupValues[1]
                continue
            }
            val m = nodeRe.find(line) ?: continue
            val g = m.groupValues
            nodes += NodeSnapshot(
                viewId = g[1].takeIf { it != "-" },
                className = g[2].takeIf { it != "-" },
                text = m.groups[3]?.value,
                contentDescription = m.groups[4]?.value,
                bounds = g[5],
                childCount = g[6].toInt(),
                flags = g[7],
            )
        }
        flush()
        return windows
    }

    /** A [ScreenView] over all windows of [pkg] in a parsed dump. */
    fun screenFor(windows: List<DumpWindow>, pkg: String): ScreenView {
        val nodes = windows.filter { it.pkg == pkg }.flatMap { it.nodes }
        return ScreenView { leaf -> nodes.any { leaf.matches(it) } }
    }
}
