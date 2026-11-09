package com.assem.scrollbreak.core.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The pages covered while the lock timer runs: ScrollBreak's own App info page and the uninstall
 * dialog. Fixtures are sanitised dumps from the Pixel (English); other languages are unverified.
 */
class LockGuardRuleTest {
    private fun rule(name: String) = RuleParser.parse(File("src/main/assets/rules/$name.json").readText())

    private fun screen(dump: String, pkg: String) =
        DumpParser.screenFor(DumpParser.parse(javaClass.getResource("/dumps/$dump")!!.readText()), pkg)

    private val settings = rule("settings_friction")
    private val uninstall = rule("uninstall_guard")

    @Test
    fun scrollBreakAppInfoPageIsGuarded() {
        assertTrue(settings.isLockGuarded(screen("settings_appinfo_scrollbreak.txt", "com.android.settings")))
    }

    @Test
    fun anotherAppsInfoPageIsNot() {
        assertFalse(settings.isLockGuarded(screen("settings_appinfo_chrome.txt", "com.android.settings")))
    }

    @Test
    fun appInfoPageIsNotTheTurnOffPage() {
        // The countdown page is a different screen; the two rules must not be confused.
        assertFalse(settings.isPlayer(screen("settings_appinfo_scrollbreak.txt", "com.android.settings")))
    }

    @Test
    fun uninstallDialogForScrollBreakIsGuarded() {
        val s = screen("packageinstaller_uninstall_scrollbreak.txt", "com.google.android.packageinstaller")
        assertTrue(uninstall.isLockGuarded(s))
    }

    @Test
    fun uninstallRuleCoversBothInstallerPackages() {
        assertEquals(
            setOf("com.google.android.packageinstaller", "com.android.packageinstaller"),
            uninstall.packages,
        )
        assertEquals(Purpose.FRICTION, uninstall.purpose)
        assertNotNull(uninstall.lockSignature)
    }

    @Test
    fun aRuleWithOnlyALockSignatureParses() {
        val r = RuleParser.parse(
            """{"schemaVersion":1,"app":"x","packages":["p"],"lockSignature":{"viewId":"p:id/a"}}""",
        )
        assertNotNull(r.lockSignature)
        assertFalse(r.isPlayer { false })
    }
}
