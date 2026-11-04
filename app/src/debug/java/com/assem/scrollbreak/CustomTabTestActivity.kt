package com.assem.scrollbreak

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsIntent

/** Debug helper: `adb shell am start -n .../.CustomTabTestActivity --es url <url>` opens a Custom Tab. */
class CustomTabTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("url") ?: "https://example.com"
        CustomTabsIntent.Builder().build().launchUrl(this, Uri.parse(url))
        finish()
    }
}
