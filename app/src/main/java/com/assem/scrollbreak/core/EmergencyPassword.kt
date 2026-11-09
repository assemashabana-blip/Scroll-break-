package com.assem.scrollbreak.core

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * A six-digit code a friend or family member sets, so that every lock and limit can be lifted in
 * an emergency without the person being locked knowing how. Only a salted PBKDF2 hash is kept, so
 * the code cannot be read back from the phone. (Six digits is a small space, so the slow hash and
 * the growing wait between wrong tries, [Attempts], are what make guessing impractical.)
 */
object EmergencyPassword {
    const val CODE_LENGTH = 6

    /** The salt and hash as text, ready to be saved. */
    data class Stored(val salt: String, val hash: String)

    /** Null when [password] is acceptable, otherwise what is wrong with it. */
    fun problemWith(password: String): String? = when {
        password.length != CODE_LENGTH || !password.all { it in '0'..'9' } -> "The code must be exactly $CODE_LENGTH digits."
        else -> null
    }

    fun create(password: String, random: SecureRandom = SecureRandom()): Stored {
        val salt = ByteArray(16).also { random.nextBytes(it) }
        return Stored(Base64.getEncoder().encodeToString(salt), Base64.getEncoder().encodeToString(hash(password, salt)))
    }

    fun verify(password: String, stored: Stored): Boolean = try {
        val salt = Base64.getDecoder().decode(stored.salt)
        val expected = Base64.getDecoder().decode(stored.hash)
        MessageDigest.isEqual(hash(password, salt), expected)
    } catch (_: IllegalArgumentException) {
        false
    }

    private fun hash(password: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256))
            .encoded

    private const val ITERATIONS = 60_000
}

/**
 * Slows down guessing. The first two wrong tries cost nothing; from the third on, each wrong try
 * locks the entry for a minute, then two, four... up to an hour. A right password resets it.
 */
data class Attempts(val failures: Int = 0, val lockedUntil: Long = 0) {
    fun remainingMs(now: Long): Long = (lockedUntil - now).coerceAtLeast(0)

    fun failed(now: Long): Attempts {
        val n = failures + 1
        val wait = if (n < FREE_TRIES + 1) 0L else minOf(BASE_WAIT_MS shl (n - FREE_TRIES - 1).coerceAtMost(20), MAX_WAIT_MS)
        return Attempts(n, if (wait > 0) now + wait else lockedUntil)
    }

    fun succeeded() = Attempts()

    companion object {
        const val FREE_TRIES = 2
        const val BASE_WAIT_MS = 60_000L
        const val MAX_WAIT_MS = 3_600_000L
    }
}
