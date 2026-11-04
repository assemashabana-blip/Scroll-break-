package com.assem.scrollbreak.core

import com.assem.scrollbreak.core.Watchdog.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchdogTest {
    @Test
    fun disabledAndNoAlertRaisesTheAlert() {
        assertEquals(Decision.ALERT, Watchdog.decide(serviceEnabled = false, alertShown = false))
    }

    @Test
    fun disabledAndAlreadyAlertedDoesNotNagAgain() {
        assertEquals(Decision.NONE, Watchdog.decide(serviceEnabled = false, alertShown = true))
    }

    @Test
    fun enabledClearsAStaleAlert() {
        assertEquals(Decision.CLEAR, Watchdog.decide(serviceEnabled = true, alertShown = true))
    }

    @Test
    fun enabledAndQuietDoesNothing() {
        assertEquals(Decision.NONE, Watchdog.decide(serviceEnabled = true, alertShown = false))
    }
}

class OemAutostartTest {
    @Test
    fun knownVendorsHaveCandidatesRegardlessOfCaseOrSpaces() {
        for (m in listOf("Xiaomi", " SAMSUNG ", "OPPO", "realme", "OnePlus", "vivo", "HUAWEI", "Honor", "asus", "POCO")) {
            assertTrue(m, OemAutostart.needsManualStep(m))
            assertTrue(m, OemAutostart.candidates(m).all { it.pkg.isNotEmpty() && it.cls.isNotEmpty() })
        }
    }

    @Test
    fun stockAndroidNeedsNoManualStep() {
        for (m in listOf("Google", "Motorola", "Nothing", "", "unknown")) assertFalse(m, OemAutostart.needsManualStep(m))
    }

    @Test
    fun samsungHasTheDeviceCareScreenFirst() {
        assertEquals("com.samsung.android.lool", OemAutostart.candidates("samsung").first().pkg)
    }

    @Test
    fun xiaomiFamilySharesOneList() {
        assertEquals(OemAutostart.candidates("xiaomi"), OemAutostart.candidates("redmi"))
        assertEquals(OemAutostart.candidates("xiaomi"), OemAutostart.candidates("poco"))
    }
}
