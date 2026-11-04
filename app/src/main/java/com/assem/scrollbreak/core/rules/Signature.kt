package com.assem.scrollbreak.core.rules

import com.assem.scrollbreak.core.NodeSnapshot

/** Answers "does a node matching this leaf exist on screen?" for one app's windows. */
fun interface ScreenView {
    fun has(leaf: Leaf): Boolean

    /** Text (or content description) of the first visible node matching [leaf], if any. */
    fun textOf(leaf: Leaf): String? = null
}

enum class MatchMode { EQUALS, CONTAINS, STARTS_WITH }

/** A boolean test over a screen: a [Leaf] or an AND/OR/NOT combination of signatures. */
sealed interface Signature {
    fun evaluate(screen: ScreenView): Boolean
}

/**
 * One node constraint. All given fields must hold on the same node.
 * [text] and [desc] use [match]; view id and class name are always exact.
 */
data class Leaf(
    val viewId: String? = null,
    val className: String? = null,
    val text: String? = null,
    val desc: String? = null,
    val match: MatchMode = MatchMode.CONTAINS,
    val selected: Boolean? = null,
    val clickable: Boolean? = null,
    /** Ignore nodes flagged invisible (e.g. pager pages pre-rendered off screen). */
    val visible: Boolean = true,
) : Signature {

    /** True when only the view id is constrained, so a targeted lookup can answer it. */
    val isViewIdOnly: Boolean
        get() = viewId != null && className == null && text == null && desc == null &&
            selected == null && clickable == null

    fun matches(n: NodeSnapshot): Boolean {
        if (visible && n.hasFlag("invisible")) return false
        if (viewId != null && n.viewId != viewId) return false
        if (className != null && n.className != className) return false
        if (text != null && !matchString(n.text, text)) return false
        if (desc != null && !matchString(n.contentDescription, desc)) return false
        if (selected != null && n.hasFlag("selected") != selected) return false
        if (clickable != null && n.hasFlag("clickable") != clickable) return false
        return true
    }

    private fun matchString(actual: String?, expected: String): Boolean = actual != null && when (match) {
        MatchMode.EQUALS -> actual == expected
        MatchMode.CONTAINS -> actual.contains(expected)
        MatchMode.STARTS_WITH -> actual.startsWith(expected)
    }

    override fun evaluate(screen: ScreenView) = screen.has(this)
}

data class AllOf(val items: List<Signature>) : Signature {
    override fun evaluate(screen: ScreenView) = items.all { it.evaluate(screen) }
}

data class AnyOf(val items: List<Signature>) : Signature {
    override fun evaluate(screen: ScreenView) = items.any { it.evaluate(screen) }
}

data class Not(val item: Signature) : Signature {
    override fun evaluate(screen: ScreenView) = !item.evaluate(screen)
}
