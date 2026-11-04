package com.assem.scrollbreak.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.assem.scrollbreak.AppLog
import com.assem.scrollbreak.RuleRepository
import com.assem.scrollbreak.AppSettings
import com.assem.scrollbreak.core.PlayerTracker
import com.assem.scrollbreak.core.TargetPackages
import com.assem.scrollbreak.core.Throttle
import com.assem.scrollbreak.core.rules.AppRule
import com.assem.scrollbreak.core.rules.RuleSet
import java.util.concurrent.Executors

/**
 * Observes target apps, detects short-video players with the rules engine and blocks them
 * (BACK, then a full-screen overlay if BACK did not leave the player).
 * Every entry point is wrapped in try/catch so the service can never crash
 * (Android disables services that crash repeatedly).
 */
class ScrollBreakService : AccessibilityService() {

    private val contentThrottle = Throttle(CONTENT_THROTTLE_MS) { SystemClock.elapsedRealtime() }
    private val tracker = PlayerTracker { SystemClock.elapsedRealtime() }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val dumpExecutor = Executors.newSingleThreadExecutor()

    private var rules = RuleSet(emptyList())
    // Lazy: system services are unavailable until the service is attached to its context.
    private val overlay by lazy { BlockOverlay(this) { performGlobalAction(GLOBAL_ACTION_BACK) } }

    /** Debug aid: renders the block overlay so its look and "Go back" button can be checked. */
    fun showTestOverlay() = overlay.show("Short-form video is blocked")

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
            AppSettings.init(this)
            instance = this
            rules = RuleRepository.load(this)
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

            val rule = rules.forPackage(pkg)
            val state = if (rule != null) detect(event, pkg, rule) else "n/a"

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
                state = state,
            )
        } catch (t: Throwable) {
            AppLog.error("onAccessibilityEvent failed", t)
        }
    }

    /** Evaluates the player signature for [pkg] and fires the block action when due. */
    private fun detect(event: AccessibilityEvent, pkg: String, rule: AppRule): String {
        val inPlayer = isInPlayer(pkg, rule)
        val decision = tracker.onObservation(pkg, inPlayer)
        if (!inPlayer) overlay.dismiss()
        if (decision == PlayerTracker.Decision.BLOCK) {
            tracker.onBlocked(pkg)
            if (AppSettings.blockingEnabled) block(event, pkg, rule) else {
                action(pkg, "WOULD_BLOCK", rule.app, "blocking is off")
            }
        }
        return tracker.state(pkg)
    }

    /** Block decisions go to the event log and to logcat (tag ScrollBreak) for debugging. */
    private fun action(pkg: String, type: String, cls: String, detail: String) {
        AppLog.events.add(System.currentTimeMillis(), pkg, type, cls, detail)
        Log.i("ScrollBreak", "$type $cls $pkg $detail")
    }

    private fun isInPlayer(pkg: String, rule: AppRule): Boolean {
        val screen = AndroidScreen.forPackage(this, pkg)
        return try {
            rule.isPlayer(screen)
        } finally {
            screen.close()
        }
    }

    /** Stage 1: BACK. Stage 2 (after [ESCALATE_DELAY_MS]): overlay if still in the player. */
    private fun block(event: AccessibilityEvent, pkg: String, rule: AppRule) {
        val latencyMs = SystemClock.uptimeMillis() - event.eventTime
        performGlobalAction(GLOBAL_ACTION_BACK)
        AppLog.recordBlock(rule.app, "player_signature")
        action(pkg, "BLOCK", "BACK", "app=${rule.app} latency=${latencyMs}ms")
        mainHandler.postDelayed({ escalateIfStillThere(pkg, rule) }, ESCALATE_DELAY_MS)
    }

    private fun escalateIfStillThere(pkg: String, rule: AppRule) {
        try {
            if (!AppSettings.blockingEnabled || !isInPlayer(pkg, rule)) return
            overlay.show("Short-form video is blocked")
            action(pkg, "BLOCK", "OVERLAY", "BACK did not leave the player")
            watchOverlay(pkg, rule)
        } catch (t: Throwable) {
            AppLog.error("escalate failed", t)
        }
    }

    /** No events arrive once the user leaves the app, so poll until the player is gone. */
    private fun watchOverlay(pkg: String, rule: AppRule) {
        mainHandler.postDelayed({
            try {
                if (!overlay.isShowing) return@postDelayed
                if (isInPlayer(pkg, rule)) watchOverlay(pkg, rule) else {
                    overlay.dismiss()
                    tracker.onObservation(pkg, false)
                }
            } catch (t: Throwable) {
                AppLog.error("overlay watch failed", t)
                overlay.dismiss()
            }
        }, OVERLAY_POLL_MS)
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        AppLog.setServiceConnected(false)
        instance = null
        overlay.dismiss()
        DumpNotification.cancel(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        AppLog.setServiceConnected(false)
        instance = null
        overlay.dismiss()
        dumpExecutor.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val CONTENT_THROTTLE_MS = 200L
        private const val ESCALATE_DELAY_MS = 300L
        private const val OVERLAY_POLL_MS = 500L

        @Volatile var instance: ScrollBreakService? = null
            private set
    }
}
