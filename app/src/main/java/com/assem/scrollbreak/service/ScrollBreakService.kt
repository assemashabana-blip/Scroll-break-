package com.assem.scrollbreak.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import java.util.concurrent.Executors
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

    private val mainHandler = Handler(Looper.getMainLooper())
    private val dumpExecutor = Executors.newSingleThreadExecutor()

    /** Dumps all windows after [delayMs]; the walk itself runs off the main thread. */
    fun requestDump(delayMs: Long) {
        // The shade stays open after tapping a notification action and would hide the app.
        if (Build.VERSION.SDK_INT >= 31) performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        mainHandler.postDelayed({
            dumpExecutor.execute {
                val message = try {
                    val r = ScreenDumper.dumpAll(this)
                    AppLog.events.add(
                        System.currentTimeMillis(), packageName, "DUMP", r.file.name,
                        "${r.windows} windows, ${r.nodes} nodes",
                    )
                    DumpNotification.show(this, "Saved ${r.file.name} (${r.nodes} nodes)")
                    "Saved ${r.file.name} (${r.nodes} nodes)"
                } catch (t: Throwable) {
                    AppLog.error("dump failed", t)
                    "Dump failed: ${t.javaClass.simpleName}"
                }
                mainHandler.post { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
            }
        }, delayMs)
    }

    override fun onServiceConnected() {
        try {
            AppLog.init(this)
            instance = this
            DumpNotification.show(this)
            serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
                eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_SCROLLED
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                // INCLUDE_NOT_IMPORTANT_VIEWS: without it dumps and lookups miss many nodes.
                flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
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
        instance = null
        DumpNotification.cancel(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        AppLog.setServiceConnected(false)
        instance = null
        dumpExecutor.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val CONTENT_THROTTLE_MS = 200L

        @Volatile var instance: ScrollBreakService? = null
            private set
    }
}
