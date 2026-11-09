package com.assem.scrollbreak.core

/** Packages the service observes. Single source of truth for the service config. */
object TargetPackages {
    val videoApps = setOf(
        "com.google.android.youtube",
        "com.instagram.android",
        "com.instagram.lite",
        "com.zhiliaoapp.musically",
        "com.zhiliaoapp.musically.go",
        "com.ss.android.ugc.trill",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.snapchat.android",
    )

    val browsers = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.sec.android.app.sbrowser",
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "com.microsoft.emmx",
        "com.brave.browser",
        "com.opera.browser",
        "com.duckduckgo.mobile.android",
        "com.vivaldi.browser",
    )

    val messaging = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "com.facebook.orca",
        "org.thoughtcrime.securesms",
        "com.google.android.gm",
    )

    /**
     * System screens the service watches for its own safeguards: Settings (the "turn me off" page
     * and ScrollBreak's App info page) and the package installer (the uninstall dialog).
     */
    val system = setOf(
        "com.android.settings",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
    )

    val all: Set<String> = videoApps + browsers + messaging + system
}
