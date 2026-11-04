# Project: ScrollBreak — Android anti-doomscroll app

## Role
You are a senior Android engineer. Build this app incrementally with me. I want to understand
every part, so after each phase: explain what you built, why, and how to test it on my phone,
then STOP and wait for my confirmation before the next phase.

## Goal
Block short-form video feeds (YouTube Shorts, Instagram Reels, TikTok, Facebook Reels,
Snapchat Spotlight) across all apps AND browsers (Chrome, Samsung Internet, Firefox, Edge,
Brave, including Chrome Custom Tabs), while still allowing me to watch a single video a friend
sent me — just no scrolling past it to the next one.

Reliability is the #1 requirement: it must work across all target apps at the same time,
react fast (block before the next video's audio starts, target < 300 ms), and stay alive
in the background on aggressive OEM ROMs.

## Constraints
- Kotlin, Jetpack Compose for UI, coroutines. minSdk 26, targetSdk latest stable.
- Personal sideloaded app (NOT Play Store). No root, no VPN, no screen capture.
- Core mechanism: a single AccessibilityService that observes all target packages.
- No network access, no analytics, all data stays on device.
- Do NOT hard-code view IDs as facts — app UIs change. All detection must be data-driven
  (see Rules Engine) and verified against real node-tree dumps from my device.

## Architecture
1. **AccessibilityService** (`ScrollBreakService`)
    - Event types: windowStateChanged, windowContentChanged, viewScrolled.
    - Flags: flagReportViewIds, flagRetrieveInteractiveWindows; canRetrieveWindowContent=true.
    - packageNames limited to target apps + messaging apps (WhatsApp, Telegram, Messenger,
      Signal, Gmail) for entry-source tracking.
    - Throttle windowContentChanged (configurable, default 200 ms). Use targeted lookups
      (findAccessibilityNodeInfosByViewId) on the hot path; full tree walks only in debug.
    - Inspect all windows via getWindows() (split-screen / PiP must not bypass).
    - Every handler wrapped in try/catch; errors logged to a rotating local file. The service
      must never crash (Android silently disables repeatedly crashing services).

2. **Rules Engine (data-driven)**
    - One JSON rule file per app in assets, overridable by an imported file at runtime.
    - Each rule defines, with multiple fallback signals (view IDs, class names,
      content descriptions, text, URL patterns):
        - `playerSignature` — the full-screen short-video player
        - `feedEntrySignatures` — Reels tab, Shorts tab, For You feed, etc.
        - `dmSignature` — in-app DM/chat screens (e.g. Instagram Direct)
        - `fingerprintNodes` — nodes (author handle, caption) used to identify the current video
        - For browsers: `urlBarIds` and `urlPatterns` (youtube.com/shorts/, instagram.com/reel(s)/,
          tiktok.com, facebook.com/reel/)
    - Signatures support AND/OR combinations.

3. **State machine** per session: IDLE → ALLOW_ONE → BLOCK
    - Keep a short history of recent windows (package, matched signature, timestamp).
    - On entering a player:
        - previous window was a messaging app or an in-app DM screen within N seconds
          (default 5 s) → ALLOW_ONE
        - previous window was a feed entry, or unknown → BLOCK
    - In ALLOW_ONE: compute a fingerprint hash of the current video. If it changes from a
      non-empty value A to a different non-empty value B while still in the player
      (or viewScrolled shows fromIndex/toIndex advanced) → BLOCK. Ignore empty→A (initial load).
    - Leaving the player → IDLE.
    - Browsers: cache the last known URL (URL bar hides on scroll). First shorts/reel URL
      after arriving from a messaging app → ALLOW_ONE; a change to a different video URL → BLOCK.

4. **Block action (escalating)**
    1. performGlobalAction(GLOBAL_ACTION_BACK)
    2. Re-check after ~300 ms; if still in a blocked player, show a full-screen
       TYPE_ACCESSIBILITY_OVERLAY with a message and a "Go back" button.
    - Log every block (app, reason, timestamp) for stats.

5. **Debug / reconnaissance tools** (critical for maintenance)
    - A persistent notification with a "Dump screen" action that walks all windows and logs
      each node: viewIdResourceName, className, text, contentDescription, bounds, child count,
      depth. Save to a file I can export. Must work on video screens (do not use uiautomator).
    - Live event log screen (package, event type, class, matched signature, current state).
    - "Detection health" screen: per app, last time each signature matched.

6. **Reliability & survival**
    - Onboarding that walks me through: enabling the service, "Allow restricted settings"
      (Android 13+ sideload), battery optimization exemption, and OEM autostart settings
      (Xiaomi/Samsung/Oppo deep links where possible).
    - Periodic watchdog (WorkManager) that detects if the service got disabled and posts
      a high-priority notification.
    - Measure and display reaction latency (event received → block action).

7. **Friction (self-control)**
    - When the Accessibility settings page for this service is opened, show a 60-second
      countdown overlay before allowing it to be turned off.
    - Optional Device Admin to add uninstall friction.

8. **UI (Compose)**
    - Home: service status, blocks today, per-app toggles.
    - Settings: allow-window seconds, throttle ms, which messaging apps count as "friend
      shared", whether inline feed reels are allowed.
    - Rules: view loaded rules, import/export JSON.
    - Debug: event log, dumps, detection health.

## Phases (stop after each)
1. Project skeleton + logging-only AccessibilityService + event log screen.
2. Screen dump tool + export. (I will dump real screens from my phone and give you the output.)
3. Rules engine + YouTube Shorts detection + escalating block action.
4. State machine: entry-source tracking + fingerprinting → "one video per entry" on YouTube.
5. Instagram, TikTok, Facebook, Snapchat rules (built from my dumps).
6. Browsers + Custom Tabs via URL rules.
7. Reliability: throttling, multi-window, crash safety, watchdog, onboarding, latency metric.
8. Friction + stats + settings polish.

## Testing expectations
For each phase give me a manual test checklist covering: friend video from WhatsApp plays,
swiping to the next one is blocked, opening the Reels/Shorts tab directly is blocked,
split-screen, PiP, app restart, phone reboot, and battery-saver mode.
Add unit tests for the state machine and rules matching (pure Kotlin, no Android deps).

## Start
Begin with Phase 1. Before writing code, briefly confirm the plan and list any questions.