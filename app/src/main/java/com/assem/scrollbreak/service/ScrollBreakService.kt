package com.assem.scrollbreak.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.assem.scrollbreak.AppLog
import com.assem.scrollbreak.core.TargetPackages
import com.assem.scrollbreak.core.Throttle

/**
 * Phase 1: observe-only. Logs events from target apps; blocks nothing yet.
 * Every entry point is wrapped in try/catch so the service can never crash
 * (Android disables services that crash repeatedly).
 */
class ScrollBreakService : AccessibilityService() {

    private val contentThrottle = Throttle(CONTENT_THROTTLE_MS) { SystemClock.elapsedRealtime() }

    override fun onServiceConnected() {
        try {
            AppLog.init(this)
            serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
                eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_SCROLLED
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                packageNames = TargetPackages.all.toTypedArray()
                notificationTimeout = 0
            }
            AppLog.setServiceConnected(true)
            AppLog.events.add(System.currentTimeMillis(), packageName, "SERVICE", "connected")
        } catch (t: Throwable) {
            AppLog.error("onServiceConnected failed", t)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (event == null) return
            val pkg = event.packageName?.toString() ?: return
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                !contentThrottle.shouldPass(pkg)
            ) return

            val detail = if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                "from=${event.fromIndex} to=${event.toIndex} count=${event.itemCount}"
            } else ""

            AppLog.events.add(
                timeMs = System.currentTimeMillis(),
                packageName = pkg,
                eventType = AccessibilityEvent.eventTypeToString(event.eventType)
                    .removePrefix("TYPE_"),
                className = event.className?.toString().orEmpty(),
                detail = detail,
            )
        } catch (t: Throwable) {
            AppLog.error("onAccessibilityEvent failed", t)
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        AppLog.setServiceConnected(false)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        AppLog.setServiceConnected(false)
        super.onDestroy()
    }

    private companion object {
        const val CONTENT_THROTTLE_MS = 200L
    }
}
