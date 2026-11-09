package com.assem.scrollbreak.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.assem.scrollbreak.AppLog
import com.assem.scrollbreak.AppSettings
import com.assem.scrollbreak.AppUsage
import com.assem.scrollbreak.LimitStore
import com.assem.scrollbreak.core.LimitDecision
import com.assem.scrollbreak.RuleStore
import com.assem.scrollbreak.core.EntryStateMachine
import com.assem.scrollbreak.core.FrictionGate
import com.assem.scrollbreak.core.LockTimer
import com.assem.scrollbreak.core.EntryStateMachine.Action
import com.assem.scrollbreak.core.EntryStateMachine.Observation
import com.assem.scrollbreak.core.EntryStateMachine.State
import com.assem.scrollbreak.core.TargetPackages
import com.assem.scrollbreak.core.Throttle
import com.assem.scrollbreak.core.rules.AppRule
import com.assem.scrollbreak.core.rules.Fingerprint
import com.assem.scrollbreak.core.rules.Leaf
import com.assem.scrollbreak.core.rules.MatchMode
import com.assem.scrollbreak.core.rules.Purpose
import com.assem.scrollbreak.core.rules.UrlTracker
import java.text.DateFormat
import java.util.Collections
import java.util.Date
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Observes target apps, detects short-video players with the rules engine and decides, per
 * entry, whether to allow one video (friend-shared) or block. Blocking is BACK first, then a
 * full-screen overlay if BACK did not leave the player.
 *
 * Threading: event callbacks arrive on the main thread and only do cheap work (logging,
 * throttling, bookkeeping). Everything that queries another app's windows runs on [evalThread].
 * Every entry point is wrapped in [safely] so the service can never crash (Android disables
 * services that crash repeatedly).
 */
class ScrollBreakService : AccessibilityService() {

    private val contentThrottle = Throttle(CONTENT_THROTTLE_MS) { SystemClock.elapsedRealtime() }
    private val fingerprintThrottle = Throttle(FINGERPRINT_THROTTLE_MS) { SystemClock.elapsedRealtime() }
    private val machine = EntryStateMachine(
        isMessaging = { it in AppSettings.friendApps },
        clock = { SystemClock.elapsedRealtime() },
    )
    private val mainHandler = Handler(Looper.getMainLooper())
    private val dumpExecutor = Executors.newSingleThreadExecutor()

    /**
     * All screen evaluation runs here, not on the main thread. Querying another app is a binder
     * call that waits on that app's UI thread; while Instagram animates it can take 100+ ms, and
     * on the main thread that would stall every other event queued behind it.
     */
    private val evalThread = HandlerThread("scrollbreak-eval").also { it.start() }
    private val evalHandler = Handler(evalThread.looper)
    private val pendingEval: MutableSet<String> = Collections.synchronizedSet(HashSet())
    private val windowCache = WindowPackageCache()

    private val debuggable by lazy { applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 }
    private val frictionGate = FrictionGate { SystemClock.elapsedRealtime() }
    private val frictionTickScheduled = AtomicBoolean(false)
    private val frictionOverlay by lazy { FrictionOverlay(this) { performGlobalAction(GLOBAL_ACTION_BACK) } }
    @Volatile private var foregroundPkg: String? = null  // written on the main thread only
    private val limitPending: MutableSet<String> = Collections.synchronizedSet(HashSet())
    private val limitTimers = HashMap<String, Runnable>()      // eval thread only
    private val lastLimitBlock = HashMap<String, Long>()       // eval thread only
    private val pollingPackages = HashSet<String>()      // eval thread only
    private val urls = UrlTracker()
    // Lazy: system services are unavailable until the service is attached to its context.
    private val overlay by lazy { BlockOverlay(this) { performGlobalAction(GLOBAL_ACTION_BACK) } }

    /** Runs [block], logging instead of throwing: a crash here would get the service disabled. */
    private inline fun safely(what: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            AppLog.error("$what failed", t)
        }
    }

    /** Debug aid: throws on both threads to prove the service survives and logs it. */
    fun injectFault() {
        mainHandler.post { safely("injected main-thread fault") { error("injected main-thread fault") } }
        evalHandler.post { safely("injected eval-thread fault") { error("injected eval-thread fault") } }
    }

    /** Debug aid: renders the block overlay so its look and "Go back" button can be checked. */
    fun showTestOverlay() {
        mainHandler.post { overlay.show("Short-form video is blocked") }
    }

    /**
     * Debug aid for phones that refuse `adb input` (Xiaomi without "USB debugging (Security
     * settings)"): clicks the first clickable node in [pkg] whose description starts with [desc].
     * Only used to switch tabs while capturing dumps; does nothing in release builds.
     */
    fun debugClick(pkg: String, desc: String) {
        if (!debuggable) return
        evalHandler.post {
            safely("debug click") {
                val clicked = redirectTo(pkg, Leaf(desc = desc, match = MatchMode.STARTS_WITH, clickable = true))
                AppLog.events.add(
                    System.currentTimeMillis(), pkg, "DEBUG_CLICK", desc, if (clicked) "clicked" else "not found",
                )
                Log.d("ScrollBreak", "DEBUG_CLICK $pkg '$desc' -> ${if (clicked) "clicked" else "not found"}")
            }
        }
    }

    /** Debug aid, like [debugClick]: scrolls [pkg]'s first visible [className] list forward (a swipe). */
    fun debugScroll(pkg: String, className: String) {
        if (!debuggable) return
        evalHandler.post {
            safely("debug scroll") {
                val screen = AndroidScreen.forPackage(this, pkg, windowCache)
                val scrolled = try { screen.scrollForward(className) } finally { screen.close() }
                Log.d("ScrollBreak", "DEBUG_SCROLL $pkg $className -> ${if (scrolled) "scrolled" else "not found"}")
            }
        }
    }

    /**
     * Debug aid: an upward swipe through the middle of the screen, i.e. "next video". Needs the
     * debug build's canPerformGestures; for lists the app does not expose as scrollable.
     */
    fun debugSwipeUp() {
        if (!debuggable) return
        val m = resources.displayMetrics
        val path = android.graphics.Path().apply {
            moveTo(m.widthPixels / 2f, m.heightPixels * 0.75f)
            lineTo(m.widthPixels / 2f, m.heightPixels * 0.25f)
        }
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 250))
            .build()
        val started = dispatchGesture(gesture, null, null)
        Log.d("ScrollBreak", "DEBUG_SWIPE -> ${if (started) "dispatched" else "refused"}")
    }

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
        safely("onServiceConnected") {
            AppLog.init(this)
            AppSettings.init(this)
            instance = this
            LimitStore.onChanged = { refreshWatchedPackages() }
            RuleStore.reload(this)
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
                // Launchers are watched only so the foreground history sees "went home" between a
                // messaging app and a video app; they have no rule and are never evaluated.
                packageNames = watchedPackages().toTypedArray()
                notificationTimeout = 0
            }
            AppLog.setServiceConnected(true)
            ProtectionAlert.cancel(this)
            AppLog.events.add(System.currentTimeMillis(), packageName, "SERVICE", "connected")
        }
    }

    /** Apps with rules, the messaging apps, launchers, and any app that has a limit. */
    private fun watchedPackages(): Set<String> =
        TargetPackages.all + launcherPackages() + LimitStore.limitedPackages()

    /** Called when limits change: starts or stops watching apps without waiting for a reconnect. */
    private fun refreshWatchedPackages() {
        mainHandler.post {
            safely("refresh watched packages") {
                val info = serviceInfo ?: return@safely
                info.packageNames = watchedPackages().toTypedArray()
                serviceInfo = info
            }
        }
    }

    /** Every installed home-screen app (Xiaomi, Samsung, Pixel... each ships its own). */
    private fun launcherPackages(): Set<String> = try {
        val home = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_HOME)
        packageManager.queryIntentActivities(home, 0).map { it.activityInfo.packageName }.toSet()
    } catch (t: Throwable) {
        AppLog.error("launcher lookup failed", t)
        emptySet()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        safely("onAccessibilityEvent") {
            if (event == null) return
            val pkg = event.packageName?.toString() ?: return
            noteForeground(pkg)
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && LimitStore.isLimited(pkg)) {
                requestLimitCheck(pkg)
            }
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                !contentThrottle.shouldPass(pkg)
            ) return

            contentThrottle.intervalMs = AppSettings.throttleMs.toLong()
            val rule = RuleStore.current.forPackage(pkg)
            val enabled = rule == null || AppSettings.isAppEnabled(rule.app)
            val detail = if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                "from=${event.fromIndex} to=${event.toIndex} count=${event.itemCount} " +
                    "scrollX=${event.scrollX} scrollY=${event.scrollY} maxX=${event.maxScrollX} maxY=${event.maxScrollY}"
            } else ""
            val state = when {
                rule == null -> "n/a"
                !enabled -> "OFF"
                rule.purpose == Purpose.FRICTION -> "FRICTION"
                else -> machine.state(pkg).name
            }
            if (debuggable) {
                Log.d("ScrollBreakEv", "${AccessibilityEvent.eventTypeToString(event.eventType)} $pkg " +
                    "${event.className} $detail state=$state")
            }
            AppLog.events.add(
                timeMs = System.currentTimeMillis(),
                packageName = pkg,
                eventType = AccessibilityEvent.eventTypeToString(event.eventType).removePrefix("TYPE_"),
                className = event.className?.toString().orEmpty(),
                detail = detail,
                state = state,
            )
            if (rule == null || !enabled) return

            val eventTime = event.eventTime
            val scroll = if (rule.purpose == Purpose.SHORT_VIDEO) scrollIndexOf(event, pkg, rule) else null
            // Events arrive in bursts; one queued evaluation reads the freshest screen for all of
            // them. Scroll events carry an index of their own, so each of those is evaluated.
            if (scroll == null && !pendingEval.add(pkg)) return
            evalHandler.post {
                safely("observe") {
                    if (scroll == null) pendingEval.remove(pkg)
                    if (rule.purpose == Purpose.FRICTION) observeFriction(pkg, rule)
                    else observe(pkg, rule, eventTime, scroll)
                }
            }
        }
    }

    /**
     * Keeps the foreground history the entry-source check relies on. When another package takes
     * over and the previous one has no window left, its session is over; that check needs a
     * binder call, so it runs on the evaluation thread, ahead of this package's own observation.
     */
    private fun noteForeground(pkg: String) {
        val previous = foregroundPkg
        if (previous == pkg) return
        machine.onForeground(pkg)
        foregroundPkg = pkg
        if (previous != null) {
            evalHandler.post {
                safely("leave check") {
                    if (!hasWindow(previous)) {
                        machine.onLeft(previous)
                        urls.forget(previous)
                    }
                }
            }
        }
    }

    private fun hasWindow(pkg: String): Boolean {
        val screen = AndroidScreen.forPackage(this, pkg, windowCache)
        val has = !screen.isEmpty
        screen.close()
        return has
    }

    /** Queues one limit check for [pkg]; a burst of window events costs a single check. */
    private fun requestLimitCheck(pkg: String) {
        if (!limitPending.add(pkg)) return
        evalHandler.post {
            limitPending.remove(pkg)
            safely("limit check") { enforceLimit(pkg) }
        }
    }

    /**
     * Stops [pkg] if its limit says so, otherwise arms a timer for the moment its daily time runs
     * out. Runs on the evaluation thread.
     */
    private fun enforceLimit(pkg: String) {
        limitTimers.remove(pkg)?.let { evalHandler.removeCallbacks(it) }
        val now = System.currentTimeMillis()
        val book = LimitStore.book.value
        val limit = book.get(pkg)?.takeIf { book.isLimited(pkg, now) } ?: return
        val used = if (limit.dailyMinutes != null) AppUsage.todayMs(this, pkg, now) else 0L
        when (val decision = book.decide(pkg, used, now)) {
            is LimitDecision.Blocked -> blockLimited(pkg, decision)
            is LimitDecision.Allowed -> decision.remainingMs?.let { scheduleLimitCheck(pkg, it) }
        }
    }

    private fun scheduleLimitCheck(pkg: String, remainingMs: Long) {
        val timer = Runnable {
            limitTimers.remove(pkg)
            // Only worth checking while the app is still open; coming back to it checks again.
            safely("limit timer") { if (hasWindow(pkg)) enforceLimit(pkg) }
        }
        limitTimers[pkg] = timer
        evalHandler.postDelayed(timer, remainingMs.coerceAtLeast(1000L))
    }

    private fun blockLimited(pkg: String, decision: LimitDecision.Blocked) {
        performGlobalAction(GLOBAL_ACTION_HOME)
        val nowMs = SystemClock.elapsedRealtime()
        // The app can come straight back (a notification, the recents screen): block each time, explain once.
        if (nowMs - (lastLimitBlock[pkg] ?: -10_000L) > 3000) {
            val label = try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
            } catch (_: Exception) {
                pkg
            }
            val message = when (decision.reason) {
                LimitDecision.Reason.DAILY -> "$label: today's limit is used up. It opens again tomorrow."
                LimitDecision.Reason.BLOCK -> "$label is blocked until ${untilText(decision.untilMs)}."
            }
            mainHandler.post { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
        }
        lastLimitBlock[pkg] = nowMs
        action(pkg, "LIMIT_BLOCK", decision.reason.name, "until=${decision.untilMs}")
    }

    /** "6:30 PM" for later today, with the date for another day. */
    private fun untilText(untilMs: Long): String {
        val sameDay = untilMs - System.currentTimeMillis() < 12 * 3_600_000L
        val format = if (sameDay) DateFormat.getTimeInstance(DateFormat.SHORT)
        else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        return format.format(Date(untilMs))
    }

    /**
     * Friction: while the "turn ScrollBreak off" page is open, cover it with a countdown. The
     * page is recognised by the rule (a system screen, not a video), and the countdown is
     * re-checked every second so leaving the page removes the overlay straight away.
     * Runs on the evaluation thread.
     */
    private fun observeFriction(pkg: String, rule: AppRule) {
        frictionGate.durationMs = AppSettings.frictionSeconds * 1000L
        val lockMs = AppSettings.protectionRemainingMs()
        val screen = AndroidScreen.forPackage(this, pkg, windowCache)
        var visible = false
        var guarded = false
        try {
            if (!screen.isEmpty) {
                visible = rule.isPlayer(screen)
                // Only worth a look while a lock runs: ScrollBreak's App info page, the uninstall dialog.
                guarded = lockMs > 0 && rule.isLockGuarded(screen)
            }
        } finally {
            screen.close()
        }
        // A running lock timer is stronger than the countdown: these pages stay covered until it ends.
        if (lockMs > 0 && (visible || guarded)) {
            val left = LockTimer.format(lockMs)
            val hint = if (guarded) {
                "A lock timer or an app limit is running. ScrollBreak cannot be switched off or uninstalled until it ends."
            } else {
                "A lock timer or an app limit is running. It cannot be switched off until it ends."
            }
            mainHandler.post { frictionOverlay.show("ScrollBreak is locked", left, hint) }
            scheduleFrictionTick(pkg, rule)
            return
        }
        val status = frictionGate.update(visible)
        if (status.locked) {
            val seconds = (status.remainingMs + 999) / 1000
            mainHandler.post {
                frictionOverlay.show(
                    "Turning ScrollBreak off is delayed",
                    "$seconds s",
                    "Wait it out, or go back and keep scrolling under control.",
                )
            }
            scheduleFrictionTick(pkg, rule)
        } else if (frictionOverlay.isShowing) {
            mainHandler.post { frictionOverlay.dismiss() }
        }
    }

    private fun scheduleFrictionTick(pkg: String, rule: AppRule) {
        if (!frictionTickScheduled.compareAndSet(false, true)) return
        evalHandler.postDelayed({
            frictionTickScheduled.set(false)
            safely("friction tick") { observeFriction(pkg, rule) }
        }, 1000)
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
     * Runs on the evaluation thread.
     */
    private fun observe(pkg: String, rule: AppRule, eventUptimeMs: Long, scrollIndex: Int? = null) {
        val t0 = SystemClock.uptimeMillis()
        machine.allowWindowMs = AppSettings.allowWindowSeconds * 1000L
        val screen = AndroidScreen.forPackage(this, pkg, windowCache)
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
            // A messaging app that is still on screen (split-screen) was never left: not a link tap.
            val sourceVisible = inPlayer && machine.state(pkg) == State.IDLE &&
                machine.candidateSource(pkg)?.let { it in AppSettings.friendApps && hasWindow(it) } == true
            result = machine.onObservation(
                Observation(pkg, inPlayer, source, fingerprint, scrollIndex, sourceVisible),
            )
            recordHealth(rule, reading, source, fingerprint, result, screen)
            if (debuggable) {
                Log.d("ScrollBreakEv", "observe $pkg player=$inPlayer fp=${fingerprint.hash()} -> ${result.action} " +
                    "queued=${t0 - eventUptimeMs}ms windows=${t1 - t0}ms player=${t2 - t1}ms fingerprint=${t3 - t2}ms")
            }
        } finally {
            screen.close()
        }

        if (!inPlayer && overlay.isShowing) mainHandler.post { overlay.dismiss() }
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
    }

    /** Feeds the detection-health screen: which of this rule's signatures matched just now. */
    private fun recordHealth(
        rule: AppRule,
        reading: Reading,
        source: String?,
        fingerprint: Fingerprint,
        result: EntryStateMachine.Result,
        screen: AndroidScreen,
    ) {
        val health = AppLog.health
        if (reading.inPlayer) health.hit(rule.app, "player")
        reading.urlKey?.let { url -> rule.matchUrl(url)?.let { health.hit(rule.app, "url:${it.name}") } }
        if (source != null) health.hit(rule.app, "source:$source")
        if (!fingerprint.isEmpty) health.hit(rule.app, "fingerprint")
        // Feed entries cost extra lookups, so they are only checked when something just changed.
        if (reading.inPlayer && result.reason != null) {
            rule.matchingFeedEntries(screen).forEach { health.hit(rule.app, "feed:$it") }
        }
    }

    /**
     * Re-observes [pkg] after [delayMs], which keeps the state machine fed when the app goes quiet:
     * YouTube emits nothing once a swipe settles, and a DM or story you sit in emits nothing, so
     * the "just saw an allowed source" sighting would expire before you tap the reel.
     * Each observation decides whether to schedule the next one.
     */
    private fun schedulePoll(pkg: String, rule: AppRule, delayMs: Long) {
        if (!pollingPackages.add(pkg)) return
        evalHandler.postDelayed({
            pollingPackages.remove(pkg)
            safely("poll") { observe(pkg, rule, SystemClock.uptimeMillis()) }
        }, delayMs)
    }

    /**
     * The video index a scroll event reports, if the rule names a scrollable that carries one.
     * Only read while one video is allowed: it costs a call to fetch the event's source node.
     */
    private fun scrollIndexOf(event: AccessibilityEvent, pkg: String, rule: AppRule): Int? {
        val signal = rule.scrollSignal ?: return null
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return null
        // Lists report an item index; web content (Messenger's browser) only a scroll position.
        val index = if (rule.scrollByPosition) event.scrollY.takeIf { it > 0 } else event.toIndex.takeIf { it >= 0 }
        if (index == null || machine.state(pkg) != State.ALLOW_ONE) return null
        val node = AndroidNode(event.source ?: return null)
        return try {
            if (signal.copy(visible = false).matches(node.snapshot())) index else null
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
        val screen = AndroidScreen.forPackage(this, pkg, windowCache)
        return try {
            read(pkg, rule, screen).inPlayer
        } finally {
            screen.close()
        }
    }

    /**
     * Stage 1: switch to the rule's redirect tab if it has one and it can be clicked, else BACK.
     * Stage 2 (after [ESCALATE_DELAY_MS]): overlay if still in the player.
     */
    private fun block(eventUptimeMs: Long, pkg: String, rule: AppRule, reason: String) {
        val redirected = rule.redirectTo.any { redirectTo(pkg, it) }
        if (!redirected) performGlobalAction(GLOBAL_ACTION_BACK)
        val latencyMs = SystemClock.uptimeMillis() - eventUptimeMs
        AppLog.recordBlock(rule.app, reason, latencyMs)
        action(pkg, "BLOCK", if (redirected) "REDIRECT" else "BACK", "app=${rule.app} $reason latency=${latencyMs}ms")
        evalHandler.postDelayed({ safely("escalate") { escalateIfStillThere(pkg, rule) } }, ESCALATE_DELAY_MS)
    }

    private fun redirectTo(pkg: String, target: Leaf): Boolean {
        val screen = AndroidScreen.forPackage(this, pkg, windowCache)
        return try {
            screen.click(target)
        } finally {
            screen.close()
        }
    }

    private fun escalateIfStillThere(pkg: String, rule: AppRule) {
        if (!AppSettings.blockingEnabled || !isInPlayer(pkg, rule)) return
        mainHandler.post { overlay.show("Short-form video is blocked") }
        action(pkg, "BLOCK", "OVERLAY", "BACK did not leave the player")
        watchOverlay(pkg, rule)
    }

    /** No events arrive once the user leaves the app, so poll until the player is gone. */
    private fun watchOverlay(pkg: String, rule: AppRule) {
        evalHandler.postDelayed({
            safely("overlay watch") {
                if (!overlay.isShowing) return@safely
                if (isInPlayer(pkg, rule)) watchOverlay(pkg, rule) else {
                    mainHandler.post { overlay.dismiss() }
                    machine.onLeft(pkg)
                }
            }
        }, OVERLAY_POLL_MS)
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        AppLog.setServiceConnected(false)
        instance = null
        LimitStore.onChanged = null
        evalHandler.removeCallbacksAndMessages(null)
        overlay.dismiss()
        frictionOverlay.dismiss()
        DumpNotification.cancel(this)
        // Unbind without a reconnect means switched off or killed: say so right away; the
        // watchdog repeats this later in case the process is gone before it can.
        if (!ServiceStatus.isEnabled(this)) ProtectionAlert.show(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        AppLog.setServiceConnected(false)
        instance = null
        evalHandler.removeCallbacksAndMessages(null)
        evalThread.quitSafely()
        overlay.dismiss()
        frictionOverlay.dismiss()
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
