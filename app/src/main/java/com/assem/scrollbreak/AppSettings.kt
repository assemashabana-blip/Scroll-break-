package com.assem.scrollbreak

import android.content.Context
import android.content.SharedPreferences

/** Small persisted switches shared by the UI and the service (same process). */
object AppSettings {
    private const val PREFS = "scrollbreak"
    private const val KEY_BLOCKING = "blocking_enabled"

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
}
