package com.assem.scrollbreak

import android.content.Context
import android.content.SharedPreferences

/** Small persisted switches shared by the UI and the service (same process). */
object AppSettings {
    private const val PREFS = "scrollbreak"
    private const val KEY_BLOCKING = "blocking_enabled"
    private const val KEY_OEM_AUTOSTART = "oem_autostart_done"

    @Volatile private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
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
}
