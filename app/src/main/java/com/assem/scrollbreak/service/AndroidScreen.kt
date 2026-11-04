package com.assem.scrollbreak.service

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.assem.scrollbreak.core.NodeSnapshot
import com.assem.scrollbreak.core.rules.Leaf
import com.assem.scrollbreak.core.rules.ScreenView

/**
 * [ScreenView] over every window of one package. A leaf that only names a view id is answered by
 * a targeted lookup; anything else walks the tree once (capped) and reuses that index.
 */
class AndroidScreen private constructor(private val roots: List<AccessibilityNodeInfo>) : ScreenView {

    private val index: List<NodeSnapshot> by lazy { buildIndex() }

    val isEmpty: Boolean get() = roots.isEmpty()

    override fun has(leaf: Leaf): Boolean {
        if (leaf.isViewIdOnly) return roots.any { hasViewId(it, leaf) }
        return index.any { leaf.matches(it) }
    }

    override fun textOf(leaf: Leaf): String? {
        val node = index.firstOrNull { leaf.matches(it) } ?: return null
        return (node.text ?: node.contentDescription)?.takeIf { it.isNotEmpty() }
    }

    private fun hasViewId(root: AccessibilityNodeInfo, leaf: Leaf): Boolean {
        val found = root.findAccessibilityNodeInfosByViewId(leaf.viewId!!) ?: return false
        val hit = found.any { !leaf.visible || it.isVisibleToUser }
        found.forEach(::release)
        return hit
    }

    private fun buildIndex(): List<NodeSnapshot> {
        val out = ArrayList<NodeSnapshot>()
        fun walk(info: AccessibilityNodeInfo, depth: Int) {
            if (out.size >= MAX_NODES) return
            out += AndroidNode(info).snapshot()
            if (depth >= MAX_DEPTH) return
            for (i in 0 until info.childCount) {
                val child = info.getChild(i) ?: continue
                walk(child, depth + 1)
                release(child)
            }
        }
        roots.forEach { walk(it, 0) }
        return out
    }

    fun close() = roots.forEach(::release)

    companion object {
        private const val MAX_NODES = 3000
        private const val MAX_DEPTH = 60

        /** Roots of all windows belonging to [pkg]; split-screen and PiP windows are included. */
        fun forPackage(service: AccessibilityService, pkg: String): AndroidScreen {
            val roots = ArrayList<AccessibilityNodeInfo>()
            for (w in service.windows) {
                val root = w.root ?: continue
                if (root.packageName == pkg) roots += root else release(root)
            }
            return AndroidScreen(roots)
        }

        @Suppress("DEPRECATION")
        private fun release(info: AccessibilityNodeInfo) {
            if (Build.VERSION.SDK_INT < 33) info.recycle()
        }
    }
}
