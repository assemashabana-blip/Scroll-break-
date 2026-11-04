package com.assem.scrollbreak.service

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
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
        if (leaf.isViewIdOnly) return roots.firstNotNullOfOrNull { textByViewId(it, leaf) }
        val node = index.firstOrNull { leaf.matches(it) } ?: return null
        return (node.text ?: node.contentDescription)?.takeIf { it.isNotEmpty() }
    }

    private fun textByViewId(root: AccessibilityNodeInfo, leaf: Leaf): String? {
        val found = root.findAccessibilityNodeInfosByViewId(leaf.viewId!!) ?: return null
        val node = found.firstOrNull { !leaf.visible || it.isVisibleToUser }
        val text = (node?.text ?: node?.contentDescription)?.toString()?.takeIf { it.isNotEmpty() }
        found.forEach(::release)
        return text
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

    /** Clicks the first clickable node matching [leaf]; false if none was found or the click failed. */
    fun click(leaf: Leaf): Boolean {
        var budget = MAX_NODES
        fun walk(info: AccessibilityNodeInfo, depth: Int): Boolean {
            if (budget-- <= 0) return false
            if (info.isClickable && leaf.matches(AndroidNode(info).snapshot()) &&
                info.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            ) return true
            if (depth >= MAX_DEPTH) return false
            for (i in 0 until info.childCount) {
                val child = info.getChild(i) ?: continue
                val hit = walk(child, depth + 1)
                release(child)
                if (hit) return true
            }
            return false
        }
        return roots.any { walk(it, 0) }
    }

    fun close() = roots.forEach(::release)

    companion object {
        private const val MAX_NODES = 3000
        private const val MAX_DEPTH = 60

        /**
         * Roots of all windows belonging to [pkg], on every display, so split-screen, floating and
         * PiP windows are all seen. A window's package never changes, so it is learned once per
         * window id in [cache]; afterwards only [pkg]'s own windows cost a call.
         */
        fun forPackage(service: AccessibilityService, pkg: String, cache: WindowPackageCache): AndroidScreen {
            val roots = ArrayList<AccessibilityNodeInfo>()
            val windows = allWindows(service)
            cache.retainOnly(windows.map { it.id }.toSet())
            for (w in windows) {
                val known = cache.get(w.id)
                if (known != null) {
                    if (known == pkg) w.root?.let { roots += it }
                    continue
                }
                val root = w.root ?: continue
                val owner = root.packageName?.toString()
                if (owner != null) cache.put(w.id, owner)
                if (owner == pkg) roots += root else release(root)
            }
            return AndroidScreen(roots)
        }

        private fun allWindows(service: AccessibilityService): List<AccessibilityWindowInfo> {
            if (Build.VERSION.SDK_INT >= 30) {
                val byDisplay = service.windowsOnAllDisplays
                return (0 until byDisplay.size()).flatMap { byDisplay.valueAt(it) }
            }
            return service.windows
        }

        @Suppress("DEPRECATION")
        private fun release(info: AccessibilityNodeInfo) {
            if (Build.VERSION.SDK_INT < 33) info.recycle()
        }
    }
}

/** Which package owns each accessibility window id; ids are never reused for another package. */
class WindowPackageCache {
    private val owners = HashMap<Int, String>()

    @Synchronized fun get(id: Int): String? = owners[id]
    @Synchronized fun put(id: Int, pkg: String) { owners[id] = pkg }
    @Synchronized fun retainOnly(liveIds: Set<Int>) { owners.keys.retainAll(liveIds) }
}
