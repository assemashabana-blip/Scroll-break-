package com.assem.scrollbreak.core

/**
 * Which website is the "web version" of which app. When an app has a limit or a block, its website
 * gets the same one, in a browser; the browser itself is never limited by this.
 *
 * Only well-known apps are listed: Android gives no way to learn an app's website, so an app that
 * is not here simply has no web version to follow.
 */
object WebSites {
    private val sites: Map<String, List<String>> = mapOf(
        "com.instagram.android" to listOf("instagram.com"),
        "com.instagram.lite" to listOf("instagram.com"),
        "com.instagram.barcelona" to listOf("threads.net", "threads.com"),
        "com.google.android.youtube" to listOf("youtube.com", "youtu.be"),
        "com.google.android.apps.youtube.music" to listOf("music.youtube.com"),
        "com.facebook.katana" to listOf("facebook.com", "fb.com", "fb.watch"),
        "com.facebook.lite" to listOf("facebook.com", "fb.com", "fb.watch"),
        "com.facebook.orca" to listOf("messenger.com"),
        "com.zhiliaoapp.musically" to listOf("tiktok.com"),
        "com.zhiliaoapp.musically.go" to listOf("tiktok.com"),
        "com.ss.android.ugc.trill" to listOf("tiktok.com"),
        "com.snapchat.android" to listOf("snapchat.com"),
        "com.twitter.android" to listOf("x.com", "twitter.com"),
        "com.reddit.frontpage" to listOf("reddit.com"),
        "com.whatsapp" to listOf("web.whatsapp.com"),
        "com.whatsapp.w4b" to listOf("web.whatsapp.com"),
        "org.telegram.messenger" to listOf("web.telegram.org"),
        "com.netflix.mediaclient" to listOf("netflix.com"),
        "com.pinterest" to listOf("pinterest.com"),
        "com.linkedin.android" to listOf("linkedin.com"),
        "tv.twitch.android.app" to listOf("twitch.tv"),
        "com.discord" to listOf("discord.com"),
        "com.spotify.music" to listOf("open.spotify.com"),
        "com.google.android.gm" to listOf("mail.google.com"),
        "com.tumblr" to listOf("tumblr.com"),
        "com.quora.android" to listOf("quora.com"),
    )

    /** The websites that follow [pkg]'s limit, empty when it has none. */
    fun domainsOf(pkg: String): List<String> = sites[pkg].orEmpty()

    /** Every app whose website [host] belongs to (a site and its subdomains), usually one or two. */
    fun packagesFor(host: String): List<String> {
        val h = host.lowercase().removeSuffix(".")
        return sites.filter { (_, domains) -> domains.any { h == it || h.endsWith(".$it") } }.keys.toList()
    }

    /** True when any app that is in [limited] has [host] as its website. */
    fun anyLimited(host: String, limited: Set<String>): Boolean = packagesFor(host).any { it in limited }

    /** The host part of a normalized "host/path" URL, without a port. */
    fun hostOf(normalizedUrl: String): String = normalizedUrl.substringBefore('/').substringBefore(':')
}

/**
 * Counts how long a browser has been on a limited site. Called on every look at the screen; the time
 * since the previous look is added, but never more than [capMs], so a gap (the phone was put down,
 * the browser was backgrounded) is not counted as time on the site.
 */
class WebTimeTracker(private val capMs: Long = 15_000) {
    private var last: Long? = null

    /** Milliseconds to add now, given whether the browser is [onSite] at [now]. */
    fun tick(onSite: Boolean, now: Long): Long {
        val before = last
        last = if (onSite) now else null
        return if (onSite && before != null) (now - before).coerceIn(0, capMs) else 0L
    }
}
