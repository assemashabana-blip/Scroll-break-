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

    @Test
    fun theFirstTwoWrongTriesAreFree() {
        var a = Attempts()
        a = a.failed(1_000)
        assertEquals(0L, a.remainingMs(1_000))
        a = a.failed(2_000)
        assertEquals(0L, a.remainingMs(2_000))
    }

    @Test
    fun fromTheThirdWrongTryEachOneWaitsLonger() {
        var a = Attempts().failed(0).failed(0)
        a = a.failed(0)                                   // 3rd: 1 minute
        assertEquals(60_000L, a.remainingMs(0))
        a = a.failed(100_000)                             // 4th: 2 minutes
        assertEquals(120_000L, a.remainingMs(100_000))
        a = a.failed(300_000)                             // 5th: 4 minutes
        assertEquals(240_000L, a.remainingMs(300_000))
    }

    @Test
    fun theWaitStopsGrowingAtAnHour() {
        var a = Attempts()
        repeat(40) { a = a.failed(0) }
        assertEquals(3_600_000L, a.remainingMs(0))
    }

    @Test
    fun theWaitCountsDownAndARightPasswordResets() {
        val a = Attempts().failed(0).failed(0).failed(0)
        assertEquals(30_000L, a.remainingMs(30_000))
        assertEquals(0L, a.remainingMs(60_000))
        assertEquals(Attempts(), a.succeeded())
    }
}
