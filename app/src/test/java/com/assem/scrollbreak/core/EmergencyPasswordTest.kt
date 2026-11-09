package com.assem.scrollbreak.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyPasswordTest {
    @Test
    fun theRightPasswordVerifiesAndAWrongOneDoesNot() {
        val stored = EmergencyPassword.create("grandma-1957")
        assertTrue(EmergencyPassword.verify("grandma-1957", stored))
        assertFalse(EmergencyPassword.verify("grandma-1958", stored))
        assertFalse(EmergencyPassword.verify("", stored))
        assertFalse(EmergencyPassword.verify("GRANDMA-1957", stored))
    }

    @Test
    fun theHashDoesNotContainThePassword() {
        val stored = EmergencyPassword.create("correct horse")
        assertFalse(stored.hash.contains("correct"))
        assertFalse(stored.salt.contains("correct"))
    }

    @Test
    fun theSameDoesNotHashTwiceTheSameBecauseOfTheSalt() {
        val a = EmergencyPassword.create("same password")
        val b = EmergencyPassword.create("same password")
        assertNotEquals(a.salt, b.salt)
        assertNotEquals(a.hash, b.hash)
        assertTrue(EmergencyPassword.verify("same password", a))
        assertTrue(EmergencyPassword.verify("same password", b))
    }

    @Test
    fun brokenStoredValuesNeverVerify() {
        assertFalse(EmergencyPassword.verify("x", EmergencyPassword.Stored("not base64!", "also not")))
        assertFalse(EmergencyPassword.verify("x", EmergencyPassword.Stored("", "")))
    }

    @Test
    fun onlySixDigitsAreAccepted() {
        assertNull(EmergencyPassword.problemWith("482913"))
        assertNull(EmergencyPassword.problemWith("000000"))
        assertNotNull(EmergencyPassword.problemWith("48291"))        // too short
        assertNotNull(EmergencyPassword.problemWith("4829133"))      // too long
        assertNotNull(EmergencyPassword.problemWith("48291a"))       // not a digit
        assertNotNull(EmergencyPassword.problemWith("      "))
        assertNotNull(EmergencyPassword.problemWith(""))
        assertNotNull(EmergencyPassword.problemWith("48 913"))
    }
}
