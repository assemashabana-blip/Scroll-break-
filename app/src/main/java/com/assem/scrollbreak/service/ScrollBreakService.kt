package com.assem.scrollbreak.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.assem.scrollbreak.AppLog
import com.assem.scrollbreak.AppSettings
import com.assem.scrollbreak.RuleRepository
import com.assem.scrollbreak.core.EntryStateMachine
import com.assem.scrollbreak.core.EntryStateMachine.Action
import com.assem.scrollbreak.core.EntryStateMachine.Observation
import com.assem.scrollbreak.core.EntryStateMachine.State
import com.assem.scrollbreak.core.TargetPackages
import com.assem.scrollbreak.core.Throttle
import com.assem.scrollbreak.core.rules.AppRule
import com.assem.scrollbreak.core.rules.Fingerprint
import com.assem.scrollbreak.core.rules.RuleSet
import com.assem.scrollbreak.core.rules.UrlTracker
import java.util.concurrent.Executors

/**
 * Observes target apps, detects short-video players with the rules engine and decides, per
 * entry, whether to allow one video (friend-shared) or block. Blocking is BACK first, then a
 * full-screen overlay if BACK did not leave the player.
 * Every entry point is wrapped in try/catch so the service can never crash
 * (Android disables services that crash repeatedly).
 */
class ScrollBreakService : AccessibilityService() {

    private val contentThrottle = Throttle(CONTENT_THROTTLE_MS) { SystemClock.elapsedRealtime() }
    private val fingerprintThrottle = Throttle(FINGERPRINT_THROTTLE_MS) { SystemClock.elapsedRealtime() }
    private val machine = EntryStateMachine(
        isMessaging = { it in TargetPackages.messaging },
        clock = { SystemClock.elapsedRealtime() },
    )
    private val mainHandler = Handler(Looper.getMainLooper())
    private val dumpExecutor = Executors.newSingleThreadExecutor()

    private val debuggable by lazy { applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 }
    private var rules = RuleSet(emptyList())
    private var foregroundPkg: String? = null
    private val pollingPackages = HashSet<String>()
    private val urls = UrlTracker()
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
            noteForeground(pkg)
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                !contentThrottle.shouldPass(pkg)
            ) return

            val rule = rules.forPackage(pkg)
            val state = if (rule != null) observe(pkg, rule, event.eventTime, scrollIndexOf(event, pkg, rule)) else "n/a"

            val detail = if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                "from=${event.fromIndex} to=${event.toIndex} count=${event.itemCount}"
            } else ""
            if (debuggable) {
                Log.d("ScrollBreakEv", "${AccessibilityEvent.eventTypeToString(event.eventType)} $pkg " +
                    "${event.className} $detail state=$state")
            }

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

    /**
     * Keeps the foreground history the entry-source check relies on. When another package takes
     * over and the previous one has no window left, its session is over.
     */
    private fun noteForeground(pkg: String) {
        val previous = foregroundPkg
        if (previous == pkg) return
        machine.onForeground(pkg)
        foregroundPkg = pkg
        if (previous != null && !hasWindow(previous)) {
            machine.onLeft(previous)
            urls.forget(previous)
        }
    }

    private fun hasWindow(pkg: String): Boolean {
        val screen = AndroidScreen.forPackage(this, pkg)
        val has = !screen.isEmpty
        screen.close()
        return has
    }

    /** What a screen says right now: is it a short-video player, and (browsers) which page. */
    private class Reading(val inPlayer: Boolean, val urlKey: String?)

    /**
     * Player detection combines the app's player signature with, for browsers, the URL bar: the
     * URL is read through [UrlTracker] so a hidden bar keeps the last page, and a pattern match
     * means a short-video page. The normalised URL doubles as that page's identity.
     */
    private fun read(pkg: String, rule: AppRule, screen: AndroidScreen): Reading {
        // No window means the user left; a remembered URL must not outlive the page it came from.
        if (screen.isEmpty) {
            urls.forget(pkg)
            return Reading(false, null)
        }
        var urlKey: String? = null
        if (rule.isBrowser) {
            val url = urls.observe(pkg, rule.urlText(screen))
            if (url != null && rule.matchUrl(url) != null) urlKey = url
        }
        return Reading(rule.isPlayer(screen) || urlKey != null, urlKey)
    }

    /**
     * Observes [pkg], advances its state machine and fires the block action when due.
     * [eventUptimeMs] is when the triggering event happened (for the latency figure).
     */
    private fun observe(pkg: String, rule: AppRule, eventUptimeMs: Long, scrollIndex: Int? = null): String {
        val t0 = SystemClock.uptimeMillis()
        val screen = AndroidScreen.forPackage(this, pkg)
        val t1 = SystemClock.uptimeMillis()
        val inPlayer: Boolean
        var source: String? = null
        val result: EntryStateMachine.Result
        try {
            val reading = read(pkg, rule, screen)
            inPlayer = reading.inPlayer
            val t2 = SystemClock.uptimeMillis()
            source = if (!inPlayer) rule.matchingSource(screen) else null
            // The fingerprint costs a tree walk, so only read it while one video is allowed.
            // A swipe fires a burst of events; reading it every time would queue them up.
            val fingerprint = if (inPlayer && machine.state(pkg) == State.ALLOW_ONE &&
                fingerprintThrottle.shouldPass(pkg)
            ) {
                reading.urlKey?.let { Fingerprint(listOf(it)) } ?: rule.fingerprint(screen)
            } else Fingerprint.EMPTY
            val t3 = SystemClock.uptimeMillis()
            result = machine.onObservation(Observation(pkg, inPlayer, source, fingerprint, scrollIndex))
            if (debuggable && inPlayer) {
                Log.d("ScrollBreakEv", "observe $pkg fp=${fingerprint.hash()} -> ${result.action} " +
                    "windows=${t1 - t0}ms player=${t2 - t1}ms fingerprint=${t3 - t2}ms")
            }
        } finally {
            screen.close()
        }

        if (!inPlayer) overlay.dismiss()
        val reason = result.reason
        if (result.action == Action.BLOCK) {
            if (AppSettings.blockingEnabled) block(eventUptimeMs, pkg, rule, reason.orEmpty()) else {
                action(pkg, "WOULD_BLOCK", rule.app, reason.orEmpty())
            }
        } else if (reason != null && reason.startsWith("allow")) {
            action(pkg, "ALLOW_ONE", rule.app, reason)
        }
        when {
            machine.state(pkg) == State.ALLOW_ONE -> schedulePoll(pkg, rule, ALLOW_POLL_MS)
            source != null -> schedulePoll(pkg, rule, SOURCE_POLL_MS)
        }
        return machine.state(pkg).name
    }

    /**
     * Re-observes [pkg] after [delayMs], which keeps the state machine fed when the app goes quiet:
     * YouTube emits nothing once a swipe settles, and a DM or story you sit in emits nothing, so
     * the "just saw an allowed source" sighting would expire before you tap the reel.
     * Each observation decides whether to schedule the next one.
     */
    private fun schedulePoll(pkg: String, rule: AppRule, delayMs: Long) {
        if (!pollingPackages.add(pkg)) return
        mainHandler.postDelayed({
            pollingPackages.remove(pkg)
            try {
                observe(pkg, rule, SystemClock.uptimeMillis())
            } catch (t: Throwable) {
                AppLog.error("poll failed", t)
            }
        }, delayMs)
    }

    /**
     * The video index a scroll event reports, if the rule names a scrollable that carries one.
     * Only read while one video is allowed: it costs a call to fetch the event's source node.
     */
    private fun scrollIndexOf(event: AccessibilityEvent, pkg: String, rule: AppRule): Int? {
        val signal = rule.scrollSignal ?: return null
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED || event.toIndex < 0) return null
        if (machine.state(pkg) != State.ALLOW_ONE) return null
        val node = AndroidNode(event.source ?: return null)
        return try {
            if (signal.copy(visible = false).matches(node.snapshot())) event.toIndex else null
        } finally {
            node.release()
        }
    }

    /** Block decisions go to the event log and to logcat (tag ScrollBreak) for debugging. */
    private fun action(pkg: String, type: String, cls: String, detail: String) {
        AppLog.events.add(System.currentTimeMillis(), pkg, type, cls, detail)
        Log.i("ScrollBreak", "$type $cls $pkg $detail")
    }

    private fun isInPlayer(pkg: String, rule: AppRule): Boolean {
        val screen = AndroidScreen.forPackage(this, pkg)
        return try {
            read(pkg, rule, screen).inPlayer
        } finally {
            screen.close()
        }
    }

    /** Stage 1: BACK. Stage 2 (after [ESCALATE_DELAY_MS]): overlay if still in the player. */
    private fun block(eventUptimeMs: Long, pkg: String, rule: AppRule, reason: String) {
        val latencyMs = SystemClock.uptimeMillis() - eventUptimeMs
        performGlobalAction(GLOBAL_ACTION_BACK)
        AppLog.recordBlock(rule.app, reason)
        action(pkg, "BLOCK", "BACK", "app=${rule.app} $reason latency=${latencyMs}ms")
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
                    machine.onLeft(pkg)
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
        private const val ALLOW_POLL_MS = 250L
        private const val SOURCE_POLL_MS = 700L
        private const val FINGERPRINT_THROTTLE_MS = 120L

        @Volatile var instance: ScrollBreakService? = null
            private set
    }
}
