package com.assem.scrollbreak

import android.content.Context
import android.content.SharedPreferences
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

    const val DEFAULT_ALLOW_SECONDS = 5
    const val DEFAULT_THROTTLE_MS = 200
    const val DEFAULT_FRICTION_SECONDS = 60

    @Volatile private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    private fun int(key: String, default: Int, range: IntRange): Int =
        (prefs?.getInt(key, default) ?: default).coerceIn(range)

    private fun putInt(key: String, value: Int, range: IntRange) {
        prefs?.edit()?.putInt(key, value.coerceIn(range))?.apply()
    }

    /** Off = dry run: matches are logged as WOULD_BLOCK but nothing is blocked. */
    var blockingEnabled: Boolean
        get() = prefs?.getBoolean(KEY_BLOCKING, false) ?: false
        set(value) {
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
        prefs?.edit()?.putBoolean(KEY_APP_PREFIX + app, enabled)?.apply()
    }

    /** Back to factory values for everything except the blocking switch and per-app switches. */
    fun resetTuning() {
        prefs?.edit()
            ?.remove(KEY_ALLOW_SECONDS)?.remove(KEY_THROTTLE)?.remove(KEY_FRICTION)?.remove(KEY_FRIEND_APPS)
            ?.apply()
    }
}
