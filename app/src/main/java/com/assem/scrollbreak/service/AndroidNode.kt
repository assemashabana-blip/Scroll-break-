package com.assem.scrollbreak.service

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.assem.scrollbreak.core.NodeSnapshot
import com.assem.scrollbreak.core.NodeSource

/** Adapts an [AccessibilityNodeInfo] to the pure-Kotlin [NodeSource]. */
class AndroidNode(private val info: AccessibilityNodeInfo) : NodeSource {

    override fun snapshot(): NodeSnapshot {
        val r = Rect().also { info.getBoundsInScreen(it) }
        val flags = buildList {
            if (info.isClickable) add("clickable")
            if (info.isLongClickable) add("longClickable")
            if (info.isScrollable) add("scrollable")
            if (info.isFocused) add("focused")
            if (info.isSelected) add("selected")
            if (info.isChecked) add("checked")
            if (info.isEditable) add("editable")
            if (info.isPassword) add("password")
            if (!info.isEnabled) add("disabled")
            if (!info.isVisibleToUser) add("invisible")
        }.joinToString(",")
        return NodeSnapshot(
            viewId = info.viewIdResourceName,
            className = info.className?.toString(),
            // Never write password field contents to a file.
            text = if (info.isPassword) "<redacted>" else info.text?.toString(),
            contentDescription = info.contentDescription?.toString(),
            bounds = "[${r.left},${r.top}][${r.right},${r.bottom}]",
            childCount = info.childCount,
            flags = flags,
        )
    }

    override fun child(index: Int): NodeSource? = info.getChild(index)?.let(::AndroidNode)

    @Suppress("DEPRECATION")
    override fun release() {
        if (Build.VERSION.SDK_INT < 33) info.recycle()
    }
}
