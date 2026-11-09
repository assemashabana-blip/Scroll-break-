package com.assem.scrollbreak.core.rules

data class NamedSignature(val name: String, val signature: Signature)

/** What a rule is for. FRICTION rules recognise a system screen to slow down, not a video. */
enum class Purpose { SHORT_VIDEO, FRICTION }

/** Detection rules for one app. Unknown JSON keys are ignored so the schema can grow. */
data class AppRule(
    val app: String,
    val packages: Set<String>,
    /** The full-screen short-video player; null for apps (browsers) that are judged by URL alone. */
    val playerSignature: Signature?,
    /** Screens that lead into the player (Shorts tab, Reels tab, ...). */
    val feedEntrySignatures: List<NamedSignature> = emptyList(),
    /**
     * In-app screens a video may legitimately be opened from (a DM thread, a story, ...).
     * Reaching the player within the allow window of seeing one of these allows one video.
     */
    val allowSources: List<NamedSignature> = emptyList(),
    /** Nodes whose text identifies the video on screen (author handle, caption, ...). */
    val fingerprintNodes: List<FingerprintNode> = emptyList(),
    /**
     * The scrollable whose VIEW_SCROLLED events carry the current video index (from == to).
     * Matched against the event source; null when the app reports no usable index.
     */
    val scrollSignal: Leaf? = null,
    /**
     * Web content (an in-app browser) reports no item index, only how far it has scrolled. When
     * true, the scroll position itself stands in for the index: any movement after the first
     * event means the user swiped on to another video.
     */
    val scrollByPosition: Boolean = false,
    /** Browsers: view ids of the URL bar, tried in order. */
    val urlBarIds: List<String> = emptyList(),
    /** Browsers: URL shapes that mean "short-video page", matched on the canonical host/path. */
    val urlPatterns: List<UrlPattern> = emptyList(),
    val purpose: Purpose = Purpose.SHORT_VIDEO,
    /**
     * A tab to switch to when a video is blocked, instead of leaving the app (TikTok: Friends).
     * Several leaves cover the same tab in different phone languages; the first that clicks wins.
     * If none can be clicked the block falls back to Back.
     */
    val redirectTo: List<Leaf> = emptyList(),
    /**
     * A screen that is covered only while the lock timer runs (ScrollBreak's own App info page,
     * the uninstall dialog). Independent of [purpose]; null for most rules.
     */
    val lockSignature: Signature? = null,
) {
    fun isLockGuarded(screen: ScreenView) = lockSignature?.evaluate(screen) ?: false

    fun isPlayer(screen: ScreenView) = playerSignature?.evaluate(screen) ?: false

    val isBrowser: Boolean get() = urlBarIds.isNotEmpty() && urlPatterns.isNotEmpty()

    /** Raw text of the URL bar, or null if none is on screen. */
    fun urlText(screen: ScreenView): String? =
        urlBarIds.firstNotNullOfOrNull { screen.textOf(Leaf(viewId = it)) }

    fun matchUrl(url: String): UrlPattern? = Urls.firstMatch(urlPatterns, url)

    /** Every signal this rule can report, for the detection-health screen. */
    fun signalNames(): List<String> = buildList {
        if (playerSignature != null) add("player")
        urlPatterns.forEach { add("url:${it.name}") }
        feedEntrySignatures.forEach { add("feed:${it.name}") }
        allowSources.forEach { add("source:${it.name}") }
        if (fingerprintNodes.isNotEmpty()) add("fingerprint")
    }

    /** Names of the feed-entry signatures that currently match. */
    fun matchingFeedEntries(screen: ScreenView): List<String> =
        feedEntrySignatures.filter { it.signature.evaluate(screen) }.map { it.name }

    /** Name of the first allow-source screen currently showing, if any. */
    fun matchingSource(screen: ScreenView): String? =
        allowSources.firstOrNull { it.signature.evaluate(screen) }?.name

    fun fingerprint(screen: ScreenView): Fingerprint = Fingerprint.compute(fingerprintNodes, screen)
}

class RuleSet(rules: List<AppRule>) {
    val rules: List<AppRule> = rules
    private val byPackage: Map<String, AppRule> =
        rules.flatMap { r -> r.packages.map { it to r } }.toMap()

    fun forPackage(pkg: String): AppRule? = byPackage[pkg]
}
