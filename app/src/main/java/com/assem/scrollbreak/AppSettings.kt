package com.assem.scrollbreak

import android.content.Context
import android.content.SharedPreferences
import com.assem.scrollbreak.core.LockTimer
import com.assem.scrollbreak.core.TargetPackages

/** Persisted settings shared by the UI and the service (same process). Every read is clamped. */
object AppSettings {
    private const val PREFS = "scrollbreak"
    private const val KEY_BLOCKING = "blocking_enabled"
    private const val KEY_OEM_AUTOSTART = "oem_autostart_done"
    private const val KEY_ALLOW_SECONDS = "allow_window_seconds"
    private const val KEY_THROTTLE = "throttle_ms"
    private const val KEY_FRICTION = "friction_seconds"
    private const val KEY_FRIEND_APPS = "friend_apps"
    private const val KEY_APP_PREFIX = "app_enabled_"
    private const val KEY_HAPTICS = "haptics_enabled"
    private const val KEY_DARK = "dark_mode"
    private const val KEY_LOCK_START = "lock_started_at"
    private const val KEY_LOCK_END = "lock_ends_at"
    private const val KEY_LIMITS = "app_limits"

    const val DEFAULT_ALLOW_SECONDS = 5
    const val DEFAULT_THROTTLE_MS = 200
    const val DEFAULT_FRICTION_SECONDS = 60
    const val MIN_LOCK_MINUTES = 1
    /** 30 days. Long enough to mean it, short enough that a typo cannot lock the phone for years. */
    const val MAX_LOCK_MINUTES = 30 * 24 * 60

    @Volatile private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            LimitStore.load(limitsJson)
            EmergencyStore.init(context)
        }
    }

    private fun int(key: String, default: Int, range: IntRange): Int =
        (prefs?.getInt(key, default) ?: default).coerceIn(range)

    private fun putInt(key: String, value: Int, range: IntRange) {
        if (isLocked) return
        prefs?.edit()?.putInt(key, value.coerceIn(range))?.apply()
    }

    /** The running lock timer, or null. While it runs, protection can only get stronger. */
    val lock: LockTimer?
        get() {
            val p = prefs ?: return null
            val start = p.getLong(KEY_LOCK_START, 0)
            val end = p.getLong(KEY_LOCK_END, 0)
            if (end <= 0) return null
            return LockTimer(start, end).takeIf { it.isActive(System.currentTimeMillis()) }
        }

    val isLocked: Boolean get() = lock != null

    /** The per-app limits as saved text; [LimitStore] owns reading and writing it. */
    var limitsJson: String?
        get() = prefs?.getString(KEY_LIMITS, null)
        set(value) {
            prefs?.edit()?.putString(KEY_LIMITS, value)?.apply()
        }

    /**
     * How long ScrollBreak must refuse to be switched off or uninstalled: the lock timer or any
     * locked app limit, whichever lasts longer.
     */
    fun protectionRemainingMs(): Long = maxOf(lockRemainingMs(), LimitStore.protectionRemainingMs())

    fun lockRemainingMs(): Long = lock?.remainingMs(System.currentTimeMillis()) ?: 0L

    /**
     * Turns blocking on and locks it for [minutes]. Ignored while a lock is already running, so a
     * lock can be neither shortened nor replaced.
     */
    fun startLock(minutes: Int): Boolean {
        if (isLocked) return false
        val timer = LockTimer.start(System.currentTimeMillis(), minutes.coerceIn(MIN_LOCK_MINUTES, MAX_LOCK_MINUTES))
        prefs?.edit()
            ?.putBoolean(KEY_BLOCKING, true)
            ?.putLong(KEY_LOCK_START, timer.startedAt)
            ?.putLong(KEY_LOCK_END, timer.endsAt)
            ?.apply()
        return true
    }

    /** Off = dry run: matches are logged as WOULD_BLOCK but nothing is blocked. */
    var blockingEnabled: Boolean
        get() = prefs?.getBoolean(KEY_BLOCKING, false) ?: false
        set(value) {
            if (!value && isLocked) return
            prefs?.edit()?.putBoolean(KEY_BLOCKING, value)?.apply()
        }

    /** The user confirmed they allowed autostart in their phone maker's own settings (cannot be read back). */
    var oemAutostartDone: Boolean
        get() = prefs?.getBoolean(KEY_OEM_AUTOSTART, false) ?: false
        set(value) {
            prefs?.edit()?.putBoolean(KEY_OEM_AUTOSTART, value)?.apply()
        }

    /** How long after leaving a messaging app (or DM / story) a video still counts as "sent by a friend". */
    var allowWindowSeconds: Int
        get() = int(KEY_ALLOW_SECONDS, DEFAULT_ALLOW_SECONDS, 1..30)
        set(value) = putInt(KEY_ALLOW_SECONDS, value, 1..30)

    /** Minimum gap between two content-change evaluations of one app. */
    var throttleMs: Int
        get() = int(KEY_THROTTLE, DEFAULT_THROTTLE_MS, 50..1000)
        set(value) = putInt(KEY_THROTTLE, value, 50..1000)

    /** Wait before the "turn ScrollBreak off" page unlocks; 0 disables the friction. */
    var frictionSeconds: Int
        get() = int(KEY_FRICTION, DEFAULT_FRICTION_SECONDS, 0..300)
        set(value) = putInt(KEY_FRICTION, value, 0..300)

    /** Messaging apps whose links count as "a friend sent this". */
    var friendApps: Set<String>
        get() = prefs?.getStringSet(KEY_FRIEND_APPS, null)?.toSet() ?: TargetPackages.messaging
        set(value) {
            if (isLocked) return
            prefs?.edit()?.putStringSet(KEY_FRIEND_APPS, value.toSet())?.apply()
        }

    /** Vibrate on toggles, dial steps and taps inside the app. */
    var hapticsEnabled: Boolean
        get() = prefs?.getBoolean(KEY_HAPTICS, true) ?: true
        set(value) {
            prefs?.edit()?.putBoolean(KEY_HAPTICS, value)?.apply()
        }

    /** True dark, false light, null follow the phone (the default until the user picks one). */
    var darkMode: Boolean?
        get() = prefs?.takeIf { it.contains(KEY_DARK) }?.getBoolean(KEY_DARK, true)
        set(value) {
            prefs?.edit()?.apply { if (value == null) remove(KEY_DARK) else putBoolean(KEY_DARK, value) }?.apply()
        }

    fun isAppEnabled(app: String): Boolean = prefs?.getBoolean(KEY_APP_PREFIX + app, true) ?: true

    fun setAppEnabled(app: String, enabled: Boolean) {
        if (!enabled && isLocked) return
        prefs?.edit()?.putBoolean(KEY_APP_PREFIX + app, enabled)?.apply()
    }

    /**
     * The emergency password was entered: ends the lock timer and removes every app limit and
     * block. The Home blocking switch is left as it is; it can be switched off now.
     */
    fun emergencyReset() {
        prefs?.edit()?.remove(KEY_LOCK_START)?.remove(KEY_LOCK_END)?.apply()
        LimitStore.clearAll()
    }

    /** Debug builds only (called from the debug broadcast): ends a lock early and restores [blocking]. */
    fun debugClearLock(blocking: Boolean) {
        prefs?.edit()?.remove(KEY_LOCK_START)?.remove(KEY_LOCK_END)?.putBoolean(KEY_BLOCKING, blocking)?.apply()
    }

    /** Back to factory values for everything except the blocking switch and per-app switches. */
    fun resetTuning() {
        if (isLocked) return
        prefs?.edit()
            ?.remove(KEY_ALLOW_SECONDS)?.remove(KEY_THROTTLE)?.remove(KEY_FRICTION)?.remove(KEY_FRIEND_APPS)
            ?.apply()
    }
}
