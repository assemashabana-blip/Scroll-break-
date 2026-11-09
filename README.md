# ScrollBreak

An Android app against doom scrolling. It gets you out of Reels, Shorts, TikTok and every other short-video feed, but still lets you watch the one video a friend sent you, so you stay social without getting pulled into the feed.

ScrollBreak is free to download and use, and its source code is public so you can read it and build it yourself. Bug reports and pull requests are welcome.

**[⬇ Download ScrollBreak for Android (latest APK)](https://github.com/assemashabana-blip/Scroll-break-/releases/latest)**: Android 8.0 or newer, then follow [the setup steps](#install-it-on-your-android-phone).

## What it does

- **Blocks short-video feeds** the moment they appear, in under 300 ms: it goes back (or switches to a safer tab) and covers the feed if it is still there.
- **One video from a friend still plays.** If you open a video from a chat (WhatsApp, Telegram, Instagram DMs, a story…), that one video plays. Swiping to the next one is blocked.
- **Time in apps** on the home screen: how long you spent in each app today and over the week.
- **Blocks counted per day**, so you can see the habit fading.
- **App limits:** give any app on your phone a daily time limit, or block it for a while. A daily limit can be lowered but not raised or removed until tomorrow, and a block cannot be changed until it ends. While a limit is locked, ScrollBreak cannot be switched off or uninstalled from the usual screens. Daily limits need Usage access.
- **Emergency code:** a friend or family member sets a six-digit code in Settings (you do not need to know it). If you ever truly need out, entering it removes the lock timer and every app limit and block.
- **Lock timer:** pick a time on the first card of the home screen (drag the dial, or tap the number to type days, hours and minutes, up to 30 days) and blocking stays on until it ends. You cannot turn it off, cancel the timer, change the settings, switch ScrollBreak off in Accessibility or uninstall it from the usual screens meanwhile.
- **Friction on switching it off:** the Android page that turns ScrollBreak off is covered by a countdown, so turning it off is a decision, not a reflex.
- **Light and dark theme**, English and Arabic phones.

| App | What is blocked |
|---|---|
| YouTube | Shorts |
| Instagram (and Instagram Lite) | Reels |
| TikTok | The feed |
| Facebook | Reels |
| Snapchat | Spotlight |
| Messenger | Reels opened from a chat in Messenger (one plays, swiping on is blocked) |
| Chrome | Shorts, Reels and TikTok opened in the browser |

### Privacy

ScrollBreak works only through Android's Accessibility service, which lets it see which screen an app is showing. It has **no internet permission**, no analytics, no ads and no account. Nothing you do leaves your phone. It does not use root, a VPN or screen recording.

## Install it on your Android phone

You need **Android 8.0 or newer**. ScrollBreak is not on the Play Store, so you install the APK yourself ("sideloading").

### 1. Get the APK

**Option A: download it.** On your phone, open the [latest release](https://github.com/assemashabana-blip/Scroll-break-/releases/latest) and tap the `.apk` file under **Assets** (for example `ScrollBreak-1.0.1.apk`).

**Option B: build it yourself.** Follow [Work on the project](#work-on-the-project) below, then in Android Studio choose **Build → Generate Signed App Bundle or APK → APK**, create a key when it asks (keep that key file safe: you need the same key for every update), pick the **release** build, and copy the APK to your phone.

### 2. Install it

1. Open the APK on your phone.
2. Android asks to allow installing from this source (your browser or file manager). Allow it, go back, and tap **Install**.
3. Play Protect may warn that the app is unknown. Choose **More details → Install anyway**. That warning appears for any app that is not from the Play Store.

#### If the phone blocks the install

ScrollBreak comes from outside the Play Store and asks for the Accessibility service, which scam apps also misuse, so phones check it strictly. What to do depends on the message:

| Message | What to do |
|---|---|
| "Blocked by Play Protect" / "Unsafe app blocked" | Tap **More details → Install anyway**. If there is no such button: Play Store → your profile picture → **Play Protect** → ⚙ → turn off **Scan apps with Play Protect**, install, then turn it back on. |
| Samsung: "Blocked by Auto Blocker" | Settings → **Security and privacy → Auto Blocker** → off, install, then turn it back on. |
| Xiaomi / Redmi / Poco: security scan warning | Wait for the countdown, tick the box and tap **Install anyway**. |
| "App blocked to protect your device" (mentions sensitive permissions) | Google's fraud protection, which some countries enforce with no "install anyway". Install from a computer instead: turn on USB debugging on the phone, connect it, and run `adb install ScrollBreak-<version>.apk`. |
| "App not installed" / "conflicts with an existing package" | A ScrollBreak signed with a different key is already installed (for example a debug build). Uninstall it first. |

### 3. Set it up

Open ScrollBreak. The first time, it shows a **Setup checklist** with a button for each step:

1. **Turn on the accessibility service.** Settings → Accessibility → ScrollBreak → On. This is what lets ScrollBreak see which video is showing.
2. **Allow restricted settings (Android 13 and newer).** If the accessibility switch is greyed out, open ScrollBreak's App info, tap **⋮** (top right), choose **Allow restricted settings**, then go back and turn the service on.
3. **Allow notifications**, so ScrollBreak can warn you if Android ever switches it off.
4. **Turn off battery optimisation** for ScrollBreak, so Android does not put it to sleep.
5. **Xiaomi, Huawei, Oppo, Vivo, Samsung and similar phones:** allow **autostart / background activity**. These phones close background apps aggressively; the checklist opens the right page.

Optional:

- **Usage access**, to see *Time in apps* on the home screen (the card has a button for it).

That's it. The orange circle on the home screen means blocking is on. Use the tiles to choose which apps are blocked, and *Settings* to tune how long a friend's link counts and which messaging apps count as "a friend sent this".

### Updating

Install the new APK over the old one. If you built it yourself, sign it with the **same key** as before, or Android will refuse the update. After an update, check that the accessibility service is still on.

## Work on the project

### What you need

- [Android Studio](https://developer.android.com/studio) (latest stable). It includes the JDK and the Android SDK.
- In Android Studio's SDK Manager: **Android SDK Platform 37**.
- An Android phone with [USB debugging](https://developer.android.com/studio/debug/dev-options) turned on (an emulator works for the UI, but the apps ScrollBreak watches are best tested on a real phone).

### Get the code and run it

```bash
git clone https://github.com/assemashabana-blip/Scroll-break-.git
cd Scroll-break-
```

Open the folder in Android Studio, wait for Gradle to sync, connect your phone and press **Run**. Or from a terminal (with `JAVA_HOME` pointing at a JDK, for example Android Studio's `jbr` folder):

```bash
./gradlew :app:testDebugUnitTest      # all unit tests (Windows: gradlew.bat)
./gradlew :app:installDebug           # build and install on the connected phone
```

**After every install, Android turns the accessibility service off.** Turn it back on in Settings, or with adb:

```bash
adb shell settings put secure enabled_accessibility_services com.assem.scrollbreak/com.assem.scrollbreak.service.ScrollBreakService
adb shell settings put secure accessibility_enabled 1
```

> Use the debug build only for development. It opens a developer receiver (screen dumps, test switches) to adb and to other apps on the phone; the release build keeps it closed.

### How the code is organised

| Path | What is there |
|---|---|
| `app/src/main/java/.../core/` | The logic, in plain Kotlin with no Android imports, so it is tested with ordinary JUnit tests. |
| `app/src/main/java/.../service/` | The accessibility service and everything else that talks to Android. `ScrollBreakService.kt` is the central file. |
| `app/src/main/java/.../ui/` | The Jetpack Compose screens (Home, Settings, Setup, Rules, Health). |
| `app/src/main/assets/rules/*.json` | **One detection rule per app.** This is where "what does a Reel look like" lives. |
| `app/src/test/` | Unit tests, with sanitised screen dumps in `resources/dumps/`. |
| `tools/sanitize_dump.py` | Turns a raw screen dump into a test fixture with all personal text removed. |
| `app/prompt.md` | The original design spec. |

Detection is data, not code: each rule describes the full-screen player, the tabs that lead to it, the screens a video may be opened from (a chat, a story), and how to tell one video from the next. The rule format is documented in `core/rules/`.

### Fixing or adding an app

Apps change their screens with updates, so rules need care. **Never write a rule from memory**; always work from a real dump of the screen:

1. Install the debug build, open the screen in the target app, then capture it:
   ```bash
   adb shell am broadcast -a com.assem.scrollbreak.DUMP_NOW -n com.assem.scrollbreak/.service.DumpReceiver
   ```
   Dumps are saved in the app's `files/dumps/` folder (also listed under *Settings → Screen dumps* in the debug build).
2. Sanitise it before it goes anywhere near git. Raw dumps contain names, messages and handles from your own accounts:
   ```bash
   python tools/sanitize_dump.py dump.txt <package> app/src/test/resources/dumps/<name>.txt
   ```
3. Write or change the rule in `app/src/main/assets/rules/`, preferring view ids, then text and descriptions.
4. Add a `*RuleTest` that runs the rule against the fixtures (a player screen, a non-player screen, and two different videos), run the tests, and try it on a phone.

Rules that match on-screen text must list every phone language that has been checked (English and Arabic so far). Dumps from phones in other languages are very welcome.

### Contributing

1. Fork the repository and create a branch.
2. Keep new logic in `core/` where possible, with tests.
3. Make sure `./gradlew :app:testDebugUnitTest` passes.
4. Never commit raw screen dumps, only sanitised ones.
5. Open a pull request that says what you changed and which phone and app versions you tested on.

Bug reports are just as useful: open an issue with your phone model, Android version, the app and its version, and what happened.

## Brand

Logo: a video card snapped in half. The top half (the one video you're allowed) stays, the next one breaks away, and the accent marks the break. Black `#000000`, white `#FFFFFF`, and one accent `#FF4A1C`, used only for the break. Wordmark typeface: Space Grotesk (SIL Open Font License).

## Copyright

© 2026 Assem Shabana. All rights reserved.
