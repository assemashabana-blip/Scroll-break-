package com.assem.scrollbreak.core

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * A six-digit code a friend or family member sets, so that every lock and limit can be lifted in
 * an emergency without the person being locked knowing how. Only a salted PBKDF2 hash is kept, so
 * the code cannot be read back from the phone. (Six digits is a small space and wrong tries are
 * not limited, by choice, so only the slow hash makes guessing expensive.)
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
