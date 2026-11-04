package com.assem.scrollbreak.core.rules

/** One node whose text helps identify the current video (author handle, caption, ...). */
data class FingerprintNode(val name: String, val leaf: Leaf)

/**
 * Identity of the video on screen: one value per fingerprint node ("" while that node is absent).
 * Comparison is per component, so a node that has not loaded yet (empty to A) is not a change.
 */
class Fingerprint(val values: List<String>) {
    val isEmpty: Boolean get() = values.all { it.isEmpty() }

    /** True when some component is non-empty in both and differs. */
    fun differsFrom(old: Fingerprint): Boolean = values.indices.any { i ->
        val a = values[i]
        val b = old.values.getOrElse(i) { "" }
        a.isNotEmpty() && b.isNotEmpty() && a != b
    }

    /** This fingerprint with its empty components filled from [other]. */
    fun fillGapsFrom(other: Fingerprint): Fingerprint =
        Fingerprint((0 until maxOf(values.size, other.values.size)).map { i ->
            values.getOrElse(i) { "" }.ifEmpty { other.values.getOrElse(i) { "" } }
        })

    /** Short stable hash for logs; the raw text is never logged. */
    fun hash(): String {
        var h = -0x340d631b7bdddcdbL // FNV-1a 64-bit offset basis
        for (c in values.joinToString("\u0000")) {
            h = (h xor c.code.toLong()) * 0x100000001b3L
        }
        return java.lang.Long.toHexString(h).takeLast(8)
    }

    override fun equals(other: Any?) = other is Fingerprint && values == other.values
    override fun hashCode() = values.hashCode()

    companion object {
        val EMPTY = Fingerprint(emptyList())

        fun compute(nodes: List<FingerprintNode>, screen: ScreenView): Fingerprint =
            if (nodes.isEmpty()) EMPTY
            else Fingerprint(nodes.map { screen.textOf(it.leaf).orEmpty() })
    }
}
