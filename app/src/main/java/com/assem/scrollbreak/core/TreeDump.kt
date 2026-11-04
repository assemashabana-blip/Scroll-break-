package com.assem.scrollbreak.core

/** Plain-data view of one accessibility node, free of Android types. */
data class NodeSnapshot(
    val viewId: String?,
    val className: String?,
    val text: String?,
    val contentDescription: String?,
    val bounds: String,
    val childCount: Int,
    val flags: String,
) {
    fun hasFlag(flag: String): Boolean = flags.split(',').contains(flag)
}

/** Abstraction over a node tree so the walker can be unit-tested without Android. */
interface NodeSource {
    fun snapshot(): NodeSnapshot
    fun child(index: Int): NodeSource?
    /** Called once the node and its children have been visited. */
    fun release() {}
}

object TreeDump {
    const val MAX_TEXT = 300

    /** Appends one line per node (indented by depth) to [out]; returns the number of nodes written. */
    fun dump(
        root: NodeSource,
        out: StringBuilder,
        maxDepth: Int = 60,
        maxNodes: Int = 6000,
    ): Int {
        var count = 0
        fun visit(node: NodeSource, depth: Int) {
            if (count >= maxNodes) return
            count++
            val s = node.snapshot()
            out.append("  ".repeat(depth)).append('[').append(depth).append("] ")
                .append(format(s)).append('\n')
            if (depth >= maxDepth) {
                if (s.childCount > 0) {
                    out.append("  ".repeat(depth + 1)).append("... depth limit, ")
                        .append(s.childCount).append(" children skipped\n")
                }
            } else {
                for (i in 0 until s.childCount) {
                    if (count >= maxNodes) {
                        out.append("  ".repeat(depth + 1)).append("... node limit reached\n")
                        break
                    }
                    val c = node.child(i) ?: continue
                    visit(c, depth + 1)
                }
            }
            node.release()
        }
        visit(root, 0)
        return count
    }

    fun format(s: NodeSnapshot): String = buildString {
        append("id=").append(s.viewId ?: "-")
        append(" cls=").append(s.className ?: "-")
        s.text?.let { append(" text=").append(quote(it)) }
        s.contentDescription?.let { append(" desc=").append(quote(it)) }
        append(" bounds=").append(s.bounds)
        append(" children=").append(s.childCount)
        if (s.flags.isNotEmpty()) append(" flags=").append(s.flags)
    }

    private fun quote(v: String): String {
        val oneLine = v.replace("\r", "").replace("\n", "\\n").replace("\"", "'")
        val cut = if (oneLine.length > MAX_TEXT) oneLine.take(MAX_TEXT) + "…" else oneLine
        return "\"$cut\""
    }
}
