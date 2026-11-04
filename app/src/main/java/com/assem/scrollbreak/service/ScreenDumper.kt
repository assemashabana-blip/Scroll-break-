package com.assem.scrollbreak.service

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityWindowInfo
import com.assem.scrollbreak.DumpStore
import com.assem.scrollbreak.core.TreeDump
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Walks every window the service can see and writes the node trees to a text file. */
object ScreenDumper {
    data class Result(val file: File, val windows: Int, val nodes: Int)

    fun dumpAll(service: AccessibilityService): Result {
        val sb = StringBuilder()
        sb.append("# ScrollBreak screen dump\n")
        sb.append("# time: ").append(
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        ).append('\n')
        sb.append("# device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")

        val windows = service.windows
        var nodes = 0
        if (windows.isEmpty()) {
            sb.append("\n=== NO WINDOWS; falling back to active window ===\n")
            service.rootInActiveWindow?.let { nodes += TreeDump.dump(AndroidNode(it), sb) }
        }
        // Topmost window first.
        for (w in windows.sortedByDescending { it.layer }) {
            val root = w.root
            sb.append("\n=== WINDOW id=${w.id} type=${typeName(w.type)} layer=${w.layer}")
            sb.append(" pkg=${root?.packageName ?: "?"} title=\"${w.title ?: ""}\"")
            sb.append(" active=${w.isActive} focused=${w.isFocused}")
            sb.append(" pip=${w.isInPictureInPictureMode} ===\n")
            if (root == null) {
                sb.append("(root unavailable)\n")
            } else {
                nodes += TreeDump.dump(AndroidNode(root), sb)
            }
        }
        val file = DumpStore.newFile(service)
        file.writeText(sb.toString())
        DumpStore.refresh(service)
        return Result(file, windows.size, nodes)
    }

    private fun typeName(type: Int) = when (type) {
        AccessibilityWindowInfo.TYPE_APPLICATION -> "APPLICATION"
        AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "INPUT_METHOD"
        AccessibilityWindowInfo.TYPE_SYSTEM -> "SYSTEM"
        AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> "ACCESSIBILITY_OVERLAY"
        AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER -> "SPLIT_SCREEN_DIVIDER"
        else -> "TYPE_$type"
    }
}
