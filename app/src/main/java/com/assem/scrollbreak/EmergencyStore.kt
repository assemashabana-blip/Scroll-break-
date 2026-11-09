package com.assem.scrollbreak

import android.content.Context
import android.content.SharedPreferences
import com.assem.scrollbreak.core.EmergencyPassword

/**
 * The emergency code (six digits): set by a friend or family member, it lifts the lock timer and every app
 * limit and block. Kept in its own preferences file, which the backup rules leave out, as a salted
 * hash only. Wrong guesses are not limited (a deliberate choice).
 */
object EmergencyStore {
    private const val PREFS = "emergency"
    private const val KEY_SALT = "salt"
    private const val KEY_HASH = "hash"

    /** [message] is what to show the user when [ok] is false; null on success. */
    class Outcome(val ok: Boolean, val message: String? = null)

    @Volatile private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    val isSet: Boolean get() = stored() != null

    /**
     * Sets the first password. Refused while a lock timer or app limit is running: otherwise the
     * person being locked could simply choose a password they know and use it.
     */
    @Synchronized
    fun setFirst(password: String): Outcome {
        if (isSet) return Outcome(false, "A code is already set.")
        if (AppSettings.protectionRemainingMs() > 0) {
            return Outcome(false, "A code can only be set when no lock timer or app limit is running.")
        }
        EmergencyPassword.problemWith(password)?.let { return Outcome(false, it) }
        save(EmergencyPassword.create(password))
        return Outcome(true)
    }

    @Synchronized
    fun change(current: String, new: String): Outcome {
        verifyOrFail(current)?.let { return it }
        EmergencyPassword.problemWith(new)?.let { return Outcome(false, it) }
        save(EmergencyPassword.create(new))
        return Outcome(true)
    }

    @Synchronized
    fun remove(current: String): Outcome {
        verifyOrFail(current)?.let { return it }
        prefs?.edit()?.remove(KEY_SALT)?.remove(KEY_HASH)?.apply()
        return Outcome(true)
    }

    /** With the right password: ends the lock timer and removes every app limit and block. */
    @Synchronized
    fun unlockEverything(password: String): Outcome {
        verifyOrFail(password)?.let { return it }
        AppSettings.emergencyReset()
        return Outcome(true)
    }

    /** Null when [password] is right, otherwise why not. */
    private fun verifyOrFail(password: String): Outcome? {
        val stored = stored() ?: return Outcome(false, "No code is set.")
        return if (EmergencyPassword.verify(password, stored)) null else Outcome(false, "Wrong code.")
    }

    private fun stored(): EmergencyPassword.Stored? {
        val p = prefs ?: return null
        val salt = p.getString(KEY_SALT, null) ?: return null
        val hash = p.getString(KEY_HASH, null) ?: return null
        return EmergencyPassword.Stored(salt, hash)
    }

    private fun save(stored: EmergencyPassword.Stored) {
        prefs?.edit()?.putString(KEY_SALT, stored.salt)?.putString(KEY_HASH, stored.hash)?.apply()
    }
}
